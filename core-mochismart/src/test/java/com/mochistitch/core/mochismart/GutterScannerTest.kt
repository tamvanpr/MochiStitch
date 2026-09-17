package com.mochistitch.core.mochismart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Grid palsu: latar kertas putih + balok "tinta" pada baris tertentu. */
private class FakeGrid(
    override val width: Int = 100,
    override val height: Int = 200,
    private val inkRows: Set<Int> = emptySet()
) : GutterScanner.PixelGrid {
    private val row = IntArray(width) { 0xFFFFFFFF.toInt() }
    private val inkRow = IntArray(width) { i -> if (i in 20..79) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
    override fun getRow(y: Int, out: IntArray) {
        val src = if (y in inkRows) inkRow else row
        src.copyInto(out)
    }
}

class GutterScannerTest {

    @Test
    fun testEstimatePaperLuminance_white() {
        val paper = GutterScanner.estimatePaperLuminance(FakeGrid())
        assertEquals(255f, paper, 1f)
    }

    @Test
    fun testIsPaperRow_cleanAndInk() {
        val grid = FakeGrid(inkRows = setOf(50))
        assertTrue(GutterScanner.isPaperRow(grid, 10, 255f))
        assertFalse(GutterScanner.isPaperRow(grid, 50, 255f))
    }

    @Test
    fun testIsPaperRow_outOfBounds() {
        val grid = FakeGrid()
        assertFalse(GutterScanner.isPaperRow(grid, -1, 255f))
        assertFalse(GutterScanner.isPaperRow(grid, 200, 255f))
    }

    @Test
    fun testNearestPaperRow_exactAndNearest() {
        val grid = FakeGrid(inkRows = (48..52).toSet())
        // Tepat di baris kertas.
        assertEquals(40, GutterScanner.nearestPaperRow(grid, 40, 10, 255f))
        // Target di dalam tinta: sisi atas menang seri (47 vs 53).
        assertEquals(47, GutterScanner.nearestPaperRow(grid, 50, 10, 255f))
        // Jendela nol dan target tinta: null.
        assertNull(GutterScanner.nearestPaperRow(grid, 50, 0, 255f))
    }

    @Test
    fun testFindSplitPoint_cutsOnPaper_noReview() {
        val grid = FakeGrid(inkRows = (90..110).toSet())
        val balloon = BoundingBox(left = 0, top = 90, right = 99, bottom = 110)
        val result = ContourDetector.findSplitPoint(
            grid = grid,
            currentPos = 0,
            targetPos = 100,
            tolerance = 30,
            protectedBoxes = listOf(balloon)
        )
        assertTrue(result.splitPosition < 90 || result.splitPosition > 110)
        assertFalse(result.needsManualReview)
    }

    @Test
    fun testFindSplitPoint_fullBleed_fallsBackWithReview() {
        // Semua baris bertinta dan satu balon menutup seluruh halaman:
        // tidak ada celah kertas di mana pun.
        val grid = FakeGrid(inkRows = (0..199).toSet())
        val balloon = BoundingBox(left = 0, top = 0, right = 99, bottom = 199)
        val result = ContourDetector.findSplitPoint(
            grid = grid,
            currentPos = 0,
            targetPos = 100,
            tolerance = 30,
            protectedBoxes = listOf(balloon)
        )
        assertNotNull(result)
        assertTrue(result.needsManualReview)
    }

    @Test
    fun testFindSplitPoint_neverInsideBalloon() {
        // Balon menutup target dan tidak ada kertas di jendela maupun sekitar:
        // setengah halaman tinta, balon di tengah area tinta.
        val grid = FakeGrid(inkRows = (0..199).toSet())
        val balloon = BoundingBox(left = 0, top = 80, right = 99, bottom = 120)
        val result = ContourDetector.findSplitPoint(
            grid = grid,
            currentPos = 0,
            targetPos = 100,
            tolerance = 10,
            protectedBoxes = listOf(balloon)
        )
        assertTrue(result.splitPosition !in 80..120)
    }
}
