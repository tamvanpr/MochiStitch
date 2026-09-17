package com.mochistitch.core.mochismart

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Pemindai celah kertas (gutter) — fondasi rombak total mesin potong MochiStitch.
 *
 * Prinsip: potongan HANYA boleh jatuh pada baris yang seluruhnya kertas kosong.
 * Baris kertas murni tidak mungkin memotong balon, teks, atau SFX — berdasarkan
 * konstruksi, bukan berdasarkan ambang deteksi. Ini membalik logika lama
 * ("deteksi balon lalu hindari") menjadi ("buktikan baris kosong lalu potong").
 *
 * Bekerja lewat abstraksi [PixelGrid] agar logika murni bisa diuji unit tanpa
 * Bitmap Android.
 */
object GutterScanner {

    /** Sumber piksel yang bisa diuji unit dengan grid palsu. */
    interface PixelGrid {
        val width: Int
        val height: Int
        fun getRow(y: Int, out: IntArray)
    }

    /** Adaptor Bitmap Android ke [PixelGrid]. */
    class BitmapPixelGrid(private val bitmap: Bitmap) : PixelGrid {
        override val width: Int get() = bitmap.width
        override val height: Int get() = bitmap.height
        override fun getRow(y: Int, out: IntArray) {
            bitmap.getPixels(out, 0, width, 0, y, width, 1)
        }
    }

    /**
     * Estimasi luminansi kertas: median sampel tepi halaman (atas, bawah,
     * kiri, kanan) yang biasanya kertas kosong pada halaman komik.
     */
    fun estimatePaperLuminance(grid: PixelGrid): Float {
        val w = grid.width
        val h = grid.height
        if (w <= 0 || h <= 0) return 255f
        val samples = mutableListOf<Float>()
        val row = IntArray(w)
        val sampleRows = listOf(0, h / 4, h / 2, (3 * h) / 4, h - 1).distinct().filter { it in 0 until h }
        for (y in sampleRows) {
            grid.getRow(y, row)
            var x = 0
            while (x < w) {
                samples.add(PixelComparisonDetector.calculateLuminance(row[x]))
                x += max(1, w / 32)
            }
        }
        if (samples.isEmpty()) return 255f
        samples.sort()
        return samples[samples.size / 2]
    }

    /**
     * True bila baris [y] seluruhnya kertas: setiap piksel sampel menyimpang
     * maksimal [paperTolerance] dari [paperLuminance]. Satu piksel tinta saja
     * langsung menggugurkan baris (early-exit).
     */
    fun isPaperRow(
        grid: PixelGrid,
        y: Int,
        paperLuminance: Float,
        paperTolerance: Float = 12f,
        margins: Int = 0,
        sampleStep: Int = 4
    ): Boolean {
        val w = grid.width
        val h = grid.height
        if (y < 0 || y >= h || w < 2) return false
        val left = margins.coerceIn(0, max(0, (w - 2) / 2))
        val right = margins.coerceIn(0, max(0, (w - 2) / 2))
        if (w - left - right < 2) return true
        val row = IntArray(w)
        grid.getRow(y, row)
        var x = left
        while (x < w - right) {
            val lum = PixelComparisonDetector.calculateLuminance(row[x])
            if (abs(lum - paperLuminance) > paperTolerance) return false
            x += max(1, sampleStep)
        }
        return true
    }

    /**
     * Semua baris kertas murni dalam [fromY]..[toY] (inklusif, dijepit).
     * Dipindai dengan [scanStep] lalu disaring per-baris.
     */
    fun findPaperRows(
        grid: PixelGrid,
        fromY: Int,
        toY: Int,
        paperLuminance: Float,
        paperTolerance: Float = 12f,
        margins: Int = 0,
        scanStep: Int = 2
    ): List<Int> {
        val h = grid.height
        if (h <= 0) return emptyList()
        val lo = max(0, min(fromY, toY))
        val hi = min(h - 1, max(fromY, toY))
        if (lo > hi) return emptyList()
        val result = mutableListOf<Int>()
        var y = lo
        while (y <= hi) {
            if (isPaperRow(grid, y, paperLuminance, paperTolerance, margins)) {
                // Perluas ke baris kertas tetangga agar celah penuh tertangkap.
                var yy = y
                while (yy <= hi && isPaperRow(grid, yy, paperLuminance, paperTolerance, margins)) {
                    result.add(yy)
                    yy++
                }
                y = yy
            } else {
                y += max(1, scanStep)
            }
        }
        return result
    }

    /**
     * Baris kertas terdekat dari [targetY] dalam jendela
     * [targetY] ± [window]. Seri dimenangkan sisi atas (potongan pendek).
     * Null bila tidak ada.
     */
    fun nearestPaperRow(
        grid: PixelGrid,
        targetY: Int,
        window: Int,
        paperLuminance: Float,
        paperTolerance: Float = 12f,
        margins: Int = 0
    ): Int? {
        val h = grid.height
        if (h <= 0 || window < 0) return null
        val target = targetY.coerceIn(0, h - 1)
        if (isPaperRow(grid, target, paperLuminance, paperTolerance, margins)) return target
        var delta = 1
        while (delta <= window) {
            val up = target - delta
            if (up >= 0 && isPaperRow(grid, up, paperLuminance, paperTolerance, margins)) return up
            val down = target + delta
            if (down < h && isPaperRow(grid, down, paperLuminance, paperTolerance, margins)) return down
            delta++
        }
        return null
    }
}
