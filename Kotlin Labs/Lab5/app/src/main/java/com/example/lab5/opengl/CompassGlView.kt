package com.example.lab5.opengl

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet

class CompassGlView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : GLSurfaceView(context, attrs) {

    val renderer: CompassRenderer = CompassRenderer()

    init {
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }
}

