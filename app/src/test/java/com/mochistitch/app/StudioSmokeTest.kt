package com.mochistitch.app

import org.junit.Assert.assertEquals
import org.junit.Test

class StudioSmokeTest {
    @Test
    fun testScreensExist() {
        assertEquals(4, StudioScreen.entries.size)
    }

    @Test
    fun testScreenOrder() {
        assertEquals(
            listOf("INPUT", "SETUP", "RESULT", "QUEUE"),
            StudioScreen.entries.map { it.name }
        )
    }
}
