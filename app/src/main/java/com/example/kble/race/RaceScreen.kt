package com.example.kble.race
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel

// Composant Stateful (Relié au ViewModel)
@Composable
fun RaceScreen(
    viewModel: CourseViewModel = viewModel(), // Obtient l'instance du ViewModel
    onRetour: () -> Unit
) {
    // Observe l'état du ViewModel en temps réel
    val position by viewModel.positionPilote.collectAsState()

    // Appel du composant Stateless en lui passant la valeur vers le bas et les événements vers le haut
    StatelessCourseHUD(
        position = position,
        onUseItemClick = { viewModel.utiliserObjet() },
        onQuitClick = onRetour
    )
}

// Composant Stateless
@Composable
fun StatelessCourseHUD(
    position: Int,
    onUseItemClick: () -> Unit,
    onQuitClick: () -> Unit
) {
    Column {
        Text("Votre position actuelle : $position / 4")

        Button(onClick = onUseItemClick) {
            Text("Lancer un malus !")
        }

        Button(onClick = onQuitClick) {
            Text("Abandonner la course")
        }
    }
}