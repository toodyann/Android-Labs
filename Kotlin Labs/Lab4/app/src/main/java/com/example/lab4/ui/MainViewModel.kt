package com.example.lab4.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.lab4.chat.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CurrentLocation(
    val lat: Double,
    val lon: Double,
    val accuracyM: Float,
    val timeMs: Long,
)

data class MainUiState(
    val tracking: Boolean = false,
    val currentLocation: CurrentLocation? = null,
    val followMyLocation: Boolean = true,
    val mapLat: Double? = null,
    val mapLon: Double? = null,
    val mapAccuracyM: Float? = null,
    val messages: List<com.example.lab4.chat.ChatMessage> = emptyList(),
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repo: ChatRepository = ChatRepository.create(getApplication())

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repo.observeMessages().collect { list ->
                _uiState.update { it.copy(messages = list) }
            }
        }
    }

    fun setTracking(tracking: Boolean) {
        _uiState.update { it.copy(tracking = tracking) }
    }

    fun updateCurrentLocation(lat: Double, lon: Double, accuracyM: Float, timeMs: Long) {
        val loc = CurrentLocation(lat, lon, accuracyM, timeMs)
        _uiState.update { state ->
            val shouldFollow = state.followMyLocation || state.mapLat == null || state.mapLon == null
            state.copy(
                currentLocation = loc,
                mapLat = if (shouldFollow) lat else state.mapLat,
                mapLon = if (shouldFollow) lon else state.mapLon,
                mapAccuracyM = if (shouldFollow) accuracyM else state.mapAccuracyM,
            )
        }
    }

    fun focusMap(lat: Double, lon: Double, accuracyM: Float?) {
        _uiState.update {
            it.copy(
                followMyLocation = false,
                mapLat = lat,
                mapLon = lon,
                mapAccuracyM = accuracyM,
            )
        }
    }

    fun centerOnMe() {
        val loc = _uiState.value.currentLocation ?: return
        _uiState.update {
            it.copy(
                followMyLocation = true,
                mapLat = loc.lat,
                mapLon = loc.lon,
                mapAccuracyM = loc.accuracyM,
            )
        }
    }

    fun sendText(userId: String, userName: String, text: String) {
        repo.sendMessage(userId, userName, text)
    }

    fun sendLocation(userId: String, userName: String) {
        val loc = _uiState.value.currentLocation ?: return
        repo.sendMessage(
            userId = userId,
            userName = userName,
            text = "Моє місце",
            lat = loc.lat,
            lon = loc.lon,
            accuracyM = loc.accuracyM,
        )
    }
}

