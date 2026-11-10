package com.example.kble.diagnostic

import android.annotation.SuppressLint
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class CheckpointEvent(
    val identifiant: String,
    val rssiBrut: Int,
    val timestamp: Long = System.currentTimeMillis()
)

@SuppressLint("MissingPermission")
class BleViewModel : ViewModel() {

    // On stocke le RSSI lissé pour l'affichage diagnostic
    private val _balisesDetectees = MutableStateFlow<Map<String, Int>>(emptyMap())
    val balisesDetectees = _balisesDetectees.asStateFlow()

    // Événement émis lors du franchissement d'un checkpoint
    private val _checkpointEvent = MutableSharedFlow<CheckpointEvent>()
    val checkpointEvent = _checkpointEvent.asSharedFlow()

    // Dictionnaire pour stocker l'état précédent du lissage pour chaque balise
    private val historiqueLissage = mutableMapOf<String, Double>()

    // Anti-rebond (cooldown) pour éviter d'émettre en boucle à chaque paquet BLE
    private val dernierFranchissement = mutableMapOf<String, Long>()

    // Seuil de proximité (en dBm, ex: -50 dBm)
    private val SEUIL_CHECKPOINT_DBM = -50

    // Délai minimal entre 2 enregistrements d'un même checkpoint (ex: 5000 ms)
    private val COOLDOWN_CHECKPOINT_MS = 5000L

    // Prefix/Filtre du nom des balises recherchées/
    private val FILTRE_NOM_BALISE = "TRACK_BEACON"

    // Coefficient de lissage EMA (entre 0.0 et 1.0)
    private val alphaEma = 0.2

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val nomAppareil = result.scanRecord?.deviceName ?: result.device.name
            val adresseMac = result.device.address
            val rssiBrut = result.rssi

            val identifiant = if (!nomAppareil.isNullOrEmpty()) nomAppareil else adresseMac

            // Filtrage : On ne traite que les appareils contenant "TRACK_BEACON"
            if (!identifiant.contains(FILTRE_NOM_BALISE, ignoreCase = true)) {
                return
            }

            // ---------------------------------------------------------
            // ÉTAPE 1 : LISSAGE DU SIGNAL (EMA)
            // ---------------------------------------------------------
            val rssiLisse = if (historiqueLissage.containsKey(identifiant)) {
                val ancienneValeur = historiqueLissage[identifiant]!!
                (alphaEma * rssiBrut) + ((1.0 - alphaEma) * ancienneValeur)
            } else {
                // Première fois qu'on voit cette balise : on prend le signal brut
                rssiBrut.toDouble()
            }

            historiqueLissage[identifiant] = rssiLisse
            val rssiFinalInt = rssiLisse.roundToInt()

            Log.d("KBLE_SCAN", "Balise TRACK_BEACON: $identifiant | Brut: $rssiBrut | Lissé: $rssiFinalInt")

            // ---------------------------------------------------------
            // ÉTAPE 2 : DÉTECTION DU PIC (CheckPoint)
            // ---------------------------------------------------------
            // On vérifie le RSSI brut (détection immédiate) OU le lissé
            // avec un anti-rebond (cooldown) pour ne pas émettre à chaque trame BLE.
            val maintenant = System.currentTimeMillis()
            val dernierTemps = dernierFranchissement[identifiant] ?: 0L

            Log.d("KBLE_RACE_DEBUG", "Test franchissement pour $identifiant | Brut: $rssiBrut | Lissé: $rssiFinalInt (Seuil: $SEUIL_CHECKPOINT_DBM)")

            if ((rssiBrut >= SEUIL_CHECKPOINT_DBM || rssiFinalInt >= SEUIL_CHECKPOINT_DBM) &&
                (maintenant - dernierTemps > COOLDOWN_CHECKPOINT_MS)
            ) {
                dernierFranchissement[identifiant] = maintenant
                Log.w("KBLE_RACE", "🏁 CHECKPOINT FRANCHI POUR : $identifiant (RSSI Brut: $rssiBrut, Lissé: $rssiFinalInt) !")

                viewModelScope.launch {
                    _checkpointEvent.emit(CheckpointEvent(identifiant, rssiBrut))
                }
            }

            // Mise à jour de l'interface (pour le mode Diagnostic)
            val mapMiseAJour = _balisesDetectees.value.toMutableMap()
            mapMiseAJour[identifiant] = rssiFinalInt
            _balisesDetectees.value = mapMiseAJour
        }
    }

    fun demarrerScan(scanner: android.bluetooth.le.BluetoothLeScanner?) {
        historiqueLissage.clear()
        dernierFranchissement.clear()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner?.startScan(null, settings, scanCallback)
    }

    fun arreterScan(scanner: android.bluetooth.le.BluetoothLeScanner?) {
        scanner?.stopScan(scanCallback)
    }
}