package com.example.lab2.ui

import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

@Composable
fun OsmLocationMapView(
    modifier: Modifier = Modifier,
    latitude: Double?,
    longitude: Double?,
    accuracyMeters: Float?,
) {
    val context = LocalContext.current

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(16.0)
        }
    }

    val marker = remember(mapView) {
        Marker(mapView).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "Location"
        }
    }

    val accuracyCircle = remember(mapView) {
        Polygon(mapView).apply {
            fillPaint.color = Color.argb(50, 33, 150, 243)
            outlinePaint.color = Color.argb(180, 33, 150, 243)
            outlinePaint.strokeWidth = 3f
        }
    }

    DisposableEffect(mapView) {
        mapView.overlays.add(accuracyCircle)
        mapView.overlays.add(marker)
        onDispose {
            mapView.overlays.remove(accuracyCircle)
            mapView.overlays.remove(marker)
            mapView.onDetach()
        }
    }

    LaunchedEffect(latitude, longitude, accuracyMeters) {
        if (latitude == null || longitude == null) return@LaunchedEffect
        val center = GeoPoint(latitude, longitude)
        marker.position = center
        mapView.controller.setCenter(center)

        val r = (accuracyMeters ?: 0f).coerceAtLeast(0f).toDouble()
        accuracyCircle.points = if (r > 0.0) {
            Polygon.pointsAsCircle(center, r)
        } else {
            emptyList()
        }
        mapView.invalidate()
    }

    AndroidView(
        modifier = modifier,
        factory = { mapView },
        update = { view ->
            view.onResume()
        },
    )
}

