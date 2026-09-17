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
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Merender tumpukan halaman vertikal. Satu-satunya arah yang ada.
 */
class StripRenderer(private val openStream: (Uri) -> InputStream?) {

    constructor(context: Context) : this({ uri -> context.contentResolver.openInputStream(uri) })

    data class Measured(val uri: Uri, val width: Int, val height: Int)

    data class Placed(val uri: Uri, val src: Rect, val dst: Rect, val cell: Rect)

    fun measure(uri: Uri): Pair<Int, Int> {
        return try {
            val stream = openStream(uri) ?: return Pair(0, 0)
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(stream, null, opts)
            stream.close()
            Pair(opts.outWidth, opts.outHeight)
        } catch (e: Exception) {
            Pair(0, 0)
        }
    }

    /** Susun vertikal: tiap halaman diskala proporsional ke [stripWidth]. */
    fun layout(measured: List<Measured>, stripWidth: Int, config: StripConfig): List<Placed> {
        val out = mutableListOf<Placed>()
        var y = 0
        for (m in measured) {
            val h = (m.height.toFloat() * stripWidth / m.width).roundToInt().coerceAtLeast(1)
            out.add(
                Placed(
                    uri = m.uri,
                    src = Rect(0, 0, m.width, m.height),
                    dst = Rect(0, y, stripWidth, y + h),
                    cell = Rect(0, y, stripWidth, y + h)
                )
            )
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
            val stripWidth = measured.maxOf { it.width }
            val placed = layout(measured, stripWidth, config)
            val stripHeight = placed.maxOfOrNull { it.cell.bottom } ?: 1

            val strip = Bitmap.createBitmap(max(1, stripWidth), max(1, stripHeight), Bitmap.Config.RGB_565)
            val canvas = Canvas(strip)
            canvas.drawColor(config.matte.colorInt)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

            placed.forEachIndexed { i, item ->
                val stream = openStream(item.uri)
                    ?: return@withContext Result.failure(IllegalStateException("Tidak dapat membuka: ${item.uri}"))
                val sample = sampleSize(item.src.width(), item.src.height(), item.dst.width(), item.dst.height())
                val opts = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.RGB_565
                    inSampleSize = sample
                }
                val src = BitmapFactory.decodeStream(stream, null, opts)
                stream.close()
                if (src == null) {
                    strip.recycle()
                    return@withContext Result.failure(IllegalStateException("Gagal decode: ${item.uri}"))
                }
                val scaled = Rect(
                    item.src.left / sample,
                    item.src.top / sample,
                    min(item.src.right / sample, src.width),
                    min(item.src.bottom / sample, src.height)
                )
                canvas.drawBitmap(src, scaled, item.dst, paint)
                src.recycle()
                onProgress(0.1f + 0.8f * ((i + 1).toFloat() / placed.size.toFloat()))
            }
            onProgress(1.0f)
            Result.success(strip)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    suspend fun renderToStream(
        uris: List<Uri>,
        output: OutputStream,
        config: StripConfig = StripConfig(),
        onProgress: (Float) -> Unit = {}
    ): StripOutcome = withContext(Dispatchers.IO) {
        renderStrip(uris, config, onProgress).fold(
            onSuccess = { bmp ->
                try {
                    var count = 0L
                    val meter = object : OutputStream() {
                        override fun write(b: Int) {
                            output.write(b)
                            count++
                        }

                        override fun write(b: ByteArray, off: Int, len: Int) {
                            output.write(b, off, len)
                            count += len
                        }
                    }
                    bmp.compress(config.compressFormat, config.quality, meter)
                    meter.flush()
                    val outcome = StripOutcome.Done(bmp.width, bmp.height, count)
                    bmp.recycle()
                    outcome
                } catch (e: Throwable) {
                    bmp.recycle()
                    StripOutcome.Failed(e.message ?: "Gagal kompresi", e)
                }
            },
            onFailure = { StripOutcome.Failed(it.message ?: "Gagal render", it) }
        )
    }

    private fun sampleSize(srcW: Int, srcH: Int, dstW: Int, dstH: Int): Int {
        var s = 1
        if (srcH > dstH * 2 || srcW > dstW * 2) {
            val halfH = srcH / 2
            val halfW = srcW / 2
            while ((halfH / s) >= dstH && (halfW / s) >= dstW) s *= 2
        }
        return max(1, s)
    }
}
