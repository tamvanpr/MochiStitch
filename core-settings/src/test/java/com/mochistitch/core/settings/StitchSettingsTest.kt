package com.mochistitch.core.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StitchSettingsTest {
    @Test
    fun testFreshDefaults() {
        val s = StitchSettings()
        assertEquals(ImageFormat.JPG, s.imageFormat)
        assertEquals(PackFormat.ZIP, s.packFormat)
        assertEquals(SplitRule.MAX_HEIGHT, s.splitRule)
        assertEquals(10000, s.maxStripHeight)
        assertTrue(s.smartCut)
        assertEquals(Strictness.STRICT, s.strictness)
        assertEquals(ThemeMode.SYSTEM, s.themeMode)
        assertEquals(FitMode.FIT_WIDTH, s.fitMode)
    }
}
