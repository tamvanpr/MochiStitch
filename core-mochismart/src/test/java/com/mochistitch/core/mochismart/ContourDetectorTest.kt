package com.mochistitch.core.mochismart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContourDetectorTest {

    @Test
    fun testInitOpenCVDoesNotCrash() {
        val initialized = ContourDetector.initOpenCV()
        // Should return boolean safely without throwing unsatisfied link error
        assertTrue(initialized || !initialized)
    }

    @Test
    fun testFindSafeSplitPointNoCollision() {
        val totalLength = 1000
        val candidate = 500
        val tolerance = 150
        val boundingBoxes = listOf(BoundingBox(0, 100, 200, 300))

        val result = ContourDetector.findSafeSplitPoint(
            totalLength = totalLength,
            candidate = candidate,
            tolerance = tolerance,
            isVertical = true,
            boundingBoxes = boundingBoxes
        )

        assertEquals(500, result.splitPosition)
        assertFalse(result.needsManualReview)
    }

    @Test
    fun testFindSafeSplitPointShiftToSafeGap() {
        val totalLength = 1000
        val candidate = 500
        val tolerance = 150
        // Bubble covers Y=450 to Y=550
        val boundingBoxes = listOf(BoundingBox(0, 450, 200, 550))

        val result = ContourDetector.findSafeSplitPoint(
            totalLength = totalLength,
            candidate = candidate,
            tolerance = tolerance,
            isVertical = true,
            boundingBoxes = boundingBoxes
        )

        // Safe gaps exist at 449 or 551. Nearest to 500 is 449 (dist 51) or 551 (dist 51).
        assertTrue(result.splitPosition == 449 || result.splitPosition == 551)
        assertFalse(result.needsManualReview)
    }

    @Test
    fun testFindSafeSplitPointFallbackWhenNoSafeGap() {
        val totalLength = 1000
        val candidate = 500
        val tolerance = 100
        // Bubble covers Y=300 to Y=700 (entire tolerance range 400..600)
        val boundingBoxes = listOf(BoundingBox(0, 300, 200, 700))

        val result = ContourDetector.findSafeSplitPoint(
            totalLength = totalLength,
            candidate = candidate,
            tolerance = tolerance,
            isVertical = true,
            boundingBoxes = boundingBoxes
        )

        // Fallback to original candidate position
        assertEquals(500, result.splitPosition)
        assertTrue(result.needsManualReview)
    }
}
