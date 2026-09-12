package com.mochistitch.core.mochismart

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class BoundingBox(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val isProtected: Boolean = true
)

object PixelComparisonDetector {

    /**
     * Checks whether a specific horizontal row in the bitmap can be safely cut.
     *
     * @param bitmap The source image bitmap
     * @param rowIndex The Y coordinate of the horizontal row to analyze
     * @param sensitivity Detection sensitivity from 0.0f (least sensitive) to 1.0f (most sensitive). Threshold = 255 * (1 - sensitivity)
     * @param margins Number of pixels from the left and right edges to ignore during evaluation
     * @param rowPixels Optional reusable IntArray buffer of size >= bitmap.width to avoid memory allocation
     * @return true if the row contains no horizontal pixel differences exceeding the threshold (safe to cut)
     */
    fun canSliceRow(
        bitmap: Bitmap,
        rowIndex: Int,
        sensitivity: Float = 0.5f,
        margins: Int = 0,
        rowPixels: IntArray? = null
    ): Boolean {
        val width = bitmap.width
        val height = bitmap.height

        if (rowIndex < 0 || rowIndex >= height || width <= 0) {
            return false
        }

        val clampedSensitivity = sensitivity.coerceIn(0.0f, 1.0f)
        val threshold = 255.0f * (1.0f - clampedSensitivity)

        val leftMargin = margins.coerceIn(0, max(0, (width - 2) / 2))
        val rightMargin = margins.coerceIn(0, max(0, (width - 2) / 2))
        val startX = leftMargin
        val endX = width - rightMargin

        if (endX - startX < 2) {
            return true
        }

        val pixels = if (rowPixels != null && rowPixels.size >= width) {
            rowPixels
        } else {
            IntArray(width)
        }

        bitmap.getPixels(pixels, 0, width, 0, rowIndex, width, 1)

        var prevGray = calculateLuminance(pixels[startX])

        for (x in (startX + 1) until endX) {
            val currGray = calculateLuminance(pixels[x])
            val diff = abs(currGray - prevGray)
            if (diff > threshold) {
                return false
            }
            prevGray = currGray
        }

        return true
    }

    /**
     * Calculates the pixel luminance variance / noise score for a horizontal row.
     * Lower scores indicate cleaner gutter areas with fewer details or edges.
     */
    fun calculateRowPixelVariance(
        bitmap: Bitmap,
        rowIndex: Int,
        margins: Int = 0,
        rowPixels: IntArray? = null
    ): Float {
        val width = bitmap.width
        val height = bitmap.height

        if (rowIndex < 0 || rowIndex >= height || width <= 0) {
            return Float.MAX_VALUE
        }

        val leftMargin = margins.coerceIn(0, max(0, (width - 2) / 2))
        val rightMargin = margins.coerceIn(0, max(0, (width - 2) / 2))
        val startX = leftMargin
        val endX = width - rightMargin

        if (endX - startX < 2) {
            return 0.0f
        }

        val pixels = if (rowPixels != null && rowPixels.size >= width) {
            rowPixels
        } else {
            IntArray(width)
        }

        bitmap.getPixels(pixels, 0, width, 0, rowIndex, width, 1)

        var totalDiff = 0.0f
        var maxDiff = 0.0f
        var prevGray = calculateLuminance(pixels[startX])

        for (x in (startX + 1) until endX) {
            val currGray = calculateLuminance(pixels[x])
            val diff = abs(currGray - prevGray)
            totalDiff += diff
            if (diff > maxDiff) {
                maxDiff = diff
            }
            prevGray = currGray
        }

        val count = endX - startX - 1
        val avgDiff = if (count > 0) totalDiff / count else 0.0f

        return maxDiff * 10.0f + avgDiff
    }

    /**
     * Calculates luminance using standard ITU-R BT.601 formula:
     * Gray = 0.299 * R + 0.587 * G + 0.114 * B
     */
    fun calculateLuminance(pixel: Int): Float {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return 0.299f * r + 0.587f * g + 0.114f * b
    }

    /**
     * Aligns a coordinate to a given divisor step.
     */
    fun alignToDivisor(value: Int, divisor: Int): Int {
        if (divisor <= 0) return value
        return (value / divisor) * divisor
    }

