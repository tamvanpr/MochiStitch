package com.mochistitch.core.imaging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BannerTemplateTest {

    private fun grid(w: Int, h: Int, fill: (x: Int, y: Int) -> Int): IntArray {
        val px = IntArray(w * h)
        // Kemas sebagai ARGB agar downscale bisa dipakai langsung.
        for (y in 0 until h) for (x in 0 until w) {
            val v = fill(x, y).coerceIn(0, 255)
            px[y * w + x] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        return BannerTemplate.downscale(px, w, h)
    }

    @Test
    fun testIdenticalIsOne() {
        val a = grid(200, 50) { x, _ -> if (x % 16 < 3) 0 else 255 }
        assertEquals(1.0, BannerTemplate.ncc(a, a), 1e-9)
    }

    @Test
    fun testInvertedIsMinusOne() {
        val a = grid(200, 50) { x, _ -> if (x % 16 < 3) 0 else 255 }
        val b = grid(200, 50) { x, _ -> if (x % 16 < 3) 255 else 0 }
        assertEquals(-1.0, BannerTemplate.ncc(a, b), 1e-9)
    }

    @Test
    fun testFlatNeverMatches() {
        val flat = grid(200, 50) { _, _ -> 255 }
        val text = grid(200, 50) { x, _ -> if (x % 16 < 3) 0 else 255 }
        assertEquals(0.0, BannerTemplate.ncc(flat, text), 1e-9)
        assertEquals(0.0, BannerTemplate.ncc(flat, flat), 1e-9)
    }

    @Test
    fun testScaledWidthsStillMatch() {
        // Template 690px vs strip 1280px: sama-sama diskala ke grid.
        val t = grid(690, 200) { x, _ -> if (x % 32 < 6) 0 else 255 }
        val s = grid(1280, 200) { x, _ -> if ((x * 690 / 1280) % 32 < 6) 0 else 255 }
        val sig = BannerTemplate.Sig(t)
        assertTrue(BannerTemplate.bestScore(s, listOf(sig)) > 0.90)
    }

    @Test
    fun testDifferentPatternsLow() {
        val a = grid(800, 200) { x, y -> (x * 7 + y * 13) % 256 }
        val b = grid(800, 200) { x, _ -> if (x % 16 < 3) 0 else 255 }
        assertTrue(BannerTemplate.ncc(a, b) < BannerTemplate.MEDIUM)
    }

    @Test
    fun testDownscaleSize() {
        val g = grid(800, 200) { _, _ -> 128 }
        assertEquals(BannerTemplate.GW * BannerTemplate.GH, g.size)
        assertTrue(g.all { it == 128 })
    }
}
