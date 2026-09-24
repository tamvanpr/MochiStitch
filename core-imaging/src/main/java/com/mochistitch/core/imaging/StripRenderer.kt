package com.mochistitch.core.imaging

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import kotlin.math.max

/**
 * v6: susun halaman/segmen PENUH vertikal. Satu-satunya operasi adalah
 * skala proporsional ke lebar strip + tumpuk. Segmen halaman raksasa
 * memakai rect sumber eksplisit (hasil rencana potong aman); halaman biasa
 * digambar utuh. Tidak ada crop di luar itu.
 *
 * Rombak v6 vs v5:
 * - Render per-placement via BitmapRegionDecoder (hanya rect sumber yang
 *   didecode), bukan full-decode lalu crop. Ini menghilangkan dua sumber
 *   "potong nyasar": (a) OOM pada halaman raksasa, (b) drift rounding
 *   float sx/sy yang menggeser potongan 1-2px ke dalam tinta.
 * - Partisi sumber dijamin eksak & menutup penuh (tanpa gap/duplikat);
 *   sample dihitung per-region (≤16MP) sehingga garis tipis tidak lolos.
 */
class StripRenderer(private val openStream: (Uri) -> InputStream?) {

    constructor(context: Context) : this({ uri -> context.contentResolver.openInputStream(uri) })

    data class Measured(val uri: Uri, val width: Int, val height: Int)

    /** Satu potong sumber dalam koordinat piksel gambar asli. */
    data class Placement(val uri: Uri, val srcTop: Int = 0, val srcBottom: Int = -1)

    data class Placed(val uri: Uri, val src: Rect?, val dst: Rect)

    /** Patch tepi beserta dimensinya (untuk uji seam yang benar). */
    data class EdgePatch(val px: IntArray, val w: Int, val h: Int)

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

