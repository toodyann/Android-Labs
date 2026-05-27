package com.example.lab6.geo

import com.example.lab6.data.GeoBounds

/** Резервна DEM-сітка, якщо GeoTIFF не читається. */
object FallbackDem {

    fun createKharkivGrid(size: Int = 48): ElevationGrid {
        val west = 36.22
        val east = 36.28
        val south = 49.98
        val north = 50.02
        val heights = FloatArray(size * size)
        for (j in 0 until size) {
            for (i in 0 until size) {
                val x = (i.toFloat() / (size - 1)) * 2f - 1f
                val y = (j.toFloat() / (size - 1)) * 2f - 1f
                heights[j * size + i] = 120f + 50f * (0.55f * x * x + 0.45f * y * y)
            }
        }
        return ElevationGrid(
            width = size,
            height = size,
            bounds = GeoBounds(south, west, north, east),
            heights = heights,
        )
    }
}
