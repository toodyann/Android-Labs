package com.example.lab6.geo

import android.content.Context
import com.example.lab6.data.GeoBounds
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Читає односмуговий GeoTIFF (float32 DEM) з assets.
 */
object GeoTiffDemReader {

    private const val ASSET_PATH = "dem/terrain.tif"

    fun loadFromAssets(context: Context): ElevationGrid {
        context.assets.open(ASSET_PATH).use { input ->
            return parse(input.readBytes())
        }
    }

    fun parse(bytes: ByteArray): ElevationGrid {
        require(bytes.size >= 8) { "File too small" }
        require(bytes[0] == 'I'.code.toByte() && bytes[1] == 'I'.code.toByte()) {
            "Only little-endian TIFF (II) supported"
        }

        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buf.position(2)
        val magic = buf.short.toInt() and 0xFFFF
        require(magic == 42) { "Not a TIFF file" }

        val ifdOffset = buf.int
        val tags = readIfd(buf, ifdOffset)

        val width = tags[256]?.asInt(buf) ?: error("Missing ImageWidth")
        val height = tags[257]?.asInt(buf) ?: error("Missing ImageLength")
        val stripOffset = tags[273]?.asInt(buf) ?: error("Missing StripOffsets")
        val stripBytes = tags[279]?.asInt(buf) ?: (width * height * 4)

        val heights = FloatArray(width * height)
        buf.position(stripOffset)
        buf.asFloatBuffer().get(heights, 0, (stripBytes / 4).coerceAtMost(heights.size))

        val scaleOffset = tags[33550]?.valueOrOffset
        val tieOffset = tags[33922]?.valueOrOffset

        val (west, north) = if (tieOffset != null && scaleOffset != null) {
            val tie = readDoubles(buf, tieOffset, 6)
            Pair(tie[3], tie[4])
        } else {
            Pair(36.22, 50.02)
        }

        val scale = if (scaleOffset != null) {
            readDoubles(buf, scaleOffset, 3)
        } else {
            doubleArrayOf(
                (36.28 - 36.22) / width,
                (50.02 - 49.98) / height,
                0.0,
            )
        }

        val east = west + scale[0] * width
        val south = north - scale[1] * height

        return ElevationGrid(
            width = width,
            height = height,
            bounds = GeoBounds(south = south, west = west, north = north, east = east),
            heights = heights,
        )
    }

    private fun readIfd(buf: ByteBuffer, offset: Int): Map<Int, TagValue> {
        buf.position(offset)
        val count = buf.short.toInt() and 0xFFFF
        val tags = mutableMapOf<Int, TagValue>()
        repeat(count) {
            val tag = buf.short.toInt() and 0xFFFF
            val type = buf.short.toInt() and 0xFFFF
            val countValues = buf.int
            val valueOrOffset = buf.int
            tags[tag] = TagValue(type, countValues, valueOrOffset)
        }
        return tags
    }

    private fun readDoubles(buf: ByteBuffer, offset: Int, count: Int): DoubleArray {
        buf.position(offset)
        return DoubleArray(count) { buf.double }
    }

    private data class TagValue(val type: Int, val count: Int, val valueOrOffset: Int) {
        fun asInt(buf: ByteBuffer): Int = when (type) {
            3 -> valueOrOffset and 0xFFFF // SHORT
            4 -> valueOrOffset // LONG inline
            else -> {
                buf.position(valueOrOffset)
                when (type) {
                    4 -> buf.int
                    3 -> buf.short.toInt() and 0xFFFF
                    else -> valueOrOffset
                }
            }
        }
    }
}
