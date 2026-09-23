package com.mochistitch.core.imaging

import com.mochistitch.core.imaging.PageGrouper.Sheet
import com.mochistitch.core.settings.ImageFormat
import com.mochistitch.core.settings.PackFormat
import com.mochistitch.core.settings.SplitRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupingTest {

    private fun sheets(vararg h: Int) = h.mapIndexed { i, height -> Sheet(i, height) }

    @Test
    fun testWholeIsOneBundle() {
        val groups = PageGrouper.group(sheets(100, 9000, 300), SplitRule.WHOLE, 500, 10)
        assertEquals(1, groups.size)
        assertEquals(3, groups[0].sheets.size)
    }

    @Test
    fun testHeightGroupsRespectBoundaries() {
        val groups = PageGrouper.group(sheets(400, 400, 300, 500), SplitRule.MAX_HEIGHT, 1000, 10)
        assertEquals(2, groups.size)
        assertEquals(listOf(0, 1), groups[0].sheets.map { it.order })
        assertEquals(listOf(2, 3), groups[1].sheets.map { it.order })
        assertFalse(groups[0].tallSingle)
    }

    @Test
    fun testTallSingleFlagged() {
        val groups = PageGrouper.group(sheets(400, 2500, 300), SplitRule.MAX_HEIGHT, 1000, 10)
        assertEquals(3, groups.size)
        assertTrue(groups[1].tallSingle)
        assertEquals(1, groups[1].sheets[0].order)
    }

    @Test
    fun testLinkedSheetsStayTogether() {
        val groups = PageGrouper.group(
            sheets(400, 400, 400, 400), SplitRule.MAX_HEIGHT, 1000, 10,
            linked = { a, b -> a == 1 && b == 2 }, hardCap = 2000
        )
        assertEquals(2, groups.size)
        assertEquals(listOf(0, 1, 2), groups[0].sheets.map { it.order })
        assertEquals(listOf(3), groups[1].sheets.map { it.order })
        assertFalse(groups[0].seamCut)
        assertFalse(groups[1].seamCut)
    }

    @Test
    fun testForcedBreakAtLinkedBoundaryFlagsSeam() {
        val groups = PageGrouper.group(
            sheets(600, 600, 600), SplitRule.MAX_HEIGHT, 1000, 10,
            linked = { a, b -> a == 0 && b == 1 }, hardCap = 1100
        )
        assertEquals(3, groups.size)
        assertFalse(groups[0].seamCut)
        assertTrue(groups[1].seamCut)
        assertFalse(groups[2].seamCut)
    }

    @Test
    fun testNoLinkInfoBehavesAsBefore() {
        val groups = PageGrouper.group(
            sheets(400, 400, 300, 500), SplitRule.MAX_HEIGHT, 1000, 10, null, 0
        )
        assertEquals(2, groups.size)
        assertEquals(listOf(0, 1), groups[0].sheets.map { it.order })
        assertEquals(listOf(2, 3), groups[1].sheets.map { it.order })
    }

    @Test
    fun testCountRule() {
        val groups = PageGrouper.group(sheets(50, 50, 50, 50, 50), SplitRule.PAGES_PER_PACK, 100000, 2)
        assertEquals(3, groups.size)
        assertEquals(1, groups[2].sheets.size)
    }

    @Test
    fun testNaming() {
        assertEquals(
            "S_ch02_007",
            FileNamer.numbered("S_ch{chapter}_{n}", "S", "02", 7, 3, ImageFormat.JPG)
        )
        assertEquals("k.zip", FileNamer.packName("k.zip", PackFormat.ZIP))
        assertEquals("k.cbz", FileNamer.packName("k.zip", PackFormat.CBZ))
        assertEquals("k.zip", FileNamer.packName("k.cbr", PackFormat.ZIP))
        assertEquals("k_99.zip", FileNamer.packName("k.cbz", PackFormat.ZIP, "99"))
    }

    @Test
    fun testStripConfigDefaults() {
        val c = StripConfig()
        assertEquals(StripMatte.WHITE, c.matte)
        assertEquals(90, c.quality)
    }
}
