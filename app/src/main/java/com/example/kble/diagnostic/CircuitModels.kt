package com.example.kble.diagnostic

// Représente une balise physique étalonnée
data class BaliseBleTrack(
    val adresseMac: String,
    val nom: String,
    val distanceMetres: Double,
    val seuilRssiCalibration: Int // Le RSSI enregistré "pile en face" par l'admin
)

// Représente le circuit complet à envoyer sur Supabase
data class CircuitDto(
    val nomCircuit: String,
    val longueurTotaleMetres: Double,
    val balises: List<BaliseBleTrack>
)