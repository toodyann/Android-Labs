package com.example.lab6.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.lab6.R
import com.example.lab6.map.SelectionMapView
import com.example.lab6.opengl.TerrainGlView

@Composable
fun AppRoot(
    viewModel: TerrainViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (uiState.screen) {
        Lab6Screen.MAP -> MapScreen(uiState, viewModel)
        Lab6Screen.TERRAIN -> TerrainScreen(uiState, viewModel)
    }
}

@Composable
private fun MapScreen(
    uiState: TerrainUiState,
    viewModel: TerrainViewModel,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.map_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(R.string.map_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SelectionMapView(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                selectedBounds = uiState.selectedBounds,
                onSelectionFinished = viewModel::onSelectionFinished,
            )

            uiState.selectedBounds?.let { bounds ->
                Text(
                    text = stringResource(
                        R.string.selection_bounds,
                        bounds.south,
                        bounds.west,
                        bounds.north,
                        bounds.east,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            uiState.errorMessage?.let { msg ->
                Text(text = msg, color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = viewModel::buildTerrain,
                enabled = !uiState.isLoading && uiState.selectedBounds != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.height(22.dp))
                } else {
                    Text(stringResource(R.string.build_terrain))
                }
            }
        }
    }
}

@Composable
private fun TerrainScreen(
    uiState: TerrainUiState,
    viewModel: TerrainViewModel,
) {
    val texture = uiState.texture
    val elevation = uiState.elevation

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.terrain_title),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.terrain_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            if (texture != null && elevation != null && !texture.isRecycled) {
                key(texture, elevation) {
                    AndroidView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        factory = { ctx ->
                            TerrainGlView(ctx).apply {
                                setTerrain(texture, elevation)
                                setRotations(uiState.rotationXDeg, uiState.rotationYDeg)
                            }
                        },
                        update = { view ->
                            view.setRotations(uiState.rotationXDeg, uiState.rotationYDeg)
                        },
                    )
                }
            }

            Text(stringResource(R.string.rotation_x))
            Slider(
                value = uiState.rotationXDeg,
                onValueChange = { v ->
                    viewModel.setRotation(v, uiState.rotationYDeg)
                },
                valueRange = -80f..80f,
            )

            Text(stringResource(R.string.rotation_y))
            Slider(
                value = uiState.rotationYDeg,
                onValueChange = { v ->
                    viewModel.setRotation(uiState.rotationXDeg, v)
                },
                valueRange = -180f..180f,
            )

            OutlinedButton(
                onClick = viewModel::backToMap,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.back_to_map))
            }
        }
    }
}
