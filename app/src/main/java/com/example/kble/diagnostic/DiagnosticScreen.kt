package com.example.kble.diagnostic

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun DiagnosticScreen(
    viewModel: BleViewModel = viewModel(),
    onRetour: () -> Unit
) {
    val context = LocalContext.current
    val distance by viewModel.distanceActuelle.collectAsState()
    val balisesBrutes by viewModel.balisesBrutes.collectAsState()
    val balisesValidees by viewModel.balisesValidees.collectAsState()

    // Tri dynamique : les RSSI les plus hauts (proches de 0) apparaissent en premier
    val balisesTrieesParProximite = balisesBrutes.entries.sortedByDescending { it.value }

    val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    val scanner = bluetoothManager.adapter?.bluetoothLeScanner

    var enCoursDeCalibration by remember { mutableStateOf(false) }

    val getRequiredPermissions = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACTIVITY_RECOGNITION
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACTIVITY_RECOGNITION
            )
        }
    }

    val hasPermissions = {
        getRequiredPermissions().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val canScan = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions[Manifest.permission.BLUETOOTH_SCAN] == true &&
                    permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        } else {
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        }
        val canTrackActivity = permissions[Manifest.permission.ACTIVITY_RECOGNITION] == true

        if (canScan && canTrackActivity) {
            enCoursDeCalibration = true
            viewModel.demarrerCalibration(scanner)
        }
    }

    DisposableEffect(Unit) {
        onDispose { scanner?.let { viewModel.fermerEtSauvegarderCircuit(it, "Grand Prix UQAC") } }
    }

    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        Text("🛠 Calibration & Configuration Admin", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Distance parcourue : ${String.format("%.1f", distance)} mètres", style = MaterialTheme.typography.headlineSmall)

        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (!enCoursDeCalibration) {
                    if (hasPermissions()) {
                        enCoursDeCalibration = true
                        viewModel.demarrerCalibration(scanner)
                    } else {
                        permissionLauncher.launch(getRequiredPermissions())
                    }
                } else {
                    enCoursDeCalibration = false
                    viewModel.fermerEtSauvegarderCircuit(scanner, "Grand Prix UQAC")
                }
            }) {
                Text(if (enCoursDeCalibration) "🏁 Valider & Envoyer sur Supabase" else "▶️ Démarrer l'étalonnage")
            }

            Button(onClick = onRetour) { Text("Retour") }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Section 1 : Balises détectées en direct (À toucher quand on est en face)
        if (enCoursDeCalibration) {
            Text("📡 Capteurs à proximité (Touchez celui en face de vous) :", style = MaterialTheme.typography.labelLarge)
            LazyColumn(modifier = Modifier.height(200.dp)) {
                items(balisesTrieesParProximite) { (nom, rssi) ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { viewModel.enregistrerBaliseActuelle(nom, rssi) }
                    ) {
                        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = nom)
                            Text(text = "$rssi dBm", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Section 2 : Balises déjà enregistrées dans le parcours
        Text("✅ Balises validées sur le circuit (${balisesValidees.size}) :", style = MaterialTheme.typography.labelLarge)
        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            items(balisesValidees) { balise ->
                Text("• ${balise.nom} posée à ${String.format("%.1f", balise.distanceMetres)}m (Seuil: ${balise.seuilRssiCalibration} dBm)", modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
}