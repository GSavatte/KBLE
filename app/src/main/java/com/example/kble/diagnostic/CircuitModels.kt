package com.example.kble.diagnostic

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CircuitDb(
    val id: String? = null,
    @SerialName("nom_circuit") val nomCircuit: String,
    @SerialName("longueur_totale_m") val longueurTotaleMetres: Double,
    @SerialName("nombre_portes") val nombrePortes: Int = 0
)

@Serializable
data class BaliseBleTrack(
    @SerialName("circuit_id") val circuitId: String? = null,
    @SerialName("ordre_porte") val ordrePorte: Int = 0,
    @SerialName("adresse_mac") val adresseMac: String,
    val nom: String,
    @SerialName("distance_metres") val distanceMetres: Double,
    @SerialName("seuil_rssi") val seuilRssiCalibration: Int
)