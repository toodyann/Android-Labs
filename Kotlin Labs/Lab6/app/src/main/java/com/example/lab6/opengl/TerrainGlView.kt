package com.example.lab6.opengl

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import com.example.lab6.geo.ElevationGrid
import kotlin.math.abs

class TerrainGlView(context: Context) : GLSurfaceView(context) {

    val renderer: TerrainRenderer = TerrainRenderer()

    private var lastX = 0f
    private var lastY = 0f

    init {
        setEGLContextClientVersion(2)
        setPreserveEGLContextOnPause(true)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    fun setTerrain(texture: Bitmap, elevation: ElevationGrid) {
        renderer.setTerrain(texture, elevation)
        queueEvent { /* mesh already set; upload happens in onDrawFrame */ }
        requestRender()
    }

    fun setRotations(xDeg: Float, yDeg: Float) {
        renderer.rotationXDeg = xDeg
        renderer.rotationYDeg = yDeg
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        onResume()
    }

    override fun onDetachedFromWindow() {
        onPause()
        super.onDetachedFromWindow()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastX
                val dy = event.y - lastY
                if (abs(dx) > 0.5f || abs(dy) > 0.5f) {
                    renderer.rotationYDeg += dx * 0.35f
                    renderer.rotationXDeg = (renderer.rotationXDeg + dy * 0.25f).coerceIn(-80f, 80f)
                    lastX = event.x
                    lastY = event.y
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
