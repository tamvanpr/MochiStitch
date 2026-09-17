package com.mochistitch.core.mochismart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class InkGrid(
    override val width: Int = 80,
    override val height: Int = 200,
    private val ink: Set<Int> = emptySet()
) : PaperGutter.PixelSource {
    private val paper = IntArray(width) { 0xFFFFFFFF.toInt() }
    private val drawn = IntArray(width) { i -> if (i in 10..69) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
    override fun readRow(y: Int, out: IntArray) {
        (if (y in ink) drawn else paper).copyInto(out)
    }
}

class PaperGutterTest {

    @Test
    fun testPaperLevel() {
        assertEquals(255f, PaperGutter.paperLevel(InkGrid()), 1f)
    }

    @Test
    fun testBlankRow() {
        val g = InkGrid(ink = setOf(60))
        assertTrue(PaperGutter.isBlankRow(g, 10, 255f))
        assertFalse(PaperGutter.isBlankRow(g, 60, 255f))
    }

    @Test
    fun testClosestBlankPrefersUp() {
        val g = InkGrid(ink = (48..52).toSet())
        assertEquals(40, PaperGutter.closestBlank(g, 40, 8, 255f))
        assertEquals(47, PaperGutter.closestBlank(g, 50, 8, 255f))
        assertNull(PaperGutter.closestBlank(g, 50, 0, 255f))
    }

    @Test
    fun testShortPageSingleCut() {
        val cuts = PaperGutter.sliceTallPage(InkGrid(), 500)
        assertEquals(1, cuts.size)
        assertFalse(cuts[0].flagged)
    }

    @Test
    fun testCutsLandOnPaper() {
        val g = InkGrid(height = 300, ink = (90..110).toSet() + (150..170).toSet())
        val cuts = PaperGutter.sliceTallPage(g, 100)
        assertTrue(cuts.size >= 2)
        cuts.dropLast(1).forEach { c ->
            assertTrue("Ujung ${c.to} bukan kertas", c.to < 90 || c.to > 170 || c.to in 111..149)
            assertFalse(c.flagged)
        }
        assertEquals(300, cuts.last().to)
    }

    @Test
    fun testFullInkFlagged() {
        val cuts = PaperGutter.sliceTallPage(InkGrid(ink = (0..199).toSet()), 100)
        assertEquals(1, cuts.size)
        assertTrue(cuts[0].flagged)
    }
}
