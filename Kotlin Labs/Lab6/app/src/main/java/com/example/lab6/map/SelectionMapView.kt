package com.example.lab6.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.lab6.data.GeoBounds
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

@Composable
fun SelectionMapView(
    modifier: Modifier = Modifier,
    initialLatitude: Double = 50.0,
    initialLongitude: Double = 36.25,
    selectedBounds: GeoBounds?,
    onSelectionFinished: (GeoBounds) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var mapView: MapView? by remember { mutableStateOf(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView?.onPause()
            mapView?.onDetach()
            mapView = null
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val overlay = SelectionRectOverlay(onSelectionFinished)
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(14.0)
                controller.setCenter(GeoPoint(initialLatitude, initialLongitude))
                overlays.add(overlay)
            }.also { mapView = it }
        },
        update = { view ->
            if (selectedBounds != null) {
                view.controller.setCenter(
                    GeoPoint(selectedBounds.centerLat, selectedBounds.centerLon),
                )
            }
        },
    )
}
