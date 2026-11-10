package com.example.kble.diagnostic

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun DiagnosticScreen(
    viewModel: BleViewModel = viewModel(),
    onRetour: () -> Unit
) {
    val contexte = LocalContext.current
    val balises by viewModel.balisesDetectees.collectAsState()

    var dernierCheckpoint by remember { mutableStateOf<String?>(null) }

    // Écoute des événements de checkpoint émis par le ViewModel
    LaunchedEffect(viewModel) {
        viewModel.checkpointEvent.collect { event ->
            dernierCheckpoint = "${event.identifiant} (RSSI Brut: ${event.rssiBrut} dBm)"
            Toast.makeText(
                contexte,
                "🏁 CHECKPOINT FRANCHI : ${event.identifiant}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // Accès au service Bluetooth du téléphone
    val bluetoothManager = contexte.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    val scanner = bluetoothManager.adapter?.bluetoothLeScanner

    var enCoursDeScan by remember { mutableStateOf(false) }

    // Gestionnaire de permissions demandé à l'exécution
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        Log.d("KBLE_SCAN", "3. Réponse du système pour les permissions : $permissions")

        val scanAutorise = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions[Manifest.permission.BLUETOOTH_SCAN] == true &&
                    permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        } else {
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        }

        if (scanAutorise) {
            Log.d("KBLE_SCAN", "4. Permissions OK ! Appel du ViewModel...")
            enCoursDeScan = true
            viewModel.demarrerScan(scanner)
        } else {
            Log.e("KBLE_SCAN", "ERREUR CRITIQUE : Permissions refusées par le système !")
        }
    }

    // Nettoyage : arrêter le scan quand on quitte l'écran
    DisposableEffect(Unit) {
        onDispose { viewModel.arreterScan(scanner) }
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Mode Diagnostic BLE", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                Log.d("KBLE_SCAN", "1. BOUTON CLIQUÉ")

                if (scanner == null) {
                    Log.e("KBLE_SCAN", "2. ATTENTION : Le scanner est NULL !")
                } else {
                    Log.d("KBLE_SCAN", "2. Le scanner est bien disponible.")
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    permissionLauncher.launch(arrayOf(
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ))
                } else {
                    permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
                }
            }) {
                Text(if (enCoursDeScan) "Scan en cours..." else "Lancer Scan")
            }

            Button(onClick = onRetour) { Text("Retour") }
        }

        dernierCheckpoint?.let { cp ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "🏁 Dernier checkpoint franchi : $cp",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Affichage sous forme de liste des balises trouvées
        LazyColumn {
            val listeBalises = balises.toList()
            items(listeBalises) { (nom, rssi) ->
                Text("Balise : $nom | Puissance (RSSI) : $rssi dBm")
            }
        }
    }
}