package com.example.lab6.opengl

import com.example.lab6.geo.ElevationGrid
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import kotlin.math.max

class TerrainMesh(
    elevation: ElevationGrid,
    textureWidth: Int,
    textureHeight: Int,
) {
    val indexCount: Int
    val vertices: FloatBuffer
    val texCoords: FloatBuffer
    val heights: FloatBuffer
    val indices: ShortBuffer

    init {
        val aspect = textureWidth.toFloat() / textureHeight.coerceAtLeast(1)
        val cols = 40
        val rows = (cols / aspect).toInt().coerceIn(12, 40)

        val sampled = elevation.resampleTo(cols, rows, elevation.bounds)
        val (minH, maxH) = sampled.heightRangePercentile()
        val range = max(maxH - minH, 1f)

        val lonSpan = (elevation.bounds.east - elevation.bounds.west).toFloat()
        val latSpan = (elevation.bounds.north - elevation.bounds.south).toFloat()
        val geoAspect = (lonSpan / latSpan.coerceAtLeast(1e-6f)).coerceIn(0.4f, 2.5f)

        val vertexFloats = FloatArray(cols * rows * 3)
        val uvFloats = FloatArray(cols * rows * 2)
        val heightFloats = FloatArray(cols * rows)
        val indexList = ArrayList<Short>(cols * rows * 6)

        val heightScale = 0.28f

        for (j in 0 until rows) {
            for (i in 0 until cols) {
                val idx = j * cols + i
                val h = sampled.heights[idx]
                val nx = ((i.toFloat() / (cols - 1)) * 2f - 1f) * geoAspect
                val nz = (j.toFloat() / (rows - 1)) * 2f - 1f
                val ny = ((h - minH) / range) * heightScale
                vertexFloats[idx * 3] = nx
                vertexFloats[idx * 3 + 1] = ny
                vertexFloats[idx * 3 + 2] = nz
                uvFloats[idx * 2] = i.toFloat() / (cols - 1)
                uvFloats[idx * 2 + 1] = j.toFloat() / (rows - 1)
                heightFloats[idx] = ((h - minH) / range).coerceIn(0f, 1f)
            }
        }

        for (j in 0 until rows - 1) {
            for (i in 0 until cols - 1) {
                val a = (j * cols + i).toShort()
                val b = (j * cols + i + 1).toShort()
                val c = ((j + 1) * cols + i).toShort()
                val d = ((j + 1) * cols + i + 1).toShort()
                indexList.add(a); indexList.add(b); indexList.add(d)
                indexList.add(a); indexList.add(d); indexList.add(c)
            }
        }

        vertices = toFloatBuffer(vertexFloats)
        texCoords = toFloatBuffer(uvFloats)
        heights = toFloatBuffer(heightFloats)
        indices = toShortBuffer(indexList.toShortArray())
        indexCount = indexList.size
    }

    private fun toFloatBuffer(data: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder())
            .asFloatBuffer().apply { put(data); position(0) }

    private fun toShortBuffer(data: ShortArray): ShortBuffer =
        ByteBuffer.allocateDirect(data.size * 2).order(ByteOrder.nativeOrder())
            .asShortBuffer().apply { put(data); position(0) }
}
