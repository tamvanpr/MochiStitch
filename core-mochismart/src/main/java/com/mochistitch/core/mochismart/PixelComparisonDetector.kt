package com.mochistitch.core.mochismart

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

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
     * Finds the nearest safe Y coordinate to cut, searching upwards within a search window from the target distance.
     *
     * @param bitmap Source bitmap
     * @param startY Starting Y position of the current slice
     * @param maxDistance Maximum target slice height
     * @param sensitivity Sensitivity factor between 0.0f and 1.0f
     * @param margins Number of pixels from left/right edges to ignore
     * @param step Step size in pixels for checking rows and fallback alignment
     * @param maxSearchDeviationFactor Search window factor (e.g. 0.2f means search up to 20% of maxDistance upwards)
     * @return Ideal safe Y coordinate for cutting
     */
    fun findSafeCutPoint(
        bitmap: Bitmap,
        startY: Int,
        maxDistance: Int,
        sensitivity: Float = 0.5f,
        margins: Int = 0,
        step: Int = 5,
        maxSearchDeviationFactor: Float = 0.2f
    ): Int {
        val totalHeight = bitmap.height
        val idealTargetY = min(startY + maxDistance, totalHeight)

        if (idealTargetY >= totalHeight) {
            return totalHeight
        }

        val effectiveStep = max(1, step)
        val searchWindow = (maxDistance * maxSearchDeviationFactor.coerceIn(0.0f, 1.0f)).toInt()
        val minSearchY = max(startY + 1, idealTargetY - searchWindow)

        val rowBuffer = IntArray(bitmap.width)

        var candidateY = idealTargetY
        while (candidateY >= minSearchY) {
            if (canSliceRow(bitmap, candidateY, sensitivity, margins, rowBuffer)) {
                return candidateY
            }
            candidateY -= effectiveStep
        }

        // Fallback: If no safe row was found within the window, align idealTargetY to divisor step
        val aligned = alignToDivisor(idealTargetY, effectiveStep)
        return aligned.coerceIn(startY + 1, totalHeight)
    }
}
