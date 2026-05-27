package com.example.lab6.map

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import com.example.lab6.data.GeoBounds
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

class SelectionRectOverlay(
    private val onSelectionFinished: (GeoBounds) -> Unit,
) : Overlay() {

    private var startPoint: GeoPoint? = null
    private var endPoint: GeoPoint? = null
    private var dragging = false

    private val fillPaint = Paint().apply {
        color = 0x33_2196F3
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val strokePaint = Paint().apply {
        color = 0xFF_2196F3.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }

    override fun onTouchEvent(event: MotionEvent?, mapView: MapView?): Boolean {
        if (event == null || mapView == null) return false
        val projection = mapView.projection
        val geo = projection.fromPixels(event.x.toInt(), event.y.toInt()) as GeoPoint

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startPoint = geo
                endPoint = geo
                dragging = true
                mapView.invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging) return false
                endPoint = geo
                mapView.invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!dragging) return false
                endPoint = geo
                dragging = false
                val start = startPoint
                val end = endPoint
                if (start != null && end != null) {
                    val bounds = GeoBounds.fromPoints(start, end)
                    if (bounds.north - bounds.south > 0.0005 && bounds.east - bounds.west > 0.0005) {
                        onSelectionFinished(bounds)
                    }
                }
                mapView.invalidate()
                return true
            }
        }
        return false
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val start = startPoint ?: return
        val end = endPoint ?: return
        val p0 = mapView.projection.toPixels(start, null)
        val p1 = mapView.projection.toPixels(end, null)
        val rect = RectF(
            minOf(p0.x, p1.x).toFloat(),
            minOf(p0.y, p1.y).toFloat(),
            maxOf(p0.x, p1.x).toFloat(),
            maxOf(p0.y, p1.y).toFloat(),
        )
        canvas.drawRect(rect, fillPaint)
        canvas.drawRect(rect, strokePaint)
    }

    fun clear() {
        startPoint = null
        endPoint = null
        dragging = false
    }
}
