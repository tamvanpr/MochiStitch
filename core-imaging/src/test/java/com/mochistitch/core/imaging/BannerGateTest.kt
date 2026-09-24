package com.mochistitch.core.imaging

import com.mochistitch.core.common.BannerPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BannerGateTest {

    private fun stripOf(w: Int, h: Int, fill: (x: Int, y: Int) -> Int): BannerGate.Strip {
        val px = IntArray(w * h) { i -> fill(i % w, i / w) }
        return BannerGate.Strip(px, w, h)
    }

    private fun bannerStrip(w: Int, h: Int, seed: Int = 0): BannerGate.Strip {
        // Tiruan strip banner: kolom teks/logo hitam di posisi tetap.
        return stripOf(w, h) { x, _ ->
            if ((x + seed) % 16 < 3) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
    }

    private fun artStrip(w: Int, h: Int, seed: Int): BannerGate.Strip {
        // Tiruan art komik: pola beda tiap halaman.
        return stripOf(w, h) { x, y ->
            val v = (x * 7 + y * 13 + seed * 131) % 256
            (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
    }

    private val policy = BannerPolicy(stripPx = 200, minPages = 3, minFrac = 0.97)

    @Test
    fun testIdenticalStripsCrop() {
        val tops = listOf(bannerStrip(64, 8), bannerStrip(64, 8), bannerStrip(64, 8))
        val bots = listOf(bannerStrip(64, 8, seed = 5), bannerStrip(64, 8, seed = 5), bannerStrip(64, 8, seed = 5))
        val d = BannerGate.decide(tops, bots, policy)
        assertTrue(d.top.isNotEmpty())
        assertTrue(d.bottom.isNotEmpty())
    }

    @Test
    fun testDifferentStripsKeep() {
        val tops = listOf(artStrip(64, 8, 1), artStrip(64, 8, 2), artStrip(64, 8, 3))
        val bots = listOf(artStrip(64, 8, 4), artStrip(64, 8, 5), artStrip(64, 8, 6))
        val d = BannerGate.decide(tops, bots, policy)
        assertTrue(d.top.isEmpty())
        assertTrue(d.bottom.isEmpty())
    }

    @Test
    fun testTooFewPagesKeep() {
        val tops = listOf(bannerStrip(64, 8), bannerStrip(64, 8))
        val d = BannerGate.decide(tops, emptyList(), policy)
        assertTrue(d.top.isEmpty())
        assertTrue(d.bottom.isEmpty())
    }

    @Test
    fun testTopOnlyBanner() {
        val tops = listOf(bannerStrip(64, 8), bannerStrip(64, 8), bannerStrip(64, 8))
        val bots = listOf(artStrip(64, 8, 1), artStrip(64, 8, 2), artStrip(64, 8, 3))
        val d = BannerGate.decide(tops, bots, policy)
        assertTrue(d.top.isNotEmpty())
        assertTrue(d.bottom.isEmpty())
    }
}
