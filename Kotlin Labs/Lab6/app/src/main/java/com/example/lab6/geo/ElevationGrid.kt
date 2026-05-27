package com.example.lab6.geo

import com.example.lab6.data.GeoBounds
import kotlin.math.max

data class ElevationGrid(
    val width: Int,
    val height: Int,
    val bounds: GeoBounds,
    val heights: FloatArray,
) {
    fun sample(lat: Double, lon: Double): Float {
        val clampedLat = lat.coerceIn(bounds.south, bounds.north)
        val clampedLon = lon.coerceIn(bounds.west, bounds.east)

        val lonSpan = (bounds.east - bounds.west).coerceAtLeast(1e-9)
        val latSpan = (bounds.north - bounds.south).coerceAtLeast(1e-9)
        val u = ((clampedLon - bounds.west) / lonSpan).toFloat()
        val v = ((bounds.north - clampedLat) / latSpan).toFloat()

        val x = (u * (width - 1)).coerceIn(0f, (width - 1).toFloat())
        val y = (v * (height - 1)).coerceIn(0f, (height - 1).toFloat())
        val x0 = x.toInt().coerceIn(0, width - 1)
        val y0 = y.toInt().coerceIn(0, height - 1)
        val x1 = (x0 + 1).coerceAtMost(width - 1)
        val y1 = (y0 + 1).coerceAtMost(height - 1)
        val tx = x - x0
        val ty = y - y0
        val h00 = heights[y0 * width + x0]
        val h10 = heights[y0 * width + x1]
        val h01 = heights[y1 * width + x0]
        val h11 = heights[y1 * width + x1]
        val h0 = h00 * (1 - tx) + h10 * tx
        val h1 = h01 * (1 - tx) + h11 * tx
        return h0 * (1 - ty) + h1 * ty
    }

    fun resampleTo(targetWidth: Int, targetHeight: Int, targetBounds: GeoBounds): ElevationGrid {
        val out = FloatArray(targetWidth * targetHeight)
        for (j in 0 until targetHeight) {
            val lat = targetBounds.north - (j.toDouble() / max(targetHeight - 1, 1)) *
                (targetBounds.north - targetBounds.south)
            for (i in 0 until targetWidth) {
                val lon = targetBounds.west + (i.toDouble() / max(targetWidth - 1, 1)) *
                    (targetBounds.east - targetBounds.west)
                out[j * targetWidth + i] = sample(lat, lon)
            }
        }
        return ElevationGrid(targetWidth, targetHeight, targetBounds, out).smooth()
    }

    /** Згладжування — прибирає «гострі» піки на DEM. */
    fun smooth(radius: Int = 1): ElevationGrid {
        if (radius <= 0) return this
        val out = FloatArray(heights.size)
        for (j in 0 until height) {
            for (i in 0 until width) {
                var sum = 0f
                var count = 0
                for (dj in -radius..radius) {
                    for (di in -radius..radius) {
                        val ni = (i + di).coerceIn(0, width - 1)
                        val nj = (j + dj).coerceIn(0, height - 1)
                        sum += heights[nj * width + ni]
                        count++
                    }
                }
                out[j * width + i] = sum / count
            }
        }
        return copy(heights = out)
    }

    fun heightRangePercentile(lowPct: Float = 0.05f, highPct: Float = 0.95f): Pair<Float, Float> {
        val sorted = heights.sorted()
        val lo = sorted[(sorted.size * lowPct).toInt().coerceIn(0, sorted.lastIndex)]
        val hi = sorted[(sorted.size * highPct).toInt().coerceIn(0, sorted.lastIndex)]
        return lo to max(hi, lo + 1f)
    }

    fun copy(heights: FloatArray = this.heights): ElevationGrid =
        ElevationGrid(width, height, bounds, heights)
}
