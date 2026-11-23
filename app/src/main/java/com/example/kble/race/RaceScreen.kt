package com.example.kble.race

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kble.diagnostic.CircuitDb

@Composable
fun RaceScreen(viewModel: CourseViewModel = viewModel(), onRetour: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    when (state.ecran) {
        EcranCourse.ACCUEIL   -> AccueilCourseScreen(viewModel, onRetour)
        EcranCourse.LOBBY     -> LobbyScreen(viewModel, onRetour)
        EcranCourse.COURSE    -> CourseEnCoursScreen(viewModel, onRetour)
        EcranCourse.RESULTATS -> ResultatsScreen(viewModel, onRetour)
    }
}

// ═══════════════ Écran 1 : Créer / Rejoindre ═══════════════

@Composable
private fun AccueilCourseScreen(vm: CourseViewModel, onRetour: () -> Unit) {
    val state by vm.uiState.collectAsState()
    var nom by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var erreurLocale by remember { mutableStateOf<String?>(null) }

    var menuOuvert by remember { mutableStateOf(false) }
    var circuitSelectionne by remember { mutableStateOf<CircuitDb?>(null) }

    LaunchedEffect(state.circuitsDisponibles) {
        if (circuitSelectionne == null && state.circuitsDisponibles.isNotEmpty()) {
            circuitSelectionne = state.circuitsDisponibles.first()
        }
    }

    Column(Modifier.fillMaxSize().padding(24.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        Text("Gestion de course", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))

        //badge statut réseau
        Surface(
            color = if (vm.isOnline) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.small
        ) {
            Text(
                text = if (vm.isOnline) "Supabase connecté" else "Mode local",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall
            )
        }

        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = nom,
            onValueChange = { nom = it },
            label = { Text("Votre pseudo") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        if (state.circuitsDisponibles.isNotEmpty()) {
            @OptIn(ExperimentalMaterial3Api::class)
            ExposedDropdownMenuBox(
                expanded = menuOuvert,
                onExpandedChange = { menuOuvert = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = circuitSelectionne?.let { "${it.nomCircuit} (${it.nombrePortes} portes)" } ?: "Choisir un circuit",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Circuit") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuOuvert) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = menuOuvert, onDismissRequest = { menuOuvert = false }) {
                    state.circuitsDisponibles.forEach { circuit ->
                        DropdownMenuItem(
                            text = { Text("${circuit.nomCircuit} · ${"%.0f".format(circuit.longueurTotaleMetres)}m (${circuit.nombrePortes} portes)") },
                            onClick = {
                                circuitSelectionne = circuit
                                menuOuvert = false
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        Button(
            onClick = {
                if (nom.isBlank()) { erreurLocale = "Entrez un pseudo"; return@Button }
                erreurLocale = null
                vm.creerCourse(nom.trim(), circuit = circuitSelectionne)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.chargement
        ) {
            if (state.chargement) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text("Créer une course")
        }
        Spacer(Modifier.height(8.dp))
        Text("— ou —", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = code,
            onValueChange = { code = it.uppercase() },
            label = { Text("Code de la course") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                if (nom.isBlank()) { erreurLocale = "Entrez un pseudo"; return@Button }
                if (code.isBlank()) { erreurLocale = "Entrez un code"; return@Button }
                erreurLocale = null
                vm.rejoindreCourse(code.trim(), nom.trim())
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.chargement
        ) {
            if (state.chargement) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text("Rejoindre une course")
        }

        val messageErreur = erreurLocale ?: state.erreur
        messageErreur?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onRetour) { Text("← Retour à l'accueil") }
    }
}

// ═══════════════ Écran 2 : Lobby ═══════════════

@Composable
private fun LobbyScreen(vm: CourseViewModel, onRetour: () -> Unit) {
    val state by vm.uiState.collectAsState()
    val course = state.course ?: return
    val estHote = state.joueurLocal?.nom == course.hote

    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Lobby de course", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Code d'accès", style = MaterialTheme.typography.labelMedium)
                Text(course.id, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text("${course.nomCircuit} · ${course.nombreToursTotal} tours · ${course.portesParTour} portes/tour", style = MaterialTheme.typography.bodySmall)
                if (vm.isOnline) {
                    Text("Synchronisation temps réel active", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Joueurs connectés (${course.joueurs.size}/${course.maxJoueurs})", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            itemsIndexed(course.joueurs) { i, joueur ->
                val estMoi = joueur.id == state.joueurLocal?.id
                Card(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = if (estMoi) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else CardDefaults.cardColors()
                ) {
                    Row(Modifier.padding(12.dp).fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text("${i + 1}. ${joueur.nom} ${if (estMoi) "(vous)" else ""}", fontWeight = if (estMoi) FontWeight.Bold else FontWeight.Normal)
                        if (joueur.nom == course.hote) Text("Hôte", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        if (estHote) {
            Button({ vm.lancerCourse() }, Modifier.fillMaxWidth(), enabled = course.joueurs.isNotEmpty()) {
                Text("Lancer la course pour tous !")
            }
        } else {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("En attente du départ par l'hôte…", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton({ vm.quitterCourse(); onRetour() }) { Text("Quitter le lobby") }
    }
}

// ═══════════════ Écran 3 : Course + classement live ═══════════════

@Composable
private fun CourseEnCoursScreen(vm: CourseViewModel, onRetour: () -> Unit) {
    val state by vm.uiState.collectAsState()
    val course = state.course ?: return
    val moi = state.joueurLocal ?: return
    val totalPortes = course.nombreToursTotal * course.portesParTour

    // Récupération du scanner BLE
    val context = androidx.compose.ui.platform.LocalContext.current
    val bluetoothManager = context.getSystemService(android.content.Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
    val scanner = bluetoothManager.adapter?.bluetoothLeScanner

    // Démarre le scan dès que la course commence, et le coupe dès qu'on quitte l'écran
    DisposableEffect(Unit) {
        vm.demarrerScanCourse(scanner)
        onDispose { vm.arreterScanCourse(scanner) }
    }

    // Calcule le nom de la prochaine balise à franchir pour l'afficher au joueur
    val prochaineBalise = if (state.balisesDuCircuit.isNotEmpty() && !moi.aFini) {
        state.balisesDuCircuit[moi.dernierePorte % state.balisesDuCircuit.size]
    } else null

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(16.dp)) {
                Text("Position : ${moi.position}/${course.joueurs.size}", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text("Tour ${moi.tourActuel}/${course.nombreToursTotal} · Porte ${moi.dernierePorte}/$totalPortes")
                Text("Chrono : ${"%.1f".format(moi.tempsMs / 1000.0)}s", style = MaterialTheme.typography.bodyLarge)
                prochaineBalise?.let {
                    Spacer(Modifier.height(4.dp))
                    Text("🎯 Prochaine balise : ${it.nom} (seuil ${it.seuilRssiCalibration} dBm)", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
            Button({ vm.enregistrerPassagePorte() }, Modifier.weight(1f), enabled = !moi.aFini) {
                Text(if (moi.aFini) "Arrivé !" else "Valider porte")
            }
            if (!vm.isOnline && course.joueurs.size > 1) {
                OutlinedButton({ vm.simulerAdversaire() }, Modifier.weight(1f)) {
                    Text("Simulation")
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Classement en direct", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        LazyColumn(Modifier.weight(1f)) {
            val classement = course.joueurs.sortedBy { it.position }
            itemsIndexed(classement) { _, joueur ->
                val estMoi = joueur.id == moi.id
                Card(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    colors = if (estMoi) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else CardDefaults.cardColors()
                ) {
                    Row(Modifier.padding(12.dp).fillMaxWidth(), Arrangement.SpaceBetween) {
                        val medaille = when (joueur.position) { 1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> "  " }
                        Text("$medaille ${joueur.nom}", fontWeight = if (estMoi) FontWeight.Bold else FontWeight.Normal)
                        Text(
                            if (joueur.aFini) "Terminé (${"%.1f".format(joueur.tempsMs / 1000.0)}s)"
                            else "T${joueur.tourActuel} P${joueur.dernierePorte}"
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton({ vm.quitterCourse(); onRetour() }) { Text("Abandonner la course") }
    }
}

// ═══════════════ Écran 4 : Résultats / Podium ═══════════════

@Composable
private fun ResultatsScreen(vm: CourseViewModel, onRetour: () -> Unit) {
    val state by vm.uiState.collectAsState()
    val course = state.course ?: return
    val classement = course.joueurs.sortedBy { it.position }

    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Course terminée !", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(course.nomCircuit, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(24.dp))

        classement.take(3).forEach { joueur ->
            val medaille = when (joueur.position) { 1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> "" }
            Card(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = if (joueur.position == 1) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else CardDefaults.cardColors()
            ) {
                Row(Modifier.padding(16.dp).fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text("$medaille ${joueur.nom}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("${"%.1f".format(joueur.tempsMs / 1000.0)}s", style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        if (classement.size > 3) {
            Spacer(Modifier.height(16.dp))
            classement.drop(3).forEach { joueur ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 16.dp), Arrangement.SpaceBetween) {
                    Text("${joueur.position}. ${joueur.nom}")
                    Text("${"%.1f".format(joueur.tempsMs / 1000.0)}s")
                }
            }
        }

        Spacer(Modifier.weight(1f))
        Button({ vm.quitterCourse() }, Modifier.fillMaxWidth()) { Text("Nouvelle course") }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { vm.quitterCourse(); onRetour() }) { Text("← Retour à l'accueil") }
    }
}
