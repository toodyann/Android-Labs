package com.example.lab6.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Bitmap.Config
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.lab6.data.GeoBounds
import com.example.lab6.geo.ElevationGrid
import com.example.lab6.geo.FallbackDem
import com.example.lab6.geo.GeoTiffDemReader
import com.example.lab6.map.MapTileStitcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Lab6Screen { MAP, TERRAIN }

data class TerrainUiState(
    val screen: Lab6Screen = Lab6Screen.MAP,
    val selectedBounds: GeoBounds? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val texture: Bitmap? = null,
    val elevation: ElevationGrid? = null,
    val rotationXDeg: Float = 25f,
    val rotationYDeg: Float = -35f,
)

class TerrainViewModel(application: Application) : AndroidViewModel(application) {

    private val demGrid: ElevationGrid by lazy {
        runCatching { GeoTiffDemReader.loadFromAssets(application) }
            .getOrElse { e ->
                android.util.Log.e("TerrainViewModel", "DEM load failed, using fallback", e)
                FallbackDem.createKharkivGrid()
            }
    }
    private var buildJob: Job? = null

    private val _uiState = MutableStateFlow(TerrainUiState())
    val uiState: StateFlow<TerrainUiState> = _uiState.asStateFlow()

    fun onSelectionFinished(bounds: GeoBounds) {
        _uiState.update { it.copy(selectedBounds = bounds, errorMessage = null) }
    }

    fun buildTerrain() {
        val bounds = _uiState.value.selectedBounds
        if (bounds == null) {
            _uiState.update { it.copy(errorMessage = "Спочатку виділіть прямокутник на карті") }
            return
        }

        buildJob?.cancel()
        buildJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                _uiState.value.texture?.recycle()

                val zoom = 15
                val stitched = MapTileStitcher.stitchMapnikTiles(getApplication(), bounds, zoom)
                val texture = stitched.copy(stitched.config ?: Config.ARGB_8888, true)
                if (stitched !== texture) stitched.recycle()
                val elevCols = 48
                val elevRows = (elevCols * texture.height.toFloat() / texture.width)
                    .toInt().coerceIn(24, 48)
                val elevation = demGrid.resampleTo(elevCols, elevRows, bounds)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        screen = Lab6Screen.TERRAIN,
                        texture = texture,
                        elevation = elevation,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Помилка побудови рельєфу",
                    )
                }
            }
        }
    }

    fun backToMap() {
        buildJob?.cancel()
        _uiState.value.texture?.recycle()
        _uiState.update {
            TerrainUiState(
                screen = Lab6Screen.MAP,
                selectedBounds = it.selectedBounds,
                rotationXDeg = it.rotationXDeg,
                rotationYDeg = it.rotationYDeg,
            )
        }
    }

    fun setRotation(xDeg: Float, yDeg: Float) {
        _uiState.update { it.copy(rotationXDeg = xDeg, rotationYDeg = yDeg) }
    }

    override fun onCleared() {
        buildJob?.cancel()
        _uiState.value.texture?.recycle()
        super.onCleared()
    }
}
