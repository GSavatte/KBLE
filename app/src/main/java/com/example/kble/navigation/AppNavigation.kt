package com.example.kble.navigation
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.kble.diagnostic.DiagnosticScreen
import com.example.kble.home.HomeScreen
import com.example.kble.race.RaceScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
    startDestination = "home"
    ) {
        // Route 1 : Accueil
        composable("home") {
            HomeScreen(
                onRejoindreCourse = {
                    navController.navigate("race")
                },
                onDiagnostic = {
                    navController.navigate("diagnostic")
                }
            )
        }

        // Route 2 : Écran de course
        composable("race") {
            RaceScreen(
                onRetour = {
                    navController.popBackStack()
                }
            )
        }
        composable("diagnostic") {
            DiagnosticScreen(
                onRetour = { navController.popBackStack() }
            )
        }
    }
}