package com.mochistitch.core.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class MochiThemeTest {
    @Test
    fun testThemeColorSchemePrimaryValues() {
        assertEquals(Color(0xFF2E6B40), LightMochiStitchColorScheme.primary)
        assertEquals(Color(0xFF96D6A2), DarkMochiStitchColorScheme.primary)
    }
}
