package com.mochistitch.core.imaging

import android.graphics.Bitmap
import kotlin.math.abs

data class RowProfile(val busy: BooleanArray, val ink: IntArray)

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
        for (y in 0 until h) {
            bmp.getPixels(row, 0, w, 0, y, w, 1)
            val (b, c) = scanRow(row, w, edgeThreshold, rangeThreshold, ignoreBorder, noisePixels, 0, lumBuf, hist)
            busy[y] = b
            ink[y] = c
        }
        return RowProfile(busy, ink)
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
        for (y in 0 until h) {
            val (b, c) = scanRow(px, w, edgeThreshold, rangeThreshold, ignoreBorder, noisePixels, y * w, lumBuf, hist)
            busy[y] = b
            ink[y] = c
        }
        return RowProfile(busy, ink)
    }

    /**
     * Tiga lapis cek per baris: selisih antar-piksel bertetangga (garis tepi),
     * rentang min-max (glow/gradasi), dan porsi piksel yang menyimpang dari
     * median baris (kalimat pudar/tipis: tiap piksel bedanya kecil, tapi ada
     * puluhan piksel yang beda dari latar).
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
        hist: IntArray
    ): Pair<Boolean, Int> {
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
        if (count > noisePixels || hi - lo > rangeThreshold) return true to count
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
        return (dev > maxOf(2, n / 100)) to count
    }

    private fun luma(c: Int): Int =
        (((c shr 16) and 0xFF) * 77 + ((c shr 8) and 0xFF) * 150 + (c and 0xFF) * 29) shr 8

    private companion object {
        const val MEDIAN_DEVIATION = 16
    }
}
