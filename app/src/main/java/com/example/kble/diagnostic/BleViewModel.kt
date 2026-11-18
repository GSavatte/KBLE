package com.example.kble.diagnostic

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.sqrt

@SuppressLint("MissingPermission")
class BleViewModel(application: Application) : AndroidViewModel(application), SensorEventListener {

    // --- États pour l'UI ---
    // Liste brute des balises détectées en direct avec leur RSSI lissé (pour affichage et tri)
    private val _balisesDetectees = MutableStateFlow<Map<String, Int>>(emptyMap())
    val balisesBrutes = _balisesDetectees.asStateFlow()

    // Liste officielle des balises validées et enregistrées par l'admin
    private val _balisesValidees = MutableStateFlow<List<BaliseBleTrack>>(emptyList())
    val balisesValidees = _balisesValidees.asStateFlow()

    private val _distanceActuelle = MutableStateFlow(0.0)
    val distanceActuelle = _distanceActuelle.asStateFlow()

    // --- Logique interne ---
    private val historiqueLissage = mutableMapOf<String, Double>()
    private val alphaEma = 0.2
    private val longueurPasMetres = 0.75

    // Podomètre multi-capteurs
    private val sensorManager = application.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepDetectorSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
    private val stepCounterSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private val accelerometerSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var totalPasCount = 0
    private var hardwareDetectorCount = 0
    private var pasAuDemarrage = -1f
    private var lastAccelerometerStepTimestamp = 0L

    private val trackingBeaconRegex = Regex("""^TRACK_BEACON_\d+$""", RegexOption.IGNORE_CASE)

    // Callback du Scanner BLE
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val nomAppareil = result.scanRecord?.deviceName ?: result.device.name
            if (nomAppareil.isNullOrEmpty() || !trackingBeaconRegex.matches(nomAppareil)) {
                return
            }

            val rssiBrut = result.rssi
            val identifiant = nomAppareil

            // Lissage EMA du signal radio
            val rssiLisse = if (historiqueLissage.containsKey(identifiant)) {
                (alphaEma * rssiBrut) + ((1.0 - alphaEma) * historiqueLissage[identifiant]!!)
            } else {
                rssiBrut.toDouble()
            }
            historiqueLissage[identifiant] = rssiLisse
            val rssiInt = Math.round(rssiLisse).toInt()

            // Mise à jour de la map globale pour le tri en temps réel
            val mapMaj = _balisesDetectees.value.toMutableMap()
            mapMaj[identifiant] = rssiInt
            _balisesDetectees.value = mapMaj
        }
    }

    // --- Gestion du Podomètre ---
    override fun onSensorChanged(event: SensorEvent?) {
        val sensorType = event?.sensor?.type ?: return

        when (sensorType) {
            Sensor.TYPE_ACCELEROMETER -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val norm = sqrt((x * x + y * y + z * z).toDouble())
                val now = System.currentTimeMillis()
                // Détection dynamique de pas via l'accéléromètre (pic > 12.0 m/s² et délai anti-rebond 380 ms)
                if (norm > 12.0 && (now - lastAccelerometerStepTimestamp) > 380) {
                    lastAccelerometerStepTimestamp = now
                    totalPasCount++
                    _distanceActuelle.value = totalPasCount * longueurPasMetres
                    Log.d("KBLE_PODOMETER", "Accel Step: pas=$totalPasCount, dist=${_distanceActuelle.value}m")
                }
            }
            Sensor.TYPE_STEP_DETECTOR -> {
                val steps = event.values[0].toInt()
                if (steps > 0) {
                    hardwareDetectorCount += steps
                    if (hardwareDetectorCount > totalPasCount) {
                        totalPasCount = hardwareDetectorCount
                        _distanceActuelle.value = totalPasCount * longueurPasMetres
                        Log.d("KBLE_PODOMETER", "Step Detector Sync: pas=$totalPasCount, dist=${_distanceActuelle.value}m")
                    }
                }
            }
            Sensor.TYPE_STEP_COUNTER -> {
                val pasTotaux = event.values[0]
                if (pasAuDemarrage == -1f) pasAuDemarrage = pasTotaux
                val pasFait = (pasTotaux - pasAuDemarrage).toInt().coerceAtLeast(0)
                if (pasFait > totalPasCount) {
                    totalPasCount = pasFait
                    _distanceActuelle.value = totalPasCount * longueurPasMetres
                    Log.d("KBLE_PODOMETER", "Step Counter Sync: pas=$totalPasCount, dist=${_distanceActuelle.value}m")
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // --- Actions Admin ---
    fun demarrerCalibration(scanner: android.bluetooth.le.BluetoothLeScanner?) {
        historiqueLissage.clear()
        _balisesValidees.value = emptyList()
        totalPasCount = 0
        hardwareDetectorCount = 0
        pasAuDemarrage = -1f
        _distanceActuelle.value = 0.0

        // 1. Toujours enregistrer l'accéléromètre pour des mises à jour réactives en temps réel
        accelerometerSensor?.also {
            val reg = sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            Log.d("KBLE_PODOMETER", "Accéléromètre temps réel enregistré: $reg")
        }

        // 2. Enregistrer également les capteurs matériels pour la synchronisation
        stepDetectorSensor?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
            Log.d("KBLE_PODOMETER", "TYPE_STEP_DETECTOR matériel enregistré")
        }

        stepCounterSensor?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
            Log.d("KBLE_PODOMETER", "TYPE_STEP_COUNTER matériel enregistré")
        }

        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner?.startScan(null, settings, scanCallback)
        Log.d("KBLE_ADMIN", "Calibration démarrée.")
    }

    // L'admin appuie sur une balise dans la liste car il est pile en face
    fun enregistrerBaliseActuelle(identifiant: String, rssiActuel: Int) {
        val dejaEnregistree = _balisesValidees.value.any { it.adresseMac == identifiant || it.nom == identifiant }

        if (!dejaEnregistree) {
            val distance = _distanceActuelle.value
            // On fixe le seuil de déclenchement à un niveau proche du RSSI actuel (ex: RSSI actuel - 5 dBm de marge)
            val nouvelleBalise = BaliseBleTrack(
                adresseMac = identifiant,
                nom = identifiant,
                distanceMetres = distance,
                seuilRssiCalibration = rssiActuel - 5
            )

            _balisesValidees.value = _balisesValidees.value + nouvelleBalise
            Log.w("KBLE_ADMIN", "📍 Balise $identifiant enregistrée à ${distance}m avec un seuil de ${nouvelleBalise.seuilRssiCalibration} dBm")
        }
    }

    fun fermerEtSauvegarderCircuit(scanner: android.bluetooth.le.BluetoothLeScanner?, nomCircuit: String) {
        scanner?.stopScan(scanCallback)
        sensorManager.unregisterListener(this)

        val circuitFinal = CircuitDto(
            nomCircuit = nomCircuit,
            longueurTotaleMetres = _distanceActuelle.value,
            balises = _balisesValidees.value
        )

        // Sauvegarde Supabase en arrière-plan (Dispatcher IO)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Exemple d'appel Supabase (à adapter selon votre initialisation client Supabase)
                // supabase.from("circuits").insert(circuitFinal)
                Log.d("KBLE_SUPABASE", "Circuit '${circuitFinal.nomCircuit}' sauvegardé avec succès (${circuitFinal.balises.size} balises) !")
            } catch (e: Exception) {
                Log.e("KBLE_SUPABASE", "Erreur lors de la sauvegarde Supabase", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        sensorManager.unregisterListener(this)
    }
}