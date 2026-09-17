package com.mochistitch.core.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class StudioThemeTest {
    @Test
    fun testTealPalette() {
        assertEquals(Color(0xFF0E7C6B), StudioLightScheme.primary)
        assertEquals(Color(0xFF7FD8C7), StudioDarkScheme.primary)
    }
}
