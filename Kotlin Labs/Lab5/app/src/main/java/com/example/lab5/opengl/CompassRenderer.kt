package com.example.lab5.opengl

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin

class CompassRenderer : GLSurfaceView.Renderer {

    @Volatile
    var azimuthDeg: Float = 0f

    private var program: Int = 0
    private var aPos: Int = 0
    private var uMvp: Int = 0
    private var uColor: Int = 0

    private val mvp = FloatArray(16)
    private val proj = FloatArray(16)
    private val view = FloatArray(16)
    private val model = FloatArray(16)
    private val tmp = FloatArray(16)

    private lateinit var ringBuffer: FloatBuffer
    private var ringVertexCount: Int = 0

    private val needleVertices = floatArrayOf(
        0f, 0.75f, 0f,   // tip
        -0.06f, 0f, 0f,
        0.06f, 0f, 0f,
    )
    private val needleBuffer: FloatBuffer = bb(needleVertices)

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.07f, 0.08f, 0.10f, 1f)
        program = buildProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        aPos = GLES20.glGetAttribLocation(program, "aPos")
        uMvp = GLES20.glGetUniformLocation(program, "uMvp")
        uColor = GLES20.glGetUniformLocation(program, "uColor")

        buildRing()
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val aspect = width.toFloat() / height.toFloat()
        Matrix.orthoM(proj, 0, -aspect, aspect, -1f, 1f, -1f, 1f)
        Matrix.setLookAtM(view, 0, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(program)

        // Ring
        Matrix.setIdentityM(model, 0)
        composeMvp()
        GLES20.glUniform4f(uColor, 0.25f, 0.65f, 0.95f, 0.55f)
        drawLineStrip(ringBuffer, ringVertexCount)

        // Cardinal tick marks (N/E/S/W)
        drawTick(0f)
        drawTick(90f)
        drawTick(180f)
        drawTick(270f)

        // Red needle points to magnetic north; rotate opposite to device heading.
        Matrix.setIdentityM(model, 0)
        Matrix.rotateM(model, 0, -azimuthDeg, 0f, 0f, 1f)
        composeMvp()
        GLES20.glUniform4f(uColor, 0.95f, 0.25f, 0.25f, 1f)
        drawTriangles(needleBuffer, 3)
    }

    private fun drawTick(angleDeg: Float) {
        val tick = floatArrayOf(
            0f, 0.86f, 0f,
            0f, 0.72f, 0f,
        )
        val buf = bb(tick)
        Matrix.setIdentityM(model, 0)
        Matrix.rotateM(model, 0, angleDeg, 0f, 0f, 1f)
        composeMvp()
        GLES20.glUniform4f(uColor, 0.85f, 0.88f, 0.92f, 0.9f)
        drawLines(buf, 2)
    }

    private fun composeMvp() {
        Matrix.multiplyMM(tmp, 0, view, 0, model, 0)
        Matrix.multiplyMM(mvp, 0, proj, 0, tmp, 0)
        GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)
    }

    private fun drawLineStrip(buf: FloatBuffer, count: Int) {
        buf.position(0)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 3 * 4, buf)
        GLES20.glDrawArrays(GLES20.GL_LINE_STRIP, 0, count)
        GLES20.glDisableVertexAttribArray(aPos)
    }

    private fun drawLines(buf: FloatBuffer, count: Int) {
        buf.position(0)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 3 * 4, buf)
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, count)
        GLES20.glDisableVertexAttribArray(aPos)
    }

    private fun drawTriangles(buf: FloatBuffer, count: Int) {
        buf.position(0)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 3 * 4, buf)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, count)
        GLES20.glDisableVertexAttribArray(aPos)
    }

    private fun buildRing() {
        val segments = 128
        val r = 0.88f
        val verts = FloatArray((segments + 1) * 3)
        for (i in 0..segments) {
            val t = (i.toFloat() / segments.toFloat()) * (Math.PI * 2.0).toFloat()
            verts[i * 3 + 0] = (cos(t) * r)
            verts[i * 3 + 1] = (sin(t) * r)
            verts[i * 3 + 2] = 0f
        }
        ringBuffer = bb(verts)
        ringVertexCount = segments + 1
    }

    private fun buildProgram(vs: String, fs: String): Int {
        val v = compileShader(GLES20.GL_VERTEX_SHADER, vs)
        val f = compileShader(GLES20.GL_FRAGMENT_SHADER, fs)
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, v)
        GLES20.glAttachShader(p, f)
        GLES20.glLinkProgram(p)
        val link = IntArray(1)
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, link, 0)
        if (link[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(p)
            GLES20.glDeleteProgram(p)
            error("Program link failed: $log")
        }
        GLES20.glDeleteShader(v)
        GLES20.glDeleteShader(f)
        return p
    }

    private fun compileShader(type: Int, src: String): Int {
        val s = GLES20.glCreateShader(type)
        GLES20.glShaderSource(s, src)
        GLES20.glCompileShader(s)
        val ok = IntArray(1)
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(s)
            GLES20.glDeleteShader(s)
            error("Shader compile failed: $log")
        }
        return s
    }

    private fun bb(floats: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(floats.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(floats)
                position(0)
            }

    private companion object {
        private const val VERTEX_SHADER = """
            uniform mat4 uMvp;
            attribute vec4 aPos;
            void main() {
              gl_Position = uMvp * aPos;
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform vec4 uColor;
            void main() {
              gl_FragColor = uColor;
            }
        """
    }
}

