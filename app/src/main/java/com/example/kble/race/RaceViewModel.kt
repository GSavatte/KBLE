package com.example.kble.race

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * ViewModel de gestion des courses.
 * Gère : création, lobby, rejoindre, lancement, passages de portes,
 * compteur de tours, classement live, chronomètre, fin de course et résultats.
 */
class CourseViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(CourseUiState())
    val uiState = _uiState.asStateFlow()

    private var timerJob: Job? = null

    // ========== Actions Lobby ==========

    fun creerCourse(nomJoueur: String, nomCircuit: String = "Circuit UQAC", nbTours: Int = 3, portesParTour: Int = 4) {
        val hote = Joueur(nom = nomJoueur)
        val course = Course(nomCircuit = nomCircuit, hote = nomJoueur, joueurs = listOf(hote), nombreToursTotal = nbTours, portesParTour = portesParTour)
        _uiState.update { it.copy(course = course, joueurLocal = hote, ecran = EcranCourse.LOBBY) }
    }

    fun rejoindreCourse(codeCourse: String, nomJoueur: String): Boolean {
        val c = _uiState.value.course ?: return false
        if (c.id != codeCourse.uppercase() || c.etat != EtatCourse.LOBBY) return false
        if (c.joueurs.size >= c.maxJoueurs || c.joueurs.any { it.nom == nomJoueur }) return false
        val joueur = Joueur(nom = nomJoueur)
        _uiState.update { it.copy(course = c.copy(joueurs = c.joueurs + joueur), joueurLocal = joueur, ecran = EcranCourse.LOBBY) }
        return true
    }

    fun lancerCourse() {
        val c = _uiState.value.course ?: return
        if (c.etat != EtatCourse.LOBBY || _uiState.value.joueurLocal?.nom != c.hote) return
        val joueurs = c.joueurs.mapIndexed { i, j -> j.copy(position = i + 1, tourActuel = 1) }
        val depart = System.currentTimeMillis()
        _uiState.update {
            it.copy(
                course = c.copy(etat = EtatCourse.EN_COURS, joueurs = joueurs, timestampDepart = depart),
                joueurLocal = joueurs.first { j -> j.id == it.joueurLocal?.id },
                ecran = EcranCourse.COURSE
            )
        }
        demarrerChrono()
    }

    // ========== Chronomètre live ==========

    private fun demarrerChrono() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(100)
                val s = _uiState.value
                val c = s.course ?: break
                if (c.etat != EtatCourse.EN_COURS) break
                val now = System.currentTimeMillis()
                // Met à jour le temps du joueur local seulement (les autres via passage de porte)
                val joueurMAJ = s.joueurLocal?.let { j ->
                    if (!j.aFini) j.copy(tempsMs = now - c.timestampDepart) else j
                } ?: break
                _uiState.update { it.copy(joueurLocal = joueurMAJ) }
            }
        }
    }

    // ========== Actions en Course ==========

    /** Enregistre le passage d'une porte pour le joueur local (sera remplacé par le BLE) */
    fun enregistrerPassagePorte() {
        val s = _uiState.value
        val c = s.course ?: return
        val local = s.joueurLocal ?: return
        if (c.etat != EtatCourse.EN_COURS || local.aFini) return

        val now = System.currentTimeMillis()
        val nouvellePorte = local.dernierePorte + 1
        val nouveauTour = (nouvellePorte / c.portesParTour) + 1
        val aFini = nouvellePorte >= c.nombreToursTotal * c.portesParTour

        val joueursMAJ = c.joueurs.map { j ->
            if (j.id == local.id) {
                j.copy(dernierePorte = nouvellePorte, tourActuel = if (aFini) c.nombreToursTotal else nouveauTour.coerceAtMost(c.nombreToursTotal), tempsMs = now - c.timestampDepart, aFini = aFini)
            } else j
        }

        val joueursClasses = classerJoueurs(joueursMAJ)
        val moi = joueursClasses.first { it.id == local.id }

        // Vérifier si tous ont fini
        val courseTerminee = joueursClasses.all { it.aFini }

        _uiState.update {
            it.copy(
                course = c.copy(joueurs = joueursClasses, etat = if (courseTerminee) EtatCourse.TERMINEE else EtatCourse.EN_COURS),
                joueurLocal = moi,
                ecran = if (courseTerminee) EcranCourse.RESULTATS else EcranCourse.COURSE
            )
        }
        if (courseTerminee) timerJob?.cancel()
    }

    /** Simule un passage de porte pour un adversaire aléatoire (pour le test local) */
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

        val joueursMAJ = c.joueurs.map { j ->
            if (j.id == cible.id) {
                j.copy(dernierePorte = nouvellePorte, tourActuel = if (aFini) c.nombreToursTotal else nouveauTour.coerceAtMost(c.nombreToursTotal), tempsMs = now - c.timestampDepart, aFini = aFini)
            } else j
        }
        val joueursClasses = classerJoueurs(joueursMAJ)
        val moi = joueursClasses.first { it.id == local.id }
        val courseTerminee = joueursClasses.all { it.aFini }

        _uiState.update {
            it.copy(
                course = c.copy(joueurs = joueursClasses, etat = if (courseTerminee) EtatCourse.TERMINEE else EtatCourse.EN_COURS),
                joueurLocal = moi,
                ecran = if (courseTerminee) EcranCourse.RESULTATS else EcranCourse.COURSE
            )
        }
        if (courseTerminee) timerJob?.cancel()
    }

    /** Quitter / abandonner */
    fun quitterCourse() {
        timerJob?.cancel()
        _uiState.update { CourseUiState() }
    }

    private fun classerJoueurs(joueurs: List<Joueur>): List<Joueur> =
        joueurs.sortedWith(compareByDescending<Joueur> { it.dernierePorte }.thenBy { it.tempsMs })
            .mapIndexed { i, j -> j.copy(position = i + 1) }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}

/** Écrans du flux de course */
enum class EcranCourse { ACCUEIL, LOBBY, COURSE, RESULTATS }

/** État complet de l'UI course */
data class CourseUiState(
    val course: Course? = null,
    val joueurLocal: Joueur? = null,
    val ecran: EcranCourse = EcranCourse.ACCUEIL
)
