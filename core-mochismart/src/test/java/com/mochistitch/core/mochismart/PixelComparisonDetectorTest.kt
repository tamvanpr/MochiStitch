package com.mochistitch.core.mochismart

import org.junit.Assert.assertEquals
import org.junit.Test

class PixelComparisonDetectorTest {

    @Test
    fun testCalculateLuminance() {
        // Pure White: (255, 255, 255) -> 255.0
        val whitePixel = (0xFF shl 24) or (0xFF shl 16) or (0xFF shl 8) or 0xFF
        val whiteLum = PixelComparisonDetector.calculateLuminance(whitePixel)
        assertEquals(255.0f, whiteLum, 0.01f)

        // Pure Black: (0, 0, 0) -> 0.0
        val blackPixel = (0xFF shl 24) or (0 shl 16) or (0 shl 8) or 0
        val blackLum = PixelComparisonDetector.calculateLuminance(blackPixel)
        assertEquals(0.0f, blackLum, 0.01f)

        // Pure Red: (255, 0, 0) -> 0.299 * 255 = 76.245
        val redPixel = (0xFF shl 24) or (255 shl 16) or (0 shl 8) or 0
        val redLum = PixelComparisonDetector.calculateLuminance(redPixel)
        assertEquals(76.245f, redLum, 0.01f)
    }

    @Test
    fun testAlignToDivisor() {
        assertEquals(100, PixelComparisonDetector.alignToDivisor(104, 5))
        assertEquals(105, PixelComparisonDetector.alignToDivisor(105, 5))
        assertEquals(510, PixelComparisonDetector.alignToDivisor(512, 10))
        assertEquals(100, PixelComparisonDetector.alignToDivisor(100, 0))
    }
}
