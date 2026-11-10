package com.example.kble.race
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CourseViewModel : ViewModel() {

    // État interne modifiable
    private val _positionPilote = MutableStateFlow(1)
    // Exposition en lecture seule pour l'interface
    val positionPilote = _positionPilote.asStateFlow()

    // Préparation pour l'asynchrone (ex: envoi d'un malus via réseau)
    fun utiliserObjet() {
        viewModelScope.launch(Dispatchers.IO) {
            // Simulation d'une attente réseau (Supabase)
            delay(1000L)

            // Modification de l'état (ex: on passe 2ème après un malus)
            _positionPilote.value = 2
        }
    }
}