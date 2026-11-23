package com.example.kble.race

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kble.network.KbleSupabase
import io.github.jan.supabase.annotations.SupabaseExperimental
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.filter.FilterOperation
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.selectAsFlow
import io.github.jan.supabase.realtime.selectSingleValueAsFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * ViewModel de gestion des courses et des lobbys avec Supabase Realtime et fallback local.
 */
@OptIn(SupabaseExperimental::class)
class CourseViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(CourseUiState())
    val uiState = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var realtimeJobs = mutableListOf<Job>()

    val isOnline: Boolean get() = KbleSupabase.isConfigured

    // ========== Actions Lobby ==========

    fun creerCourse(nomJoueur: String, nomCircuit: String = "Circuit UQAC", nbTours: Int = 3, portesParTour: Int = 4, onComplete: (Boolean, String?) -> Unit = { _, _ -> }) {
        val hote = Joueur(nom = nomJoueur)
        val course = Course(
            nomCircuit = nomCircuit,
            hote = nomJoueur,
            joueurs = listOf(hote),
            nombreToursTotal = nbTours,
            portesParTour = portesParTour
        )
        val hoteAvecCourse = hote.copy(courseId = course.id)

        if (!KbleSupabase.isConfigured) {
            //mode local
            _uiState.update { it.copy(course = course.copy(joueurs = listOf(hoteAvecCourse)), joueurLocal = hoteAvecCourse, ecran = EcranCourse.LOBBY, messageInfo = "Lobby local (Supabase hors-ligne)") }
            onComplete(true, null)
            return
        }

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(chargement = true, erreur = null) }
                KbleSupabase.client.from("courses").insert(course)
                KbleSupabase.client.from("joueurs").insert(hoteAvecCourse)

                _uiState.update {
                    it.copy(
                        course = course.copy(joueurs = listOf(hoteAvecCourse)),
                        joueurLocal = hoteAvecCourse,
                        ecran = EcranCourse.LOBBY,
                        chargement = false
                    )
                }
                observerCourseRealtime(course.id, hoteAvecCourse.id)
                onComplete(true, null)
            } catch (e: Exception) {
                Log.e("KBLE_RACE", "Erreur création course Supabase", e)
                _uiState.update { it.copy(chargement = false, erreur = e.message ?: "Erreur réseau") }
                onComplete(false, e.message)
            }
        }
    }

    fun rejoindreCourse(codeCourse: String, nomJoueur: String, onComplete: (Boolean, String?) -> Unit = { _, _ -> }) {
        val code = codeCourse.trim().uppercase()

        if (!KbleSupabase.isConfigured) {
            //fallback mode local
            val c = _uiState.value.course
            if (c == null || c.id != code || c.etat != EtatCourse.LOBBY) {
                onComplete(false, "Course introuvable ou déjà lancée")
                return
            }
            if (c.joueurs.size >= c.maxJoueurs || c.joueurs.any { it.nom == nomJoueur }) {
                onComplete(false, "Course complète ou pseudo déjà utilisé")
                return
            }
            val joueur = Joueur(nom = nomJoueur, courseId = code)
            _uiState.update { it.copy(course = c.copy(joueurs = c.joueurs + joueur), joueurLocal = joueur, ecran = EcranCourse.LOBBY) }
            onComplete(true, null)
            return
        }

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(chargement = true, erreur = null) }
                // 1. Recherche de la course
                val courses = KbleSupabase.client.from("courses").select {
                    filter { eq("id", code) }
                }.decodeList<Course>()

                if (courses.isEmpty()) {
                    _uiState.update { it.copy(chargement = false, erreur = "Code de course invalide") }
                    onComplete(false, "Code de course invalide")
                    return@launch
                }
                val course = courses.first()
                if (course.etat != EtatCourse.LOBBY) {
                    _uiState.update { it.copy(chargement = false, erreur = "La course est déjà lancée") }
                    onComplete(false, "La course est déjà lancée")
                    return@launch
                }

                // 2. Vérification des joueurs existants
                val joueurs = KbleSupabase.client.from("joueurs").select {
                    filter { eq("course_id", code) }
                }.decodeList<Joueur>()

                if (joueurs.size >= course.maxJoueurs) {
                    _uiState.update { it.copy(chargement = false, erreur = "La course est complète") }
                    onComplete(false, "La course est complète")
                    return@launch
                }
                if (joueurs.any { it.nom.equals(nomJoueur, ignoreCase = true) }) {
                    _uiState.update { it.copy(chargement = false, erreur = "Ce pseudo est déjà utilisé") }
                    onComplete(false, "Ce pseudo est déjà utilisé")
                    return@launch
                }

                // 3. Rejoindre
                val joueur = Joueur(nom = nomJoueur, courseId = code)
                KbleSupabase.client.from("joueurs").insert(joueur)

                val courseAvecJoueurs = course.copy(joueurs = joueurs + joueur)
                _uiState.update {
                    it.copy(course = courseAvecJoueurs, joueurLocal = joueur, ecran = EcranCourse.LOBBY, chargement = false)
                }
                observerCourseRealtime(code, joueur.id)
                onComplete(true, null)
            } catch (e: Exception) {
                Log.e("KBLE_RACE", "Erreur rejoindre course Supabase", e)
                _uiState.update { it.copy(chargement = false, erreur = e.message ?: "Erreur de connexion") }
                onComplete(false, e.message)
            }
        }
    }

    /** Écoute réactive Realtime des changements dans Supabase (lobby, départ, positions) */
    private fun observerCourseRealtime(codeCourse: String, joueurId: String) {
        realtimeJobs.forEach { it.cancel() }
        realtimeJobs.clear()

        // 1. Écoute de l'état de la course (lancement, fin)
        val jobCourse = viewModelScope.launch {
            try {
                KbleSupabase.client.from("courses")
                    .selectSingleValueAsFlow(primaryKey = Course::id) {
                        eq("id", codeCourse)
                    }
                    .catch { e -> Log.e("KBLE_REALTIME", "Erreur flux course", e) }
                    .collect { courseMaj: Course ->
                        val etatPrecedent = _uiState.value.course?.etat
                        _uiState.update { s ->
                            val c = s.course?.copy(
                                etat = courseMaj.etat,
                                timestampDepart = courseMaj.timestampDepart
                            ) ?: courseMaj
                            val nouvelEcran = when (courseMaj.etat) {
                                EtatCourse.LOBBY -> EcranCourse.LOBBY
                                EtatCourse.EN_COURS -> if (s.ecran == EcranCourse.LOBBY) EcranCourse.COURSE else s.ecran
                                EtatCourse.TERMINEE -> EcranCourse.RESULTATS
                            }
                            s.copy(course = c, ecran = nouvelEcran)
                        }
                        if (etatPrecedent == EtatCourse.LOBBY && courseMaj.etat == EtatCourse.EN_COURS) {
                            demarrerChrono()
                        }
                    }
            } catch (e: Exception) {
                Log.e("KBLE_REALTIME", "Erreur écoute course", e)
            }
        }

        // 2. Écoute des joueurs (nouveaux inscrits, portes franchies, classement)
        val jobJoueurs = viewModelScope.launch {
            try {
                KbleSupabase.client.from("joueurs")
                    .selectAsFlow(
                        primaryKey = Joueur::id,
                        filter = FilterOperation("course_id", FilterOperator.EQ, codeCourse)
                    )
                    .catch { e -> Log.e("KBLE_REALTIME", "Erreur flux joueurs", e) }
                    .collect { listeJoueurs ->
                        val joueursClasses = classerJoueurs(listeJoueurs)
                        val moi = joueursClasses.firstOrNull { it.id == joueurId }
                        val tousOntFini = joueursClasses.isNotEmpty() && joueursClasses.all { it.aFini }

                        _uiState.update { s ->
                            s.copy(
                                course = s.course?.copy(joueurs = joueursClasses),
                                joueurLocal = moi ?: s.joueurLocal,
                                ecran = if (tousOntFini) EcranCourse.RESULTATS else s.ecran
                            )
                        }
                        if (tousOntFini) timerJob?.cancel()
                    }
            } catch (e: Exception) {
                Log.e("KBLE_REALTIME", "Erreur écoute joueurs", e)
            }
        }

        realtimeJobs.add(jobCourse)
        realtimeJobs.add(jobJoueurs)
    }

    fun lancerCourse() {
        val c = _uiState.value.course ?: return
        val local = _uiState.value.joueurLocal ?: return
        if (c.etat != EtatCourse.LOBBY || local.nom != c.hote) return

        val depart = System.currentTimeMillis()

        if (!KbleSupabase.isConfigured) {
            val joueurs = c.joueurs.mapIndexed { i, j -> j.copy(position = i + 1, tourActuel = 1) }
            _uiState.update {
                it.copy(
                    course = c.copy(etat = EtatCourse.EN_COURS, joueurs = joueurs, timestampDepart = depart),
                    joueurLocal = joueurs.first { j -> j.id == it.joueurLocal?.id },
                    ecran = EcranCourse.COURSE
                )
            }
            demarrerChrono()
            return
        }

        viewModelScope.launch {
            try {
                KbleSupabase.client.from("courses").update(
                    {
                        set("etat", EtatCourse.EN_COURS.name)
                        set("timestamp_depart", depart)
                    }
                ) {
                    filter { eq("id", c.id) }
                }
                //début de course --> chaque joueur est au premier tour
                c.joueurs.forEachIndexed { i, j ->
                    KbleSupabase.client.from("joueurs").update(
                        {
                            set("position", i + 1)
                            set("tour_actuel", 1)
                        }
                    ) {
                        filter { eq("id", j.id) }
                    }
                }
                demarrerChrono()
            } catch (e: Exception) {
                Log.e("KBLE_RACE", "Erreur lancement course Supabase", e)
            }
        }
    }

    private fun demarrerChrono() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(100)
                val s = _uiState.value
                val c = s.course ?: break
                if (c.etat != EtatCourse.EN_COURS) break
                val now = System.currentTimeMillis()
                val joueurMAJ = s.joueurLocal?.let { j ->
                    if (!j.aFini) j.copy(tempsMs = now - c.timestampDepart) else j
                } ?: break
                _uiState.update { it.copy(joueurLocal = joueurMAJ) }
            }
        }
    }

    fun enregistrerPassagePorte() {
        val s = _uiState.value
        val c = s.course ?: return
        val local = s.joueurLocal ?: return
        if (c.etat != EtatCourse.EN_COURS || local.aFini) return

        val now = System.currentTimeMillis()
        val nouvellePorte = local.dernierePorte + 1
        val nouveauTour = (nouvellePorte / c.portesParTour) + 1
        val aFini = nouvellePorte >= c.nombreToursTotal * c.portesParTour
        val temps = now - c.timestampDepart

        if (!KbleSupabase.isConfigured) {
            val joueursMAJ = c.joueurs.map { j ->
                if (j.id == local.id) {
                    j.copy(dernierePorte = nouvellePorte, tourActuel = if (aFini) c.nombreToursTotal else nouveauTour.coerceAtMost(c.nombreToursTotal), tempsMs = temps, aFini = aFini)
                } else j
            }
            val joueursClasses = classerJoueurs(joueursMAJ)
            val courseTerminee = joueursClasses.all { it.aFini }
            _uiState.update {
                it.copy(
                    course = c.copy(joueurs = joueursClasses, etat = if (courseTerminee) EtatCourse.TERMINEE else EtatCourse.EN_COURS),
                    joueurLocal = joueursClasses.first { j -> j.id == local.id },
                    ecran = if (courseTerminee) EcranCourse.RESULTATS else EcranCourse.COURSE
                )
            }
            if (courseTerminee) timerJob?.cancel()
            return
        }

        viewModelScope.launch {
            try {
                KbleSupabase.client.from("joueurs").update(
                    {
                        set("derniere_porte", nouvellePorte)
                        set("tour_actuel", if (aFini) c.nombreToursTotal else nouveauTour.coerceAtMost(c.nombreToursTotal))
                        set("temps_ms", temps)
                        set("a_fini", aFini)
                    }
                ) {
                    filter { eq("id", local.id) }
                }
            } catch (e: Exception) {
                Log.e("KBLE_RACE", "Erreur mise à jour porte Supabase", e)
            }
        }
    }

    fun simulerAdversaire() {
        val c = _uiState.value.course ?: return
        val local = _uiState.value.joueurLocal ?: return
        if (c.etat != EtatCourse.EN_COURS) return

        val adversaires = c.joueurs.filter { it.id != local.id && !it.aFini }
        if (adversaires.isEmpty()) return

        val cible = adversaires[Random.nextInt(adversaires.size)]
        val now = System.currentTimeMillis()
        val nouvellePorte = cible.dernierePorte + 1
        val nouveauTour = (nouvellePorte / c.portesParTour) + 1
        val aFini = nouvellePorte >= c.nombreToursTotal * c.portesParTour
        val temps = now - c.timestampDepart

        if (!KbleSupabase.isConfigured) {
            val joueursMAJ = c.joueurs.map { j ->
                if (j.id == cible.id) {
                    j.copy(dernierePorte = nouvellePorte, tourActuel = if (aFini) c.nombreToursTotal else nouveauTour.coerceAtMost(c.nombreToursTotal), tempsMs = temps, aFini = aFini)
                } else j
            }
            val joueursClasses = classerJoueurs(joueursMAJ)
            val courseTerminee = joueursClasses.all { it.aFini }
            _uiState.update {
                it.copy(
                    course = c.copy(joueurs = joueursClasses, etat = if (courseTerminee) EtatCourse.TERMINEE else EtatCourse.EN_COURS),
                    joueurLocal = joueursClasses.first { j -> j.id == local.id },
                    ecran = if (courseTerminee) EcranCourse.RESULTATS else EcranCourse.COURSE
                )
            }
            if (courseTerminee) timerJob?.cancel()
            return
        }

        viewModelScope.launch {
            try {
                KbleSupabase.client.from("joueurs").update(
                    {
                        set("derniere_porte", nouvellePorte)
                        set("tour_actuel", if (aFini) c.nombreToursTotal else nouveauTour.coerceAtMost(c.nombreToursTotal))
                        set("temps_ms", temps)
                        set("a_fini", aFini)
                    }
                ) {
                    filter { eq("id", cible.id) }
                }
            } catch (e: Exception) {
                Log.e("KBLE_RACE", "Erreur simulation Supabase", e)
            }
        }
    }

    fun quitterCourse() {
        val s = _uiState.value
        val local = s.joueurLocal
        val c = s.course

        timerJob?.cancel()
        realtimeJobs.forEach { it.cancel() }
        realtimeJobs.clear()

        if (KbleSupabase.isConfigured && local != null) {
            viewModelScope.launch {
                try {
                    KbleSupabase.client.from("joueurs").delete {
                        filter { eq("id", local.id) }
                    }
                    if (c != null && c.hote == local.nom && c.etat == EtatCourse.LOBBY) {
                        KbleSupabase.client.from("courses").delete {
                            filter { eq("id", c.id) }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("KBLE_RACE", "Erreur suppression joueur Supabase", e)
                }
            }
        }

        _uiState.update { CourseUiState() }
    }

    private fun classerJoueurs(joueurs: List<Joueur>): List<Joueur> =
        joueurs.sortedWith(compareByDescending<Joueur> { it.dernierePorte }.thenBy { it.tempsMs })
            .mapIndexed { i, j -> j.copy(position = i + 1) }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        realtimeJobs.forEach { it.cancel() }
    }
}

/** Écrans du flux de course */
enum class EcranCourse { ACCUEIL, LOBBY, COURSE, RESULTATS }

/** État complet de l'UI course */
data class CourseUiState(
    val course: Course? = null,
    val joueurLocal: Joueur? = null,
    val ecran: EcranCourse = EcranCourse.ACCUEIL,
    val chargement: Boolean = false,
    val erreur: String? = null,
    val messageInfo: String? = null
)
