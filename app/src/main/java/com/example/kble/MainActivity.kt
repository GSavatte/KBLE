package com.example.kble

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.kble.navigation.AppNavigation

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Définit le contenu visuel de l'Activity
        setContent {
            // On lance notre architecture de navigation
            AppNavigation()
        }
    }
}