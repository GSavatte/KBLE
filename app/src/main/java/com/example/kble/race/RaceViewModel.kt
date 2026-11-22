package com.example.kble.race
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * ViewModel de gestion des courses.
 * Commit 1 : Création d'une course, lobby, rejoindre, classement local.
 * Le serveur distant (Supabase/WebSocket) sera branché dans un commit futur.
 */
class CourseViewModel : ViewModel() {

// --- État global observable ---
    private val _uiState = MutableStateFlow(CourseUiState())
    val uiState = _uiState.asStateFlow()

    // ========== Actions Lobby ==========

    /** Crée une nouvelle course et y place le joueur comme hôte */
    fun creerCourse(nomJoueur: String, nomCircuit: String = "Circuit UQAC", nbTours: Int = 3) {
        val hote = Joueur(nom = nomJoueur)
        val course = Course(
            nomCircuit = nomCircuit,
            hote = nomJoueur,
            joueurs = listOf(hote),
            nombreToursTotal = nbTours
        )
        _uiState.update {
            it.copy(
                course = course,
                joueurLocal = hote,
                ecran = EcranCourse.LOBBY
            )
        }
    }

    /** Rejoint une course existante par son code */
    fun rejoindreCourse(codeCourse: String, nomJoueur: String): Boolean {
        val courseActuelle = _uiState.value.course ?: return false

        // Vérifications
        if (courseActuelle.id != codeCourse.uppercase()) return false
        if (courseActuelle.etat != EtatCourse.LOBBY) return false
        if (courseActuelle.joueurs.size >= courseActuelle.maxJoueurs) return false
        if (courseActuelle.joueurs.any { it.nom == nomJoueur }) return false

        val joueur = Joueur(nom = nomJoueur)
        _uiState.update {
            it.copy(
                course = courseActuelle.copy(
                    joueurs = courseActuelle.joueurs + joueur
                ),
                joueurLocal = joueur,
                ecran = EcranCourse.LOBBY
            )
        }
        return true
    }

    /** L'hôte lance la course */
    fun lancerCourse() {
        val course = _uiState.value.course ?: return
        if (course.etat != EtatCourse.LOBBY) return
        if (_uiState.value.joueurLocal?.nom != course.hote) return

        // Attribuer la position initiale à chaque joueur
        val joueursAvecPosition = course.joueurs.mapIndexed { index, joueur ->
            joueur.copy(position = index + 1)
        }

        _uiState.update {
            it.copy(
                course = course.copy(
                    etat = EtatCourse.EN_COURS,
                    joueurs = joueursAvecPosition,
                    timestampDepart = System.currentTimeMillis()
                ),
                ecran = EcranCourse.COURSE
            )
        }
    }

    // ========== Actions en Course ==========

    /** Simule le passage d'une porte pour le joueur local (sera remplacé par le BLE) */
    fun enregistrerPassagePorte() {
        val state = _uiState.value
        val course = state.course ?: return
        val joueurLocal = state.joueurLocal ?: return
        if (course.etat != EtatCourse.EN_COURS) return

        val maintenant = System.currentTimeMillis()
        val joueursMAJ = course.joueurs.map { joueur ->
            if (joueur.id == joueurLocal.id) {
                joueur.copy(
                    dernierePorte = joueur.dernierePorte + 1,
                    tempsMs = maintenant - course.timestampDepart
                )
            } else joueur
        }

        // Recalcul du classement : plus de portes = meilleur ; à portes égales, moins de temps = meilleur
        val joueursClasses = joueursMAJ
            .sortedWith(compareByDescending<Joueur> { it.dernierePorte }.thenBy { it.tempsMs })
            .mapIndexed { index, joueur -> joueur.copy(position = index + 1) }

        val joueurMAJ = joueursClasses.first { it.id == joueurLocal.id }

        _uiState.update {
            it.copy(
                course = course.copy(joueurs = joueursClasses),
                joueurLocal = joueurMAJ
            )
        }
    }

    /** Quitter / abandonner */
    fun quitterCourse() {
        _uiState.update { CourseUiState() }
    }
}

/** Écrans du flux de course */
enum class EcranCourse {
    ACCUEIL,    // Choix créer / rejoindre
    LOBBY,      // Salle d'attente
    COURSE      // Course en cours
}

/** État complet de l'UI course */
data class CourseUiState(
    val course: Course? = null,
    val joueurLocal: Joueur? = null,
    val ecran: EcranCourse = EcranCourse.ACCUEIL
)