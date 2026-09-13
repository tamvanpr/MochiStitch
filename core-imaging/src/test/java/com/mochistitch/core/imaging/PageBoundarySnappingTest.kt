package com.mochistitch.core.imaging

import com.mochistitch.core.mochismart.BoundingBox
import com.mochistitch.core.mochismart.BoundingBoxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageBoundarySnappingTest {

    @Test
    fun testBoundaryInTolerance_NoOverlap_SnapOccurs() {
        val targetPos = 2000
        val tolerance = 300
        val pageBoundaries = listOf(1000, 1900, 3000)
        val protectedBoxes = listOf(
            BoundingBox(left = 0, top = 500, right = 1000, bottom = 800, type = BoundingBoxType.PROTECTED_BALLOON),
            BoundingBox(left = 0, top = 2100, right = 1000, bottom = 2300, type = BoundingBoxType.PROTECTED_BALLOON)
        )

        val snapResult = PageBoundarySnapping.findSnapBoundary(
            targetPos = targetPos,
            tolerance = tolerance,
            pageBoundaries = pageBoundaries,
            protectedBoxes = protectedBoxes,
            isVertical = true
        )

        assertEquals(1900, snapResult)
    }

    @Test
    fun testBoundaryOutsideTolerance_SnapCancelled() {
        val targetPos = 2000
        val tolerance = 300
        val pageBoundaries = listOf(1000, 1600, 2500)
        val protectedBoxes = emptyList<BoundingBox>()

        val snapResult = PageBoundarySnapping.findSnapBoundary(
            targetPos = targetPos,
            tolerance = tolerance,
            pageBoundaries = pageBoundaries,
            protectedBoxes = protectedBoxes,
            isVertical = true
        )

        assertNull(snapResult)
    }

    @Test
    fun testBoundaryInTolerance_WithProtectedOverlap_SnapCancelled() {
        val targetPos = 2000
        val tolerance = 300
        val pageBoundaries = listOf(1900)
        val protectedBoxes = listOf(
            // Box spans from 1850 to 1950, crossing the boundary 1900
            BoundingBox(left = 0, top = 1850, right = 1000, bottom = 1950, type = BoundingBoxType.PROTECTED_BALLOON)
        )

        val snapResult = PageBoundarySnapping.findSnapBoundary(
            targetPos = targetPos,
            tolerance = tolerance,
            pageBoundaries = pageBoundaries,
            protectedBoxes = protectedBoxes,
            isVertical = true
        )

        assertNull(snapResult)
    }

    @Test
    fun testBoundaryAtExactToleranceEdge_SnapOccurs() {
        val targetPos = 2000
        val tolerance = 300
        val pageBoundaries = listOf(1700) // Exactly 300 distance (2000 - 1700 = 300)
        val protectedBoxes = emptyList<BoundingBox>()

        val snapResult = PageBoundarySnapping.findSnapBoundary(
            targetPos = targetPos,
            tolerance = tolerance,
            pageBoundaries = pageBoundaries,
            protectedBoxes = protectedBoxes,
            isVertical = true
        )

        assertEquals(1700, snapResult)
    }
}
