package com.example.kble.race

import java.util.UUID

/** Représente un joueur dans une course */
data class Joueur(
    val id: String = UUID.randomUUID().toString().take(8),
    val nom: String,
    val position: Int = 0,
    val dernierePorte: Int = 0,
    val tourActuel: Int = 0,
    val tempsMs: Long = 0L,
    val aFini: Boolean = false
)

/** États possibles d'une course */
enum class EtatCourse { LOBBY, EN_COURS, TERMINEE }

/** Représente une course / salle */
data class Course(
    val id: String = UUID.randomUUID().toString().take(6).uppercase(),
    val nomCircuit: String = "Circuit UQAC",
    val hote: String = "",
    val joueurs: List<Joueur> = emptyList(),
    val etat: EtatCourse = EtatCourse.LOBBY,
    val nombreToursTotal: Int = 3,
    val portesParTour: Int = 4,
    val maxJoueurs: Int = 8,
    val timestampDepart: Long = 0L
)
