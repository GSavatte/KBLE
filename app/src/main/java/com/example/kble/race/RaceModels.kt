package com.example.kble.race

import java.util.UUID

/** Représente un joueur dans une course */
data class Joueur(
    val id: String = UUID.randomUUID().toString().take(8),
    val nom: String,
    val position: Int = 0,          // Position dans le classement (1er, 2e…)
    val dernierePorte: Int = 0,     // Index de la dernière porte franchie
    val tempsMs: Long = 0L          // Temps écoulé depuis le départ
)

/** États possibles d'une course */
enum class EtatCourse {
    LOBBY,      // En attente de joueurs
    EN_COURS,   // Course active
    TERMINEE    // Course finie
}

/** Représente une course / salle */
data class Course(
    val id: String = UUID.randomUUID().toString().take(6).uppercase(),
    val nomCircuit: String = "Circuit UQAC",
    val hote: String = "",                  // Nom du créateur
    val joueurs: List<Joueur> = emptyList(),
    val etat: EtatCourse = EtatCourse.LOBBY,
    val nombreToursTotal: Int = 3,
    val maxJoueurs: Int = 8,
    val timestampDepart: Long = 0L
)
