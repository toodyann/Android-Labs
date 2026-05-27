package com.example.lab6.opengl

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import com.example.lab6.geo.ElevationGrid
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class TerrainRenderer : GLSurfaceView.Renderer {

    @Volatile var rotationXDeg: Float = 30f
    @Volatile var rotationYDeg: Float = -40f

    private var mesh: TerrainMesh? = null
    private var textureId: Int = 0
    private var program: Int = 0
    private var aPos = 0
    private var aTex = 0
    private var aHeight = 0
    private var uMvp = 0
    private var uTexture = 0
    private var uHasTexture = 0

    private val mvp = FloatArray(16)
    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val model = FloatArray(16)
    private val tmp = FloatArray(16)

    private var sourceBitmap: Bitmap? = null
    @Volatile private var pendingUpload = false

    fun setTerrain(texture: Bitmap, elevation: ElevationGrid) {
        sourceBitmap = texture
        mesh = TerrainMesh(elevation, texture.width, texture.height)
        pendingUpload = true
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.18f, 0.20f, 0.26f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        textureId = 0
        program = buildProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        aPos = GLES20.glGetAttribLocation(program, "aPos")
        aTex = GLES20.glGetAttribLocation(program, "aTex")
        aHeight = GLES20.glGetAttribLocation(program, "aHeight")
        uMvp = GLES20.glGetUniformLocation(program, "uMvp")
        uTexture = GLES20.glGetUniformLocation(program, "uSampler")
        uHasTexture = GLES20.glGetUniformLocation(program, "uHasTexture")
        if (sourceBitmap != null) pendingUpload = true
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val aspect = width.toFloat() / maxOf(height, 1)
        Matrix.perspectiveM(projection, 0, 55f, aspect, 0.2f, 30f)
        Matrix.setLookAtM(view, 0, 0f, 2.2f, 2.8f, 0f, 0.2f, 0f, 0f, 1f, 0f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        if (pendingUpload) {
            uploadTexture()
            pendingUpload = false
        }

        val terrain = mesh ?: return
        if (program == 0) return

        GLES20.glUseProgram(program)

        Matrix.setIdentityM(model, 0)
        Matrix.rotateM(model, 0, rotationYDeg, 0f, 1f, 0f)
        Matrix.rotateM(model, 0, rotationXDeg, 1f, 0f, 0f)

        Matrix.multiplyMM(tmp, 0, view, 0, model, 0)
        Matrix.multiplyMM(mvp, 0, projection, 0, tmp, 0)
        GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)

        val hasTex = textureId != 0
        GLES20.glUniform1i(uHasTexture, if (hasTex) 1 else 0)
        if (hasTex) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
            GLES20.glUniform1i(uTexture, 0)
        }

        terrain.vertices.position(0)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 0, terrain.vertices)

        terrain.texCoords.position(0)
        GLES20.glEnableVertexAttribArray(aTex)
        GLES20.glVertexAttribPointer(aTex, 2, GLES20.GL_FLOAT, false, 0, terrain.texCoords)

        terrain.heights.position(0)
        GLES20.glEnableVertexAttribArray(aHeight)
        GLES20.glVertexAttribPointer(aHeight, 1, GLES20.GL_FLOAT, false, 0, terrain.heights)

        terrain.indices.position(0)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, terrain.indexCount, GLES20.GL_UNSIGNED_SHORT, terrain.indices)

        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aTex)
        GLES20.glDisableVertexAttribArray(aHeight)
    }

    private fun uploadTexture() {
        val bitmap = sourceBitmap ?: return
        if (bitmap.isRecycled || bitmap.width < 2 || bitmap.height < 2) return

        if (textureId != 0) GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        textureId = ids[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
    }

    private fun buildProgram(vs: String, fs: String): Int {
        val v = compileShader(GLES20.GL_VERTEX_SHADER, vs)
        val f = compileShader(GLES20.GL_FRAGMENT_SHADER, fs)
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, v)
        GLES20.glAttachShader(p, f)
        GLES20.glLinkProgram(p)
        val ok = IntArray(1)
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) error(GLES20.glGetProgramInfoLog(p))
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
        if (ok[0] == 0) error(GLES20.glGetShaderInfoLog(s))
        return s
    }

    private companion object {
        private const val VERTEX_SHADER = """
            uniform mat4 uMvp;
            attribute vec3 aPos;
            attribute vec2 aTex;
            attribute float aHeight;
            varying vec2 vTex;
            varying float vHeight;
            void main() {
              vTex = aTex;
              vHeight = aHeight;
              gl_Position = uMvp * vec4(aPos, 1.0);
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform sampler2D uSampler;
            uniform int uHasTexture;
            varying vec2 vTex;
            varying float vHeight;
            void main() {
              vec3 low = vec3(0.25, 0.45, 0.22);
              vec3 high = vec3(0.55, 0.75, 0.35);
              vec3 hill = mix(low, high, vHeight);
              if (uHasTexture == 1) {
                vec4 tex = texture2D(uSampler, vTex);
                if (tex.a > 0.1) {
                  gl_FragColor = vec4(tex.rgb, 1.0);
                  return;
                }
              }
              gl_FragColor = vec4(hill, 1.0);
            }
        """
    }
}
