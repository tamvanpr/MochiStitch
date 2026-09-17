package com.mochistitch.core.imaging

import com.mochistitch.core.imaging.PageAwareSplitter.PageRef
import com.mochistitch.core.settings.OutputFormat
import com.mochistitch.core.settings.OutputWrapperFormat
import com.mochistitch.core.settings.SplitMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageAwareSplitterTest {

    private fun pages(vararg heights: Int) = heights.mapIndexed { i, h -> PageRef(i, h) }

    @Test
    fun testNoLimit_singleGroup() {
        val groups = PageAwareSplitter.split(pages(100, 200, 300), SplitMode.NO_LIMIT, 150, 10)
        assertEquals(1, groups.size)
        assertEquals(3, groups[0].pages.size)
        assertFalse(groups[0].overflow)
    }

    @Test
    fun testMaxPixels_groupsAtPageBoundaries() {
        // 400 + 400 <= 1000, +300 > 1000 -> [0,1] [2,3? 300+500=800] ...
        val groups = PageAwareSplitter.split(pages(400, 400, 300, 500), SplitMode.MAX_PIXELS, 1000, 10)
        assertEquals(2, groups.size)
        assertEquals(listOf(0, 1), groups[0].pages.map { it.index })
        assertEquals(listOf(2, 3), groups[1].pages.map { it.index })
    }

    @Test
    fun testMaxPixels_singleHugePage_overflow() {
        val groups = PageAwareSplitter.split(pages(400, 2500, 300), SplitMode.MAX_PIXELS, 1000, 10)
        assertEquals(3, groups.size)
        assertFalse(groups[0].overflow)
        assertTrue(groups[1].overflow)
        assertEquals(listOf(1), groups[1].pages.map { it.index })
        assertFalse(groups[2].overflow)
    }

    @Test
    fun testPagesPerFile() {
        val groups = PageAwareSplitter.split(pages(100, 100, 100, 100, 100), SplitMode.PAGES_PER_FILE, 10000, 2)
        assertEquals(3, groups.size)
        assertEquals(2, groups[0].pages.size)
        assertEquals(1, groups[2].pages.size)
    }

    @Test
    fun testFilenameAndArchiveNaming() {
        assertEquals(
            "Solo_ch01_005",
            FilenameFormatter.formatFilename("Solo_ch01_{index}", "Solo", "01", 5, 3, OutputFormat.JPG)
        )
        assertEquals("k.zip", FilenameFormatter.resolveArchiveOutputName("k.zip", OutputWrapperFormat.ZIP))
        assertEquals("k.cbz", FilenameFormatter.resolveArchiveOutputName("k.zip", OutputWrapperFormat.CBZ))
        assertEquals("k.zip", FilenameFormatter.resolveArchiveOutputName("k.cbz", OutputWrapperFormat.ZIP))
        assertEquals(
            "k_20240101.zip",
            FilenameFormatter.resolveArchiveOutputName("k.zip", OutputWrapperFormat.ZIP, "20240101")
        )
    }

    @Test
    fun testMergeConfigDefaults() {
        val c = MergeConfig()
        assertEquals(AlignmentMode.RESIZE_PROPORTIONAL, c.alignmentMode)
        assertEquals(PaddingColor.WHITE, c.paddingColor)
        assertEquals(90, c.quality)
    }
}
