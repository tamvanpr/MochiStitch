package com.mochistitch.core.imaging

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeepOutTest {
    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()

    private fun boxed(w: Int, h: Int, x0: Int, y0: Int, x1: Int, y1: Int): IntArray {
        val px = IntArray(w * h) { white }
        for (x in x0..x1) {
            px[y0 * w + x] = black
            px[y1 * w + x] = black
        }
        for (y in y0..y1) {
            px[y * w + x0] = black
            px[y * w + x1] = black
        }
        return px
    }

    @Test
    fun enclosedBoxInteriorIsKeepOut() {
        val w = 120
        val h = 80
        val keep = KeepOut.enclosed(boxed(w, h, 10, 8, 50, 30), w, h)
        assertTrue("tengah kotak harus terlarang", keep[19])
        assertFalse("atas kotak (latar luar) harus bebas", keep[2])
        assertFalse("bawah kotak (latar luar) harus bebas", keep[35])
    }

    @Test
    fun smallTailGapIsClosed() {
        // Celah ekor 5px di sisi bawah: ditutup morfologi -> interior tetap terlarang.
        val w = 120
        val h = 80
        val px = boxed(w, h, 10, 8, 50, 30)
        for (x in 28..32) px[30 * w + x] = white
        val keep = KeepOut.enclosed(px, w, h)
        assertTrue("celah ekor kecil harus tertutup", keep[19])
    }

    @Test
    fun openBoxIsNotKeepOut() {
        // Kotak tanpa sisi bawah = corong ke tepi = bukan ruangan terkurung.
        val w = 120
        val h = 80
        val px = boxed(w, h, 10, 8, 50, 30)
        for (x in 10..50) px[30 * w + x] = white
        val keep = KeepOut.enclosed(px, w, h)
        assertTrue("tak ada baris terlarang", keep.none { it })
    }

    @Test
    fun flatPagesHaveNoKeepOut() {
        val w = 40
        val h = 20
        assertTrue(KeepOut.enclosed(IntArray(w * h) { white }, w, h).none { it })
        assertTrue(KeepOut.enclosed(IntArray(w * h) { black }, w, h).none { it })
    }

    @Test
    fun scatteredDotsDoNotEnclose() {
        // Screentone: titik-titik gelap tersebar tak bisa mengurung area terang.
        val w = 60
        val h = 40
        val px = IntArray(w * h) { white }
        for (y in 0 until h step 3) {
            for (x in 0 until w step 3) {
                px[y * w + x] = black
            }
        }
        assertTrue(KeepOut.enclosed(px, w, h).none { it })
    }

    @Test
    fun textZonesClusterAndExpand() {
        val structured = BooleanArray(100)
        for (y in 10..12) structured[y] = true
        for (y in 20..21) structured[y] = true
        for (y in 70..71) structured[y] = true
        val zones = KeepOut.textZones(structured, gap = 20, expand = 20)
        assertTrue(zones[0])
        assertTrue(zones[41])
        assertFalse(zones[42])
        assertTrue(zones[50])
        assertTrue(zones[91])
        assertFalse(zones[92])
    }

    @Test
    fun singleStructuredRowGetsNoExpand() {
        val structured = BooleanArray(100)
        structured[50] = true
        val zones = KeepOut.textZones(structured, gap = 20, expand = 20)
        assertTrue(zones[50])
        assertFalse(zones[49])
        assertFalse(zones[51])
    }
}
