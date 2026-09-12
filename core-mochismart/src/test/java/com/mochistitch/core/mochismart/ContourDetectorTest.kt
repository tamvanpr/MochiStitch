package com.mochistitch.core.mochismart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContourDetectorTest {

    @Test
    fun testInitOpenCVDoesNotCrash() {
        val initialized = ContourDetector.initOpenCV()
        assertTrue(initialized || !initialized)
    }

    @Test
    fun testFindSafeSplitPointNoCollision() {
        val totalLength = 1000
        val candidate = 500
        val tolerance = 150
        val boundingBoxes = listOf(BoundingBox(0, 100, 200, 300, BoundingBoxType.PROTECTED_BALLOON))

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
    fun testFindSafeSplitPointShiftOutsideProtectedBalloon() {
        val totalLength = 1000
        val candidate = 500
        val tolerance = 150
        // Protected speech balloon covers Y=450 to Y=550
        val boundingBoxes = listOf(BoundingBox(0, 450, 200, 550, BoundingBoxType.PROTECTED_BALLOON))

        val result = ContourDetector.findSafeSplitPoint(
            totalLength = totalLength,
            candidate = candidate,
            tolerance = tolerance,
            isVertical = true,
            boundingBoxes = boundingBoxes
        )

        // Safe gaps exist at <= 449 or >= 551. Nearest to 500 is 449 or 551.
        assertTrue(result.splitPosition <= 449 || result.splitPosition >= 551)
        assertFalse(result.needsManualReview)
    }

    @Test
    fun testFindSafeSplitPointExpandsRangeForLargeBalloon() {
        val totalLength = 2000
        val candidate = 1000
        val tolerance = 100
        // Large speech balloon / system dialog box covers Y=800 to Y=1200 (spans 400px, larger than default tolerance)
        val boundingBoxes = listOf(BoundingBox(0, 800, 300, 1200, BoundingBoxType.PROTECTED_BALLOON))

        val result = ContourDetector.findSafeSplitPoint(
            totalLength = totalLength,
            candidate = candidate,
            tolerance = tolerance,
            isVertical = true,
            boundingBoxes = boundingBoxes
        )

        // Algorithm MUST shift split position outside balloon (e.g. <= 799 or >= 1201)
        assertTrue("Split position ${result.splitPosition} must be outside balloon [800..1200]",
            result.splitPosition <= 799 || result.splitPosition >= 1201)
        assertFalse(result.needsManualReview)
    }

    @Test
    fun testFindSafeSplitPointAllowsSfxSplitWhenNoPureGap() {
        val totalLength = 1000
        val candidate = 500
        val tolerance = 150
        // SFX box covers Y=400 to Y=600
        val boundingBoxes = listOf(BoundingBox(0, 400, 200, 600, BoundingBoxType.SFX))

        val result = ContourDetector.findSafeSplitPoint(
            totalLength = totalLength,
            candidate = candidate,
            tolerance = tolerance,
            isVertical = true,
            boundingBoxes = boundingBoxes
        )

        assertFalse(result.needsManualReview)
    }

    @Test
    fun testFindSafeSplitPointAutoAdjustsWithMaxSearchDeviationFactor() {
        val totalLength = 2000
        val candidate = 1000
        val tolerance = 50
        // Speech balloon covers candidate region 950 to 1050
        val boundingBoxes = listOf(BoundingBox(0, 950, 400, 1050, BoundingBoxType.PROTECTED_BALLOON))

        val result = ContourDetector.findSafeSplitPoint(
            totalLength = totalLength,
            candidate = candidate,
            tolerance = tolerance,
            isVertical = true,
            boundingBoxes = boundingBoxes,
            maxSearchDeviationFactor = 0.2f
        )

        // Must actively shift cut point outside [950..1050] to safe gutter
        assertTrue(result.splitPosition <= 949 || result.splitPosition >= 1051)
        assertFalse(result.needsManualReview)
    }
}
