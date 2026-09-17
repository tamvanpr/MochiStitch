package com.mochistitch.core.mochismart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeGrid(
    override val width: Int = 100,
    override val height: Int = 200,
    private val inkRows: Set<Int> = emptySet()
) : GutterScanner.PixelGrid {
    private val clean = IntArray(width) { 0xFFFFFFFF.toInt() }
    private val ink = IntArray(width) { i -> if (i in 20..79) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
    override fun getRow(y: Int, out: IntArray) {
        (if (y in inkRows) ink else clean).copyInto(out)
    }
}

class GutterScannerTest {

    @Test
    fun testPaperWhite() {
        assertEquals(255f, GutterScanner.estimatePaperLuminance(FakeGrid()), 1f)
    }

    @Test
    fun testIsPaperRow() {
        val g = FakeGrid(inkRows = setOf(50))
        assertTrue(GutterScanner.isPaperRow(g, 10, 255f))
        assertFalse(GutterScanner.isPaperRow(g, 50, 255f))
        assertFalse(GutterScanner.isPaperRow(g, -1, 255f))
    }

    @Test
    fun testNearestPaperRow() {
        val g = FakeGrid(inkRows = (48..52).toSet())
        assertEquals(40, GutterScanner.nearestPaperRow(g, 40, 10, 255f))
        assertEquals(47, GutterScanner.nearestPaperRow(g, 50, 10, 255f))
        assertNull(GutterScanner.nearestPaperRow(g, 50, 0, 255f))
    }

    @Test
    fun testSplitOnPaper_shortPage() {
        val intervals = GutterScanner.splitOnPaper(FakeGrid(), maxLength = 500)
        assertEquals(1, intervals.size)
        assertEquals(0, intervals[0].start)
        assertEquals(200, intervals[0].end)
        assertFalse(intervals[0].needsReview)
    }

    @Test
    fun testSplitOnPaper_cutsOnlyOnPaper() {
        // Tinta di 90..110 dan 150..170, batas 60 -> potongan di kertas.
        val g = FakeGrid(height = 300, inkRows = (90..110).toSet() + (150..170).toSet())
        val intervals = GutterScanner.splitOnPaper(g, maxLength = 100)
        assertTrue(intervals.size >= 2)
        for (iv in intervals.dropLast(1)) {
            assertTrue("Potongan ${iv.end} harus di kertas", iv.end < 90 || iv.end > 170 || (iv.end in 111..149))
            assertFalse(iv.needsReview)
        }
        assertEquals(300, intervals.last().end)
    }

    @Test
    fun testSplitOnPaper_fullBleed_needsReview() {
        val g = FakeGrid(inkRows = (0..199).toSet())
        val intervals = GutterScanner.splitOnPaper(g, maxLength = 100)
        assertEquals(1, intervals.size)
        assertTrue(intervals[0].needsReview)
    }
}