    /**
     * Decode hemat untuk pindai/analisis: RGB_565, ukuran ≤ [maxPixels],
     * dengan [maxSample] sebagai batas atas inSampleSize (agar garis tipis
     * tidak lolos dari pindai). null bila gagal.
     */
    fun decodeSampled(uri: Uri, maxPixels: Long = 16_000_000L, maxSample: Int = Int.MAX_VALUE): Bitmap? {
        val (w, h) = measure(uri)
        if (w <= 0 || h <= 0) return null
        var s = 1
        while ((w / s).toLong() * (h / s).toLong() > maxPixels) s *= 2
        if (s > maxSample) s = maxSample
        val opts = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.RGB_565
            inSampleSize = max(1, s)
        }
        return try {
            openStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Pita piksel [top, bottom) selebar penuh untuk uji kesinambungan
     * (murah: region-decode, tanpa memuat seluruh gambar). Koordinat
     * dijepit ke [0, height]. null bila gagal.
     */
    fun edgeStrip(uri: Uri, width: Int, height: Int, top: Int, bottom: Int): IntArray? =
        edgePatch(uri, width, height, top, bottom)?.px

    /**
     * v6: varian [edgeStrip] yang mengembalikan dimensi patch agar
     * [SeamScan.seamContinues] bisa membandingkan baris seam yang
     * bersebelahan (bukan interior-vs-interior).
     */
    fun edgePatch(uri: Uri, width: Int, height: Int, top: Int, bottom: Int): EdgePatch? {
        if (width <= 0 || height <= 0) return null
        val t = top.coerceIn(0, height)
        val b = bottom.coerceIn(0, height)
        if (b <= t) return null
        return try {
            openStream(uri)?.use { stream ->
                val dec = BitmapRegionDecoder.newInstance(stream, false) ?: return null
                try {
                    val rect = Rect(0, t, width, b)
                    val opts = BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.RGB_565
                    }
                    val bmp = dec.decodeRegion(rect, opts) ?: return null
                    try {
                        val out = IntArray(bmp.width * bmp.height)
                        bmp.getPixels(out, 0, bmp.width, 0, 0, bmp.width, bmp.height)
                        EdgePatch(out, bmp.width, bmp.height)
                    } finally {
                        try { bmp.recycle() } catch (t: Throwable) { }
                    }
                } finally {
                    try { dec.recycle() } catch (t: Throwable) { }
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun renderStrip(
        placements: List<Placement>,
        config: StripConfig = StripConfig(),
        onProgress: (Float) -> Unit = {}
    ): Result<Bitmap> = withContext(Dispatchers.IO) {
        if (placements.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Tidak ada gambar."))
        try {
            onProgress(0.05f)
            val measured = placements.map { p ->
                val (w, h) = measure(p.uri)
                if (w <= 0 || h <= 0) {
                    return@withContext Result.failure(IllegalStateException("Gagal membaca dimensi: ${p.uri}"))
                }
                Measured(p.uri, w, h)
            }
            val byUri = measured.associateBy { it.uri }
            val stripWidth = max(1, measured.maxOf { it.width })
            // dst dihitung dari tinggi sumber tiap placement agar konsisten
            // dengan rencana pengelompokan di StripBuilder. Partisi eksak:
            // clamp dulu, lalu pastikan menutup penuh tanpa overlap.
            val items = placements.map { p ->
                val m = byUri.getValue(p.uri)
                val top = p.srcTop.coerceIn(0, m.height)
                val bottom = (if (p.srcBottom < 0) m.height else p.srcBottom).coerceIn(top, m.height)
                val segH = max(1, bottom - top)
                val dstH = (segH.toLong() * stripWidth / m.width.coerceAtLeast(1)).toInt().coerceAtLeast(1)
                Triple(p, Rect(0, top, m.width, bottom), dstH)
            }
            val stripHeight = max(1, items.sumOf { it.third })

            val strip = Bitmap.createBitmap(stripWidth, stripHeight, Bitmap.Config.RGB_565)
            val canvas = Canvas(strip)
            canvas.drawColor(config.matte.colorInt)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)

            var y = 0
            items.forEachIndexed { i, (p, srcRect, dstH) ->
                val m = byUri.getValue(p.uri)
                // Region-decode TEPAT rect sumber (bukan full-decode + crop):
                // tanpa drift float, tanpa OOM halaman raksasa.
                val region = decodeRegion(p.uri, srcRect)
                if (region == null) {
                    strip.recycle()
                    return@withContext Result.failure(IllegalStateException("Gagal decode: ${p.uri}"))
                }
                try {
                    val dst = Rect(0, y, stripWidth, y + dstH)
                    canvas.drawBitmap(region, null, dst, paint)
                } finally {
                    try { region.recycle() } catch (t: Throwable) { }
                }
                y += dstH
                onProgress(0.1f + 0.8f * ((i + 1).toFloat() / items.size.toFloat()))
            }
            onProgress(1.0f)
            Result.success(strip)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    /**
     * Decode tepat [rect] (koordinat gambar asli) dengan sample per-region
     * agar hasil ≤ ~16MP. Rect dijepit ke dimensi gambar; sample dipilih
     * dari luas REGION, bukan luas gambar penuh.
     */
    private fun decodeRegion(uri: Uri, rect: Rect): Bitmap? {
        return try {
            openStream(uri)?.use { stream ->
                val dec = BitmapRegionDecoder.newInstance(stream, false) ?: return null
                try {
                    val w = dec.width
                    val h = dec.height
                    val r = Rect(
                        rect.left.coerceIn(0, w),
                        rect.top.coerceIn(0, h),
                        rect.right.coerceIn(0, w),
                        rect.bottom.coerceIn(0, h)
                    )
                    if (r.width() <= 0 || r.height() <= 0) return null
                    val sample = regionSample(r.width(), r.height())
                    val opts = BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.RGB_565
                        inSampleSize = sample
                    }
                    dec.decodeRegion(r, opts)
                } finally {
                    try { dec.recycle() } catch (t: Throwable) { }
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Sample agar decode REGION ≤ ~16MP. */
    private fun regionSample(w: Int, h: Int): Int {
        var s = 1
        while ((w / s).toLong() * (h / s).toLong() > 16_000_000L) s *= 2
        return max(1, s)
    }
}
