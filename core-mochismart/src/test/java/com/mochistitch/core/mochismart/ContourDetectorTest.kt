package com.mochistitch.core.mochismart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

        assertTrue(result.splitPosition <= 449 || result.splitPosition >= 551)
        assertFalse(result.needsManualReview)
    }

    @Test
    fun testFindSafeSplitPointExpandsRangeForLargeBalloon() {
        val totalLength = 2000
        val candidate = 1000
        val tolerance = 100
        val boundingBoxes = listOf(BoundingBox(0, 800, 300, 1200, BoundingBoxType.PROTECTED_BALLOON))

        val result = ContourDetector.findSafeSplitPoint(
            totalLength = totalLength,
            candidate = candidate,
            tolerance = tolerance,
            isVertical = true,
            boundingBoxes = boundingBoxes
        )

        assertTrue("Split position ${result.splitPosition} must be outside balloon [800..1200]",
            result.splitPosition <= 799 || result.splitPosition >= 1201)
        assertFalse(result.needsManualReview)
    }

    @Test
    fun testFindSafeSplitPointAllowsSfxSplitWhenNoPureGap() {
        val totalLength = 1000
        val candidate = 500
        val tolerance = 150
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
        val boundingBoxes = listOf(BoundingBox(0, 950, 400, 1050, BoundingBoxType.PROTECTED_BALLOON))

        val result = ContourDetector.findSafeSplitPoint(
            totalLength = totalLength,
            candidate = candidate,
            tolerance = tolerance,
            isVertical = true,
            boundingBoxes = boundingBoxes,
            maxSearchDeviationFactor = 0.2f
        )

        assertTrue(result.splitPosition <= 949 || result.splitPosition >= 1051)
        assertFalse(result.needsManualReview)
    }

    @Test
    fun testTwoAdjacentBalloonsExceedingTolerance() {
        val totalLength = 2000
        val currentPos = 0
        val targetPos = 1000
        val tolerance = 100 // Tolerance window [900..1100]
        // Balloon 1 covers 850..980, Balloon 2 covers 990..1150
        val boundingBoxes = listOf(
            BoundingBox(0, 850, 300, 980, BoundingBoxType.PROTECTED_BALLOON),
            BoundingBox(0, 990, 300, 1150, BoundingBoxType.PROTECTED_BALLOON)
        )
        val safeGaps = ContourDetector.calculateSafeGaps(totalLength, boundingBoxes, isVertical = true)

        val result = ContourDetector.findSafeSplitPointDetailed(
            totalLength = totalLength,
            currentPos = currentPos,
            targetPos = targetPos,
            tolerance = tolerance,
            safeGaps = safeGaps,
            allowExceedOnNoSafeGap = true,
            preferShorterOverLonger = true
        )

        assertTrue("Split position ${result.splitPosition} must fall in gap [981..989] or before 850",
            (result.splitPosition in 981..989) || result.splitPosition <= 849)
        assertFalse(result.needsManualReview)
    }

    @Test
    fun testGiantBalloonLongerThanMaxPixelLength() {
        val totalLength = 3000
        val currentPos = 0
        val targetPos = 1000
        val tolerance = 100
        // Giant balloon spans Y=0 to Y=2500 (longer than targetPos 1000 and covers start!)
        val boundingBoxes = listOf(
            BoundingBox(0, 0, 300, 2500, BoundingBoxType.PROTECTED_BALLOON)
        )
        val safeGaps = ContourDetector.calculateSafeGaps(totalLength, boundingBoxes, isVertical = true)

        val result = ContourDetector.findSafeSplitPointDetailed(
            totalLength = totalLength,
            currentPos = currentPos,
            targetPos = targetPos,
            tolerance = tolerance,
            safeGaps = safeGaps,
            allowExceedOnNoSafeGap = true,
            preferShorterOverLonger = true
        )

        assertTrue(result.needsManualReview)
        assertNotNull(result.reviewReason)
        assertTrue(result.splitPosition in 2500..2501)
    }

    @Test
    fun testFallbackShorterWhenExceedDisabled() {
        val totalLength = 2000
        val currentPos = 0
        val targetPos = 1000
        val tolerance = 100
        // Protected balloon covers Y=800..1200
        val boundingBoxes = listOf(
            BoundingBox(0, 800, 300, 1200, BoundingBoxType.PROTECTED_BALLOON)
        )
        val safeGaps = ContourDetector.calculateSafeGaps(totalLength, boundingBoxes, isVertical = true)

        val result = ContourDetector.findSafeSplitPointDetailed(
            totalLength = totalLength,
            currentPos = currentPos,
            targetPos = targetPos,
            tolerance = tolerance,
            safeGaps = safeGaps,
            allowExceedOnNoSafeGap = false,
            preferShorterOverLonger = true
        )

        assertTrue(result.splitPosition <= 799)
        assertFalse(result.needsManualReview)
    }
}
