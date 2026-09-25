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
        val busy = BooleanArray(h)
        val ink = IntArray(h)
        for (y in 0 until h) {
            bmp.getPixels(row, 0, w, 0, y, w, 1)
            val (b, c) = scanRow(row, w, edgeThreshold, rangeThreshold, ignoreBorder, noisePixels)
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
        val busy = BooleanArray(h)
        val ink = IntArray(h)
        for (y in 0 until h) {
            val (b, c) = scanRow(px, w, edgeThreshold, rangeThreshold, ignoreBorder, noisePixels, y * w)
            busy[y] = b
            ink[y] = c
        }
        return RowProfile(busy, ink)
    }

    private fun scanRow(
        px: IntArray,
        w: Int,
        edgeThreshold: Int,
        rangeThreshold: Int,
        ignoreBorder: Float,
        noisePixels: Int,
        base: Int = 0
    ): Pair<Boolean, Int> {
        val x0 = (w * ignoreBorder).toInt().coerceIn(0, w - 1)
        val x1 = (w - x0).coerceIn(x0 + 1, w)
        var prev = luma(px[base + x0])
        var lo = prev
        var hi = prev
        var count = 0
        for (x in x0 + 1 until x1) {
            val l = luma(px[base + x])
            if (abs(l - prev) > edgeThreshold) count++
            if (l < lo) lo = l
            if (l > hi) hi = l
            prev = l
        }
        return (count > noisePixels || hi - lo > rangeThreshold) to count
    }

    private fun luma(c: Int): Int =
        (((c shr 16) and 0xFF) * 77 + ((c shr 8) and 0xFF) * 150 + (c and 0xFF) * 29) shr 8
}
