package com.mochistitch.core.imaging

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RowScannerTest {
    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()
    private val gray = 0xFF969696.toInt()

    @Test
    fun flatRowsAreFree() {
        val w = 100
        val h = 10
        val whiteProfile = RowScanner.scanBuffer(IntArray(w * h) { white }, w, h)
        assertTrue(whiteProfile.busy.none { it })
        val blackProfile = RowScanner.scanBuffer(IntArray(w * h) { black }, w, h)
        assertTrue(blackProfile.busy.none { it })
    }

    @Test
    fun thinLineIsBusy() {
        val w = 100
        val h = 20
        val px = IntArray(w * h) { white }
        for (x in 20..80) px[10 * w + x] = gray
        val profile = RowScanner.scanBuffer(px, w, h)
        assertTrue(profile.busy[10])
        for (y in 0 until h) {
            if (y != 10) assertFalse("baris $y harus bebas", profile.busy[y])
        }
    }

    @Test
    fun faintSentenceIsBusy() {
        // Kalimat pudar: tiap piksel bedanya kecil dari latar (< ambang tepi
        // dan < ambang rentang), tapi puluhan piksel menyimpang dari median.
        val w = 100
        val h = 10
        val faint = (0xFF shl 24) or (235 shl 16) or (235 shl 8) or 235
        val px = IntArray(w * h) { white }
        for (x in 20..80) px[5 * w + x] = faint
        val profile = RowScanner.scanBuffer(px, w, h)
        assertTrue("kalimat pudar harus terdeteksi sibuk", profile.busy[5])
        for (y in 0 until h) {
            if (y != 5) assertFalse("baris $y harus bebas", profile.busy[y])
        }
    }

    @Test
    fun softGradientIsBusy() {
        val w = 100
        val h = 5
        val px = IntArray(w * h) { white }
        for (x in 0 until w) {
            val v = (x * 255 / (w - 1))
            px[2 * w + x] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        val profile = RowScanner.scanBuffer(px, w, h)
        assertTrue(profile.busy[2])
    }
}
