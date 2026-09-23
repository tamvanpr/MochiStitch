package com.mochistitch.core.imaging

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import kotlin.math.max

/**
 * v4: susun halaman PENUH vertikal. Satu-satunya operasi adalah
 * skala proporsional ke lebar strip + tumpuk. Tidak ada mode crop,
 * tidak ada rect sumber parsial: seluruh bitmap hasil decode
 * digambar utuh ke selnya, sehingga tidak ada piksel yang terbuang.
 */
class StripRenderer(private val openStream: (Uri) -> InputStream?) {

    constructor(context: Context) : this({ uri -> context.contentResolver.openInputStream(uri) })

    data class Measured(val uri: Uri, val width: Int, val height: Int)

    data class Placed(val uri: Uri, val dst: Rect)

    fun measure(uri: Uri): Pair<Int, Int> {
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val stream = openStream(uri)
            if (stream == null) {
                Pair(0, 0)
            } else {
                stream.use { BitmapFactory.decodeStream(it, null, opts) }
                Pair(opts.outWidth, opts.outHeight)
            }
        } catch (e: Exception) {
            Pair(0, 0)
        }
    }

    /** Susun vertikal: tiap halaman diskala proporsional ke [stripWidth]. */
    fun layout(measured: List<Measured>, stripWidth: Int): List<Placed> {
        val out = mutableListOf<Placed>()
        var y = 0
        for (m in measured) {
            val h = (m.height.toLong() * stripWidth / m.width.coerceAtLeast(1)).toInt().coerceAtLeast(1)
            out.add(Placed(uri = m.uri, dst = Rect(0, y, stripWidth, y + h)))
            y += h
        }
        return out
    }

    suspend fun renderStrip(
        uris: List<Uri>,
        config: StripConfig = StripConfig(),
        onProgress: (Float) -> Unit = {}
    ): Result<Bitmap> = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Tidak ada gambar."))
        try {
            onProgress(0.05f)
            val measured = uris.map { uri ->
                val (w, h) = measure(uri)
                if (w <= 0 || h <= 0) {
                    return@withContext Result.failure(IllegalStateException("Gagal membaca dimensi: $uri"))
                }
                Measured(uri, w, h)
            }
            val stripWidth = max(1, measured.maxOf { it.width })
            val placed = layout(measured, stripWidth)
            val stripHeight = max(1, placed.sumOf { it.dst.height() })

            val strip = Bitmap.createBitmap(stripWidth, stripHeight, Bitmap.Config.RGB_565)
            val canvas = Canvas(strip)
            canvas.drawColor(config.matte.colorInt)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

            placed.forEachIndexed { i, item ->
                val opts = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.RGB_565
                    inSampleSize = budgetSample(item.dst.width(), item.dst.height())
                }
                val src = openStream(item.uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, opts)
                }
                if (src == null) {
                    strip.recycle()
                    return@withContext Result.failure(IllegalStateException("Gagal decode: ${item.uri}"))
                }
                // src null = SELURUH bitmap digambar; tidak ada crop.
                canvas.drawBitmap(src, null as Rect?, item.dst, paint)
                src.recycle()
                onProgress(0.1f + 0.8f * ((i + 1).toFloat() / placed.size.toFloat()))
            }
            onProgress(1.0f)
            Result.success(strip)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    /** Sample agar decode ≤ ~16MP; downsample seragam tidak membuang konten. */
    private fun budgetSample(dstW: Int, dstH: Int): Int {
        var s = 1
        while ((dstW / s).toLong() * (dstH / s).toLong() > 16_000_000L) s *= 2
        return max(1, s)
    }
}
