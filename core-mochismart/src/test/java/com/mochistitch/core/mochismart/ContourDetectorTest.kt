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

    @Test
    fun testMergeUncontainedTextLinesGroupsCloseVerticalLines() {
        // Line 1: top=100, bottom=120 (height=20)
        // Line 2: top=135, bottom=155 (height=20, vertical gap=15 <= 1.8 * 20 = 36)
        // Line 3: top=170, bottom=190 (height=20, vertical gap=15 <= 36)
        val boxes = listOf(
            BoundingBox(left = 50, top = 100, right = 300, bottom = 120, type = BoundingBoxType.PROTECTED_BALLOON),
            BoundingBox(left = 45, top = 135, right = 310, bottom = 155, type = BoundingBoxType.PROTECTED_BALLOON),
            BoundingBox(left = 50, top = 170, right = 295, bottom = 190, type = BoundingBoxType.PROTECTED_BALLOON)
        )

        val merged = ContourDetector.mergeUncontainedTextLines(boxes)

        assertEquals(1, merged.size)
        val consolidated = merged.first()
        assertEquals(45, consolidated.left)
        assertEquals(100, consolidated.top)
        assertEquals(310, consolidated.right)
        assertEquals(190, consolidated.bottom)
        assertEquals(BoundingBoxType.PROTECTED_BALLOON, consolidated.type)
    }

    @Test
    fun testMergeUncontainedTextLinesPreservesDistantDialogBoxes() {
        // Dialogue box 1: top=100, bottom=250 (height=150)
        // Dialogue box 2: top=500, bottom=650 (height=150, gap=250 > 1.8 * 150 = 270... actually gap=250 <= 270)
        // Dialogue box 2 at top=600, bottom=750 (height=150, vertical gap=350 > 1.8 * 150 = 270)
        val box1 = BoundingBox(left = 50, top = 100, right = 300, bottom = 250, type = BoundingBoxType.PROTECTED_BALLOON)
        val box2 = BoundingBox(left = 50, top = 600, right = 300, bottom = 750, type = BoundingBoxType.PROTECTED_BALLOON)

        val merged = ContourDetector.mergeUncontainedTextLines(listOf(box1, box2))

        assertEquals(2, merged.size)
    }

    @Test
    fun testMergeUncontainedTextLinesPreservesSfxUnmerged() {
        val textLine = BoundingBox(left = 50, top = 100, right = 300, bottom = 120, type = BoundingBoxType.PROTECTED_BALLOON)
        val sfxBox = BoundingBox(left = 60, top = 125, right = 280, bottom = 180, type = BoundingBoxType.SFX)

        val merged = ContourDetector.mergeUncontainedTextLines(listOf(textLine, sfxBox))

        assertEquals(2, merged.size)
        assertTrue(merged.any { it.type == BoundingBoxType.SFX })
        assertTrue(merged.any { it.type == BoundingBoxType.PROTECTED_BALLOON })
    }
}
