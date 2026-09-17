package com.mochistitch.core.mochismart

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Mochi Smart v3: HANYA pemindai kertas + pembelah halaman raksasa.
 * Tanpa OpenCV, tanpa kontur, tanpa ambang balon.
 *
 * Pemotongan normal tidak lewat sini sama sekali — PageGrouper memotong
 * tepat di batas halaman. Objek ini dipakai untuk satu kasus: satu halaman
 * tunggal yang lebih tinggi dari batas maksimum.
 */
object PaperGutter {

    interface PixelSource {
        val width: Int
        val height: Int
        fun readRow(y: Int, out: IntArray)
    }

    class BitmapSource(private val bitmap: Bitmap) : PixelSource {
        override val width: Int get() = bitmap.width
        override val height: Int get() = bitmap.height
        override fun readRow(y: Int, out: IntArray) {
            bitmap.getPixels(out, 0, width, 0, y, width, 1)
        }
    }

    fun brightness(pixel: Int): Float {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return 0.299f * r + 0.587f * g + 0.114f * b
    }

    /** Median luminansi sampel tepi sebagai warna kertas. */
    fun paperLevel(src: PixelSource): Float {
        val w = src.width
        val h = src.height
        if (w <= 0 || h <= 0) return 255f
        val samples = mutableListOf<Float>()
        val row = IntArray(w)
        for (y in listOf(0, h / 4, h / 2, (3 * h) / 4, h - 1).distinct().filter { it in 0 until h }) {
            src.readRow(y, row)
            var x = 0
            while (x < w) {
                samples.add(brightness(row[x]))
                x += max(1, w / 32)
            }
        }
        if (samples.isEmpty()) return 255f
        samples.sort()
        return samples[samples.size / 2]
    }

    /** True bila baris [y] 100% kertas (satu piksel tinta menggugurkan). */
    fun isBlankRow(
        src: PixelSource,
        y: Int,
        paper: Float,
        tolerance: Float = 12f,
        edgeIgnore: Int = 0,
        stride: Int = 4
    ): Boolean {
        val w = src.width
        val h = src.height
        if (y < 0 || y >= h || w < 2) return false
        val margin = edgeIgnore.coerceIn(0, max(0, (w - 2) / 2))
        if (w - margin * 2 < 2) return true
        val row = IntArray(w)
        src.readRow(y, row)
        var x = margin
        while (x < w - margin) {
            if (abs(brightness(row[x]) - paper) > tolerance) return false
            x += max(1, stride)
        }
        return true
    }

    /**
     * Baris kosong terdekat dari [target] dalam ±[radius]; atas menang seri.
     * Yang dikembalikan adalah TENGAH rentang baris kosong ditemukannya,
     * supaya potongan punya jarak aman dari tinta di kedua sisi (balon
     * tidak tersangkut).
     */
    fun closestBlank(
        src: PixelSource,
        target: Int,
        radius: Int,
        paper: Float,
        tolerance: Float = 12f
    ): Int? {
        val h = src.height
        if (h <= 0 || radius < 0) return null
        val t = target.coerceIn(0, h - 1)
        var found = -1
        if (isBlankRow(src, t, paper, tolerance)) {
            found = t
        } else {
            var d = 1
            while (d <= radius && found < 0) {
                val up = t - d
                if (up >= 0 && isBlankRow(src, up, paper, tolerance)) {
                    found = up
                    break
                }
                val down = t + d
                if (down < h && isBlankRow(src, down, paper, tolerance)) found = down
                d++
            }
        }
        if (found < 0) return null
        var top = found
        while (top - 1 >= 0 && isBlankRow(src, top - 1, paper, tolerance)) top--
        var bottom = found
        while (bottom + 1 < h && isBlankRow(src, bottom + 1, paper, tolerance)) bottom++
        return (top + bottom) / 2
    }

    data class Cut(val from: Int, val to: Int, val flagged: Boolean)

    /**
     * Belah halaman setinggi [src.height] di baris kertas saja.
     * Tanpa kertas sama sekali -> satu interval utuh bertanda [flagged].
     */
    fun sliceTallPage(src: PixelSource, limit: Int, tolerance: Float = 12f): List<Cut> {
        val total = src.height
        if (total <= 0 || limit <= 0) return listOf(Cut(0, total, flagged = true))
        if (total <= limit) return listOf(Cut(0, total, flagged = false))
        val paper = paperLevel(src)
        val cuts = mutableListOf<Cut>()
        var pos = 0
        while (pos < total) {
            if (total - pos <= limit) {
                cuts.add(Cut(pos, total, flagged = false))
                break
            }
            val aim = pos + limit
            val near = closestBlank(src, aim, min(limit / 2, aim - pos - 1).coerceAtLeast(0), paper, tolerance)
            var edge = near
            var flagged = false
            if (edge == null) {
                edge = closestBlank(src, aim, total, paper, tolerance)
                flagged = edge != null
            }
            if (edge == null || edge <= pos) {
                cuts.add(Cut(pos, total, flagged = true))
                break
            }
            cuts.add(Cut(pos, edge, flagged = flagged))
            pos = edge
        }
        return cuts
    }
}
