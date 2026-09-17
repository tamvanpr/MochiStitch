package com.mochistitch.core.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class StitchSettingsTest {
    @Test
    fun testFreshDefaults() {
        val s = StitchSettings()
        assertEquals(ImageFormat.JPG, s.imageFormat)
        assertEquals(PackFormat.ZIP, s.packFormat)
        assertEquals(SplitRule.MAX_HEIGHT, s.splitRule)
        assertEquals(10000, s.maxStripHeight)
        assertEquals(ThemeMode.SYSTEM, s.themeMode)
    }
}
