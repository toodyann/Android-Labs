package com.example.lab6.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import com.example.lab6.data.GeoBounds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.MapTileIndex
import org.osmdroid.util.TileSystemWebMercator
import kotlin.math.max

object MapTileStitcher {

    private const val TILE_SIZE = 256
    private const val MAX_TILES_PER_AXIS = 5
    private const val MAX_TEXTURE_SIZE = 768
    private const val TIMEOUT_MS = 60_000L

    suspend fun stitchMapnikTiles(
        context: Context,
        bounds: GeoBounds,
        zoom: Int,
    ): Bitmap = withContext(Dispatchers.IO) {
        withTimeout(TIMEOUT_MS) {
            stitchInternal(context.applicationContext, bounds, zoom)
        }
    }

    private fun stitchInternal(context: Context, bounds: GeoBounds, zoom: Int): Bitmap {
        Configuration.getInstance().userAgentValue = context.packageName

        val tileSystem = TileSystemWebMercator()
        val xMin = tileSystem.getTileXFromLongitude(bounds.west, zoom)
        val xMax = tileSystem.getTileXFromLongitude(bounds.east, zoom)
        val yMin = tileSystem.getTileYFromLatitude(bounds.north, zoom)
        val yMax = tileSystem.getTileYFromLatitude(bounds.south, zoom)

        val tilesX = xMax - xMin + 1
        val tilesY = yMax - yMin + 1

        require(tilesX in 1..MAX_TILES_PER_AXIS && tilesY in 1..MAX_TILES_PER_AXIS) {
            "Занадто велика область (макс. ${MAX_TILES_PER_AXIS}×${MAX_TILES_PER_AXIS} тайлів)"
        }

        val width = tilesX * TILE_SIZE
        val height = tilesY * TILE_SIZE
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(180, 200, 170))

        val provider = MapTileProviderBasic(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
        }

        var loaded = 0
        try {
            Thread.sleep(400)
            for (tx in xMin..xMax) {
                for (ty in yMin..yMax) {
                    val tileIndex = MapTileIndex.getTileIndex(zoom, tx, ty)
                    val drawable = loadTileWithRetry(provider, tileIndex)
                    val left = (tx - xMin) * TILE_SIZE
                    val top = (ty - yMin) * TILE_SIZE
                    if (drawable != null && drawTile(canvas, drawable, left, top)) {
                        loaded++
                    }
                }
            }
        } finally {
            provider.detach()
        }

        if (loaded == 0) {
            drawFallbackMap(canvas, width, height)
        }

        return downscaleIfNeeded(bitmap)
    }

    private fun loadTileWithRetry(provider: MapTileProviderBasic, tileIndex: Long): Drawable? {
        repeat(25) {
            val drawable = provider.getMapTile(tileIndex)
            if (drawable != null && isRealTile(drawable)) return drawable
            Thread.sleep(200)
        }
        return provider.getMapTile(tileIndex)
    }

    private fun isRealTile(drawable: Drawable): Boolean {
        if (drawable !is BitmapDrawable) return true
        val bmp = drawable.bitmap ?: return false
        if (bmp.width < 64 || bmp.height < 64) return false
        val pixel = bmp.getPixel(bmp.width / 2, bmp.height / 2)
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        return r + g + b > 40
    }

    private fun drawTile(canvas: Canvas, drawable: Drawable, left: Int, top: Int): Boolean {
        val tileBitmap = (drawable as? BitmapDrawable)?.bitmap
        return if (tileBitmap != null) {
            canvas.drawBitmap(tileBitmap, left.toFloat(), top.toFloat(), null)
            true
        } else {
            drawable.setBounds(0, 0, TILE_SIZE, TILE_SIZE)
            canvas.save()
            canvas.translate(left.toFloat(), top.toFloat())
            drawable.draw(canvas)
            canvas.restore()
            true
        }
    }

    private fun drawFallbackMap(canvas: Canvas, width: Int, height: Int) {
        val paint = Paint().apply { isAntiAlias = true }
        paint.color = Color.rgb(120, 160, 100)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.color = Color.rgb(90, 130, 80)
        paint.strokeWidth = 4f
        val step = 64
        var x = 0
        while (x < width) {
            canvas.drawLine(x.toFloat(), 0f, x.toFloat(), height.toFloat(), paint)
            x += step
        }
        var y = 0
        while (y < height) {
            canvas.drawLine(0f, y.toFloat(), width.toFloat(), y.toFloat(), paint)
            y += step
        }
        paint.color = Color.rgb(200, 180, 120)
        canvas.drawCircle(width * 0.3f, height * 0.4f, 40f, paint)
        canvas.drawCircle(width * 0.7f, height * 0.6f, 55f, paint)
    }

    private fun downscaleIfNeeded(source: Bitmap): Bitmap {
        val maxSide = max(source.width, source.height)
        if (maxSide <= MAX_TEXTURE_SIZE) return source
        val scale = MAX_TEXTURE_SIZE.toFloat() / maxSide
        val w = (source.width * scale).toInt().coerceAtLeast(1)
        val h = (source.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(source, w, h, true)
        if (scaled !== source) source.recycle()
        return scaled
    }
}
