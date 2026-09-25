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
        val x0 = (w * ignoreBorder).toInt()
        val x1 = w - x0
        val row = IntArray(w)
        val busy = BooleanArray(h)
        val ink = IntArray(h)
        for (y in 0 until h) {
            bmp.getPixels(row, 0, w, 0, y, w, 1)
            var prev = luma(row[x0])
            var lo = prev
            var hi = prev
            var count = 0
            for (x in x0 + 1 until x1) {
                val l = luma(row[x])
                if (abs(l - prev) > edgeThreshold) count++
                if (l < lo) lo = l
                if (l > hi) hi = l
                prev = l
            }
            ink[y] = count
            busy[y] = count > noisePixels || hi - lo > rangeThreshold
        }
        return RowProfile(busy, ink)
    }

    private fun luma(c: Int): Int =
        (((c shr 16) and 0xFF) * 77 + ((c shr 8) and 0xFF) * 150 + (c and 0xFF) * 29) shr 8
}
