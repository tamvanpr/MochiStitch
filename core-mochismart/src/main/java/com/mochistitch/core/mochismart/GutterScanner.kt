package com.mochistitch.core.mochismart

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Mochi Smart v2: pemindai celah kertas + pembelah halaman tunggal.
 *
 * Tidak memakai OpenCV/native — murni piksel, deterministik, dan bisa
 * diuji unit penuh. Hanya dipakai untuk SATU kasus: halaman tunggal yang
 * tingginya melebihi batas (overflow). Pemotongan normal SELALU di batas
 * halaman asli (lihat PageAwareSplitter) sehingga tidak mungkin memotong
 * balon.
 */
object GutterScanner {

    /** Sumber piksel yang bisa dipalsukan di unit test. */
    interface PixelGrid {
        val width: Int
        val height: Int
        fun getRow(y: Int, out: IntArray)
    }

    class BitmapPixelGrid(private val bitmap: Bitmap) : PixelGrid {
        override val width: Int get() = bitmap.width
        override val height: Int get() = bitmap.height
        override fun getRow(y: Int, out: IntArray) {
            bitmap.getPixels(out, 0, width, 0, y, width, 1)
        }
    }

    fun luminance(pixel: Int): Float {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return 0.299f * r + 0.587f * g + 0.114f * b
    }

    /** Estimasi luminansi kertas dari sampel tepi halaman. */
    fun estimatePaperLuminance(grid: PixelGrid): Float {
        val w = grid.width
        val h = grid.height
        if (w <= 0 || h <= 0) return 255f
        val samples = mutableListOf<Float>()
        val row = IntArray(w)
        val rows = listOf(0, h / 4, h / 2, (3 * h) / 4, h - 1).distinct().filter { it in 0 until h }
        for (y in rows) {
            grid.getRow(y, row)
            var x = 0
            while (x < w) {
                samples.add(luminance(row[x]))
                x += max(1, w / 32)
            }
        }
        if (samples.isEmpty()) return 255f
        samples.sort()
        return samples[samples.size / 2]
    }

    /** True bila baris [y] seluruhnya kertas (satu piksel tinta menggugurkan). */
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
            if (abs(luminance(row[x]) - paperLuminance) > paperTolerance) return false
            x += max(1, sampleStep)
        }
        return true
    }

    /**
     * Baris kertas terdekat dari [targetY] dalam ±[window].
     * Seri dimenangkan sisi atas. Null bila tidak ada.
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

    /**
     * Bagi halaman tunggal setinggi [totalHeight] menjadi interval-interval
     * yang semuanya berakhir di baris kertas. Interval terakhir boleh
     * berakhir di ujung. Bila tidak ada kertas sama sekali, kembalikan satu
     * interval utuh bertanda [needsReview].
     */
    data class PaperInterval(val start: Int, val end: Int, val needsReview: Boolean)

    fun splitOnPaper(
        grid: PixelGrid,
        maxLength: Int,
        paperTolerance: Float = 12f,
        expandToWholeSlice: Boolean = true
    ): List<PaperInterval> {
        val total = grid.height
        if (total <= 0 || maxLength <= 0) return listOf(PaperInterval(0, total, needsReview = true))
        if (total <= maxLength) return listOf(PaperInterval(0, total, needsReview = false))
        val paper = estimatePaperLuminance(grid)
        val intervals = mutableListOf<PaperInterval>()
        var current = 0
        while (current < total) {
            val remaining = total - current
            if (remaining <= maxLength) {
                intervals.add(PaperInterval(current, total, needsReview = false))
                break
            }
            val target = current + maxLength
            val window = min(maxLength / 2, target - current - 1).coerceAtLeast(0)
            var cut = nearestPaperRow(grid, target, window, paper, paperTolerance)
            var review = false
            if (cut == null && expandToWholeSlice) {
                cut = nearestPaperRow(grid, target, total, paper, paperTolerance)
                review = cut != null
            }
            if (cut == null || cut <= current) {
                intervals.add(PaperInterval(current, total, needsReview = true))
                break
            }
            intervals.add(PaperInterval(current, cut, needsReview = review))
            current = cut
        }
        return intervals
    }
}
