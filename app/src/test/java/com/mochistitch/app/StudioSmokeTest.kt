package com.mochistitch.app

import org.junit.Assert.assertEquals
import org.junit.Test

class StudioSmokeTest {
    @Test
    fun testScreensExist() {
        assertEquals(5, StudioScreen.entries.size)
    }
}
