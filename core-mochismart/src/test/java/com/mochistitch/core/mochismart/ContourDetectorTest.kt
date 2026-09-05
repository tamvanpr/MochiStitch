package com.mochistitch.core.mochismart

import org.junit.Assert.assertTrue
import org.junit.Test

class ContourDetectorTest {
    @Test
    fun testDetectContours() {
        assertTrue(ContourDetector.detectContours())
    }
}
