package com.mochistitch.core.settings

import org.junit.Assert.assertFalse
import org.junit.Test

class AppSettingsTest {
    @Test
    fun testIsDarkModeEnabled() {
        assertFalse(AppSettings.isDarkModeEnabled())
    }
}