    /**
     * Finds the nearest safe Y coordinate to cut, searching bi-directionally (both upwards and downwards)
     * radiating outwards from idealTargetY within a search window ±(maxDistance * maxSearchDeviationFactor).
     *
     * If no 100% clean row is found within the window, selects the candidate row with minimal pixel variance.
     *
     * @param bitmap Source bitmap
     * @param startY Starting Y position of the current slice
     * @param maxDistance Maximum target slice height
     * @param sensitivity Sensitivity factor between 0.0f and 1.0f
     * @param margins Number of pixels from left/right edges to ignore
     * @param step Step size in pixels for checking rows
     * @param maxSearchDeviationFactor Search window factor (e.g. 0.2f means search ±20% of maxDistance)
     * @return Ideal safe Y coordinate for cutting
     */
    fun findSafeCutPoint(
        bitmap: Bitmap,
        startY: Int,
        maxDistance: Int,
        sensitivity: Float = 0.5f,
        margins: Int = 0,
        step: Int = 5,
        maxSearchDeviationFactor: Float = 0.2f,
        protectedBoundingBoxes: List<BoundingBox> = emptyList()
    ): Int {
        val totalHeight = bitmap.height
        val idealTargetY = min(startY + maxDistance, totalHeight)

        if (idealTargetY >= totalHeight) {
            return totalHeight
        }

        val effectiveStep = max(1, step)
        val searchWindow = (maxDistance * maxSearchDeviationFactor.coerceIn(0.0f, 1.0f)).toInt()
        val minY = max(startY + 1, idealTargetY - searchWindow)
        val maxY = min(totalHeight - 1, idealTargetY + searchWindow)

        if (minY > maxY) {
            return idealTargetY.coerceIn(startY + 1, totalHeight)
        }

        val protectedBoxes = protectedBoundingBoxes.filter { it.isProtected }

        fun isRowProtected(y: Int): Boolean {
            return protectedBoxes.any { box ->
                y in box.top..box.bottom
            }
        }

        fun isRowClean(y: Int): Boolean {
            if (isRowProtected(y)) return false
            return canSliceRow(bitmap, y, sensitivity, margins, rowBuffer = rowBuffer)
        }

        val rowBuffer = IntArray(bitmap.width)
        val maxOffsetSteps = max((idealTargetY - minY) / effectiveStep, (maxY - idealTargetY) / effectiveStep) + 1

        // 1. Bi-directional search radiating outwards from idealTargetY
        for (k in 0..maxOffsetSteps) {
            val delta = k * effectiveStep

            // Prefer shorter slice (upwards) first when distances tie
            val upY = idealTargetY - delta
            if (upY in minY..maxY) {
                if (isRowClean(upY)) {
                    return upY
                }
            }

            if (delta > 0) {
                val downY = idealTargetY + delta
                if (downY in minY..maxY) {
                    if (isRowClean(downY)) {
                        return downY
                    }
                }
            }
        }

        // 2. Fallback: Select candidate row within [minY..maxY] with minimal pixel variance/density,
        //    strictly avoiding protected rows.
        var bestY = idealTargetY
        var minVariance = Float.MAX_VALUE
        var minDistance = Int.MAX_VALUE
        var bestIsProtected = true

        var y = minY
        while (y <= maxY) {
            val isProtected = isRowProtected(y)
            val variance = if (isProtected) {
                Float.MAX_VALUE
            } else {
                calculateRowPixelVariance(bitmap, y, margins, rowBuffer)
            }
            val dist = abs(y - idealTargetY)

            val isBetter = when {
                variance < minVariance - 0.001f -> true
                abs(variance - minVariance) <= 0.001f -> {
                    if (dist < minDistance) true
                    else if (dist == minDistance && y < bestY) true
                    else false
                }
                else -> false
            }

            if (isBetter) {
                minVariance = variance
                minDistance = dist
                bestY = y
                bestIsProtected = isProtected
            }

            y += effectiveStep
        }

        if (bestIsProtected) {
            return idealTargetY.coerceIn(startY + 1, totalHeight)
        }

        return bestY.coerceIn(startY + 1, totalHeight)
    }
}
