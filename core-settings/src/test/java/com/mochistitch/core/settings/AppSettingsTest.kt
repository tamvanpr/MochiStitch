package com.mochistitch.core.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AppSettingsTest {
    @Test
    fun testIsDarkModeEnabled() {
        assertFalse(AppSettings.isDarkModeEnabled())
    }

    @Test
    fun testDefaultSettings() {
        val settings = MochiStitchSettings()
        assertEquals(OutputFormat.PNG, settings.outputFormat)
        assertEquals(90, settings.jpgQuality)
        assertEquals(90, settings.webpQuality)
        assertEquals(false, settings.webpLossless)
        assertEquals(OutputWrapperFormat.LOOSE_FILES, settings.wrapperFormat)
        assertEquals("MochiStitch", settings.projectName)
        assertEquals("1", settings.chapterName)
        assertEquals("{project}_ch{chapter}_{index}", settings.filenameTemplate)
        assertEquals(3, settings.indexPaddingDigits)
        assertEquals(SplitMode.MAX_PIXELS, settings.splitMode)
        assertEquals(5000, settings.maxPixelLength)
        assertEquals(10, settings.maxPagesPerFile)
        assertEquals(ReadingDirection.LTR, settings.readingDirection)
        assertEquals(AlignmentModeSetting.RESIZE_PROPORTIONAL, settings.alignmentMode)
        assertEquals(PaddingColorSetting.WHITE, settings.paddingColor)
    }
}
