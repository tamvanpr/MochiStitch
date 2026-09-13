package com.mochistitch.core.imaging

import com.mochistitch.core.mochismart.BoundingBox
import kotlin.math.abs

object PageBoundarySnapping {

    /**
     * Finds a page boundary that lies within [tolerance] of [targetPos].
     *
     * If a boundary is within tolerance AND no protected bounding box spans across that boundary,
     * returns the boundary position (snap position).
     *
     * Otherwise (boundary out of tolerance OR a protected box overlaps across boundary), returns null.
     */
    fun findSnapBoundary(
        targetPos: Int,
        tolerance: Int,
        pageBoundaries: List<Int>,
        protectedBoxes: List<BoundingBox>,
        isVertical: Boolean
    ): Int? {
        if (pageBoundaries.isEmpty()) return null

        // Find candidate boundaries within tolerance
        val candidates = pageBoundaries.filter { boundary ->
            abs(boundary - targetPos) <= tolerance
        }

        if (candidates.isEmpty()) return null

        // Pick the candidate boundary closest to targetPos
        val bestBoundary = candidates.minByOrNull { abs(it - targetPos) } ?: return null

        // Check if any protected bounding box overlaps across bestBoundary
        val hasOverlap = protectedBoxes.any { box ->
            if (isVertical) {
                box.top < bestBoundary && box.bottom > bestBoundary
            } else {
                box.left < bestBoundary && box.right > bestBoundary
            }
        }

        return if (hasOverlap) null else bestBoundary
    }
}
