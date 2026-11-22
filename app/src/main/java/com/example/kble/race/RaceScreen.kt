package com.example.kble.race

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

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
    var nom by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var erreur by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().padding(24.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        Text("Gestion de course", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(nom, { nom = it }, label = { Text("Votre pseudo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        Button(onClick = {
            if (nom.isBlank()) { erreur = "Entrez un pseudo"; return@Button }
            vm.creerCourse(nom.trim())
        }, Modifier.fillMaxWidth()) { Text("Créer une course") }
        Spacer(Modifier.height(8.dp))
        Text("— ou —", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(code, { code = it.uppercase() }, label = { Text("Code de la course") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            if (nom.isBlank()) { erreur = "Entrez un pseudo"; return@Button }
            if (code.isBlank()) { erreur = "Entrez un code"; return@Button }
            if (!vm.rejoindreCourse(code.trim(), nom.trim())) erreur = "Code invalide ou course pleine"
        }, Modifier.fillMaxWidth()) { Text("Rejoindre une course") }
        erreur?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
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
        Text("Lobby", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Code de la course", style = MaterialTheme.typography.labelMedium)
                Text(course.id, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text("${course.nomCircuit} · ${course.nombreToursTotal} tours · ${course.portesParTour} portes/tour", style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Joueurs (${course.joueurs.size}/${course.maxJoueurs})", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            itemsIndexed(course.joueurs) { i, joueur ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.padding(12.dp).fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text("${i + 1}. ${joueur.nom}")
                        if (joueur.nom == course.hote) Text("Hôte", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        if (estHote) {
            Button({ vm.lancerCourse() }, Modifier.fillMaxWidth(), enabled = course.joueurs.isNotEmpty()) {
                Text("🚦 Lancer la course !")
            }
        } else Text("En attente du lancement par l'hôte…")
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

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        // Carte de statut personnel
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(16.dp)) {
                Text("Position : ${moi.position}/${course.joueurs.size}", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text("Tour ${moi.tourActuel}/${course.nombreToursTotal} · Porte ${moi.dernierePorte}/$totalPortes")
                Text("Temps : ${"%.1f".format(moi.tempsMs / 1000.0)}s", style = MaterialTheme.typography.bodyLarge)
            }
        }
        Spacer(Modifier.height(12.dp))

        // Boutons d'action
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
            Button({ vm.enregistrerPassagePorte() }, Modifier.weight(1f), enabled = !moi.aFini) {
                Text(if (moi.aFini) "Arrivé !" else "⚡ Porte")
            }
            if (course.joueurs.size > 1) {
                OutlinedButton({ vm.simulerAdversaire() }, Modifier.weight(1f)) {
                    Text("🤖 Simul. IA")
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Classement en direct", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        // Tableau de classement
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
                            if (joueur.aFini) "${"%.1f".format(joueur.tempsMs / 1000.0)}s"
                            else "T${joueur.tourActuel} P${joueur.dernierePorte}"
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton({ vm.quitterCourse(); onRetour() }) { Text("🏳️ Abandonner la course") }
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

        // Podium top 3
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

        // Reste du classement
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
