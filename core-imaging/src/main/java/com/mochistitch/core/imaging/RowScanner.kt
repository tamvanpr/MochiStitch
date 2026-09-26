package com.mochistitch.core.imaging

import android.graphics.Bitmap
import kotlin.math.abs

data class RowProfile(val busy: BooleanArray, val ink: IntArray, val structured: BooleanArray = BooleanArray(busy.size))

object RowScanner {
    fun scan(
        bmp: Bitmap,
        edgeThreshold: Int = 24,
        rangeThreshold: Int = 40,
        ignoreBorder: Float = 0.02f,
        noisePixels: Int = 1
    ): RowProfile {
        val w = bmp.width
        val h = bmp.height
        val row = IntArray(w)
        val lumBuf = IntArray(w)
        val hist = IntArray(256)
        val busy = BooleanArray(h)
        val ink = IntArray(h)
        val structured = BooleanArray(h)
        for (y in 0 until h) {
            bmp.getPixels(row, 0, w, 0, y, w, 1)
            val (b, c, s) = scanRow(row, w, edgeThreshold, rangeThreshold, ignoreBorder, noisePixels, 0, lumBuf, hist)
            busy[y] = b
            ink[y] = c
            structured[y] = s
        }
        return RowProfile(busy, ink, structured)
    }

    fun scanBuffer(
        px: IntArray,
        w: Int,
        h: Int,
        edgeThreshold: Int = 24,
        rangeThreshold: Int = 40,
        ignoreBorder: Float = 0.02f,
        noisePixels: Int = 1
    ): RowProfile {
        require(px.size >= w * h)
        val lumBuf = IntArray(w)
        val hist = IntArray(256)
        val busy = BooleanArray(h)
        val ink = IntArray(h)
        val structured = BooleanArray(h)
        for (y in 0 until h) {
            val (b, c, s) = scanRow(px, w, edgeThreshold, rangeThreshold, ignoreBorder, noisePixels, y * w, lumBuf, hist)
            busy[y] = b
            ink[y] = c
            structured[y] = s
        }
        return RowProfile(busy, ink, structured)
    }

    /**
     * Tiga lapis cek per baris: selisih antar-piksel bertetangga (garis tepi),
     * rentang min-max (glow/gradasi), dan porsi piksel yang menyimpang dari
     * median baris (kalimat pudar/tipis: tiap piksel bedanya kecil, tapi ada
     * puluhan piksel yang beda dari latar).
     *
     * Plus sinyal "terstruktur": run gelap horizontal >= [minRun] (goresan
     * teks/garis, bukan titik screentone yang run-nya 1-3px).
     */
    private fun scanRow(
        px: IntArray,
        w: Int,
        edgeThreshold: Int,
        rangeThreshold: Int,
        ignoreBorder: Float,
        noisePixels: Int,
        base: Int,
        lumBuf: IntArray,
        hist: IntArray,
        minRun: Int = 5
    ): Triple<Boolean, Int, Boolean> {
        val x0 = (w * ignoreBorder).toInt().coerceIn(0, w - 1)
        val x1 = (w - x0).coerceIn(x0 + 1, w)
        hist.fill(0)
        var prev = luma(px[base + x0])
        lumBuf[x0] = prev
        hist[prev]++
        var lo = prev
        var hi = prev
        var count = 0
        for (x in x0 + 1 until x1) {
            val l = luma(px[base + x])
            lumBuf[x] = l
            hist[l]++
            if (abs(l - prev) > edgeThreshold) count++
            if (l < lo) lo = l
            if (l > hi) hi = l
            prev = l
        }
        if (count > noisePixels || hi - lo > rangeThreshold) {
            return Triple(true, count, longDarkRun(lumBuf, x0, x1, lo, minRun))
        }
        val n = x1 - x0
        var acc = 0
        var median = 0
        val half = n / 2
        for (v in 0..255) {
            acc += hist[v]
            if (acc > half) {
                median = v
                break
            }
        }
        var dev = 0
        for (x in x0 until x1) {
            if (abs(lumBuf[x] - median) > MEDIAN_DEVIATION) dev++
        }
        val busy = dev > maxOf(2, n / 100)
        return Triple(busy, count, busy && longDarkRun(lumBuf, x0, x1, lo, minRun))
    }

    /**
     * Run piksel GELAP (dekat ujung tergelap baris) sepanjang >= [minRun].
     * Goresan teks/garis: run 5px+. Titik screentone: run 1-3px. Uji
     * terhadap ujung gelap (bukan tengah) agar baris screentone — yang
     * terang-gelapnya selang-seling — tidak ikut lolos.
     */
    private fun longDarkRun(
        lumBuf: IntArray,
        x0: Int,
        x1: Int,
        lo: Int,
        minRun: Int
    ): Boolean {
        val darkBelow = lo + MEDIAN_DEVIATION
        var run = 0
        for (x in x0 until x1) {
            if (lumBuf[x] <= darkBelow) {
                run++
                if (run >= minRun) return true
            } else {
                run = 0
            }
        }
        return false
    }

    private fun luma(c: Int): Int =
        (((c shr 16) and 0xFF) * 77 + ((c shr 8) and 0xFF) * 150 + (c and 0xFF) * 29) shr 8

    private const val MEDIAN_DEVIATION = 16
}
