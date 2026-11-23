package com.example.kble.race

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

/** Représente un joueur dans une course */
@Serializable
data class Joueur(
    val id: String = UUID.randomUUID().toString().take(8),
    @SerialName("course_id") val courseId: String = "",
    val nom: String = "",
    val position: Int = 0,
    @SerialName("derniere_porte") val dernierePorte: Int = 0,
    @SerialName("tour_actuel") val tourActuel: Int = 0,
    @SerialName("temps_ms") val tempsMs: Long = 0L,
    @SerialName("a_fini") val aFini: Boolean = false
)

/** États possibles d'une course */
@Serializable
enum class EtatCourse { LOBBY, EN_COURS, TERMINEE }

/** Représente une course / salle */
@Serializable
data class Course(
    val id: String = UUID.randomUUID().toString().take(6).uppercase(),
    @SerialName("circuit_id") val circuitId: String? = null,
    @SerialName("nom_circuit") val nomCircuit: String = "Circuit UQAC",
    val hote: String = "",
    @kotlinx.serialization.Transient val joueurs: List<Joueur> = emptyList(),
    val etat: EtatCourse = EtatCourse.LOBBY,
    @SerialName("nombre_tours") val nombreToursTotal: Int = 3,
    @SerialName("portes_par_tour") val portesParTour: Int = 4,
    @SerialName("max_joueurs") val maxJoueurs: Int = 8,
    @SerialName("timestamp_depart") val timestampDepart: Long = 0L
)
