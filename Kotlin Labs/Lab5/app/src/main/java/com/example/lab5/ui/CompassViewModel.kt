package com.example.lab5.ui

import android.app.Application
import android.hardware.SensorManager
import androidx.lifecycle.AndroidViewModel
import com.example.lab5.compass.CompassSensorRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class CompassUiState(
    val azimuthDeg: Float = 0f,
)

class CompassViewModel(application: Application) : AndroidViewModel(application) {

    private val sensorManager =
        application.getSystemService(SensorManager::class.java)

    private val compass = CompassSensorRepository(sensorManager)

    private val _uiState = MutableStateFlow(CompassUiState())
    val uiState: StateFlow<CompassUiState> = _uiState.asStateFlow()

    init {
        compass.onReading = { reading ->
            _uiState.update { it.copy(azimuthDeg = reading.azimuthDeg) }
        }
    }

    fun setDisplayRotation(rotation: Int) {
        compass.displayRotation = rotation
    }

    fun start() = compass.start()

    fun stop() = compass.stop()
}
