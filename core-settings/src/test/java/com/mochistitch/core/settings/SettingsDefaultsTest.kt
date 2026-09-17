package com.mochistitch.core.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsDefaultsTest {
    @Test
    fun testDefaults() {
        val s = MochiStitchSettings()
        assertEquals(OutputFormat.JPG, s.outputFormat)
        assertEquals(OutputWrapperFormat.ZIP, s.wrapperFormat)
        assertEquals(SplitMode.MAX_PIXELS, s.splitMode)
        assertEquals(10000, s.maxPixelLength)
        assertEquals(10, s.maxPagesPerFile)
        assertTrue(s.mochiSmartEnabled)
        assertEquals(Strictness.STRICT, s.strictness)
        assertEquals(ThemeMode.SYSTEM, s.themeMode)
    }
}
