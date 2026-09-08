package com.mochistitch.core.imaging

import android.graphics.Bitmap
import com.mochistitch.core.mochismart.ContourDetector
import com.mochistitch.core.mochismart.SmartSplitResult
import com.mochistitch.core.settings.DetectionSensitivity
import com.mochistitch.core.settings.MochiStitchSettings
import com.mochistitch.core.settings.ReadingDirection
import com.mochistitch.core.settings.SplitMode
import kotlin.math.min

data class SlicedPiece(
    val bitmap: Bitmap,
    val needsManualReview: Boolean = false
)

object SplitEngine {

    fun sliceBitmapDetailed(
        source: Bitmap,
        splitMode: SplitMode,
        maxPixelLength: Int,
        direction: ReadingDirection,
        mochiSmartEnabled: Boolean = false,
        tolerance: Int = 150,
        sensitivity: DetectionSensitivity = DetectionSensitivity.MEDIUM,
        maxPagesPerFile: Int = 1
    ): List<SlicedPiece> {
        if (splitMode == SplitMode.NO_LIMIT || (splitMode != SplitMode.MAX_PIXELS && splitMode != SplitMode.PAGES_PER_FILE) || maxPixelLength <= 0) {
            return listOf(SlicedPiece(source, needsManualReview = false))
        }

        val isVertical = direction == ReadingDirection.VERTICAL
        val totalLength = if (isVertical) source.height else source.width

        // For PAGES_PER_FILE mode, treat each "page" as maxPagesPerFile
        // We'll split by maxPixelLength but limit to maxPagesPerFile pieces per file
        if (splitMode == SplitMode.PAGES_PER_FILE && maxPagesPerFile > 0) {
            return sliceWithPageLimit(source, maxPixelLength, direction, maxPagesPerFile)
        }

        if (totalLength <= maxPixelLength) {
            return listOf(SlicedPiece(source, needsManualReview = false))
        }

        val boundingBoxes = if (mochiSmartEnabled) {
            ContourDetector.detectBoundingBoxes(source, sensitivity)
        } else {
            emptyList()
        }

        val slices = mutableListOf<SlicedPiece>()

        if (isVertical) {
            var currentY = 0
            while (currentY < totalLength) {
                val remaining = totalLength - currentY
                if (remaining <= maxPixelLength) {
                    val slice = Bitmap.createBitmap(source, 0, currentY, source.width, remaining)
                    slices.add(SlicedPiece(slice, needsManualReview = false))
                    break
                }

                val candidate = currentY + maxPixelLength
                val clampedCandidate = min(candidate, totalLength - 1)
                val (splitPos, needsReview) = if (mochiSmartEnabled) {
                    ContourDetector.findSafeSplitPoint(
                        totalLength = totalLength,
                        candidate = clampedCandidate,
                        tolerance = tolerance,
                        isVertical = true,
                        boundingBoxes = boundingBoxes,
                        bitmap = source
                    )
                } else SmartSplitResult(clampedCandidate, false)
                // Ensure splitPos advances at least 1px and stays within bounds
                val effectiveSplitPos = splitPos.coerceIn(currentY + 1, totalLength - 1)
                val sliceHeight = (effectiveSplitPos - currentY).coerceIn(1, remaining)
                val slice = Bitmap.createBitmap(source, 0, currentY, source.width, sliceHeight)
                slices.add(SlicedPiece(slice, needsReview))
                currentY += sliceHeight
            }
        } else {
            var currentX = 0
            while (currentX < totalLength) {
                val remaining = totalLength - currentX
                if (remaining <= maxPixelLength) {
                    val slice = Bitmap.createBitmap(source, currentX, 0, remaining, source.height)
                    slices.add(SlicedPiece(slice, needsManualReview = false))
                    break
                }

                val candidate = currentX + maxPixelLength
                val clampedCandidate = min(candidate, totalLength - 1)
                val (splitPos, needsReview) = if (mochiSmartEnabled) {
                    ContourDetector.findSafeSplitPoint(
                        totalLength = totalLength,
                        candidate = clampedCandidate,
                        tolerance = tolerance,
                        isVertical = false,
                        boundingBoxes = boundingBoxes,
                        bitmap = source
                    )
                } else SmartSplitResult(clampedCandidate, false)
                // Ensure splitPos advances at least 1px and stays within bounds
                val effectiveSplitPos = splitPos.coerceIn(currentX + 1, totalLength - 1)
                val sliceWidth = (effectiveSplitPos - currentX).coerceIn(1, remaining)
                val slice = Bitmap.createBitmap(source, currentX, 0, sliceWidth, source.height)
                slices.add(SlicedPiece(slice, needsReview))
                currentX += sliceWidth
            }
        }

        return slices
    }

    /**
     * Split with page limit - creates multiple output files with maxPagesPerFile pieces each
     */
    private fun sliceWithPageLimit(
        source: Bitmap,
        maxPixelLength: Int,
        direction: ReadingDirection,
        maxPagesPerFile: Int
    ): List<SlicedPiece> {
        val isVertical = direction == ReadingDirection.VERTICAL
        val totalLength = if (isVertical) source.height else source.width

        if (totalLength <= maxPixelLength) {
            return listOf(SlicedPiece(source, needsManualReview = false))
        }

        val slices = mutableListOf<SlicedPiece>()
        val sliceHeight = if (isVertical) maxPixelLength else source.height
        val sliceWidth = if (!isVertical) maxPixelLength else source.width

        var currentY = 0
        var currentX = 0
        var pageCount = 0

        while (if (isVertical) currentY < totalLength else currentX < totalLength) {
            val remaining = totalLength - if (isVertical) currentY else currentX
            val currentSliceLength = min(remaining, maxPixelLength)

            val slice = if (isVertical) {
                Bitmap.createBitmap(source, 0, currentY, source.width, currentSliceLength)
            } else {
                Bitmap.createBitmap(source, currentX, 0, currentSliceLength, source.height)
            }

            slices.add(SlicedPiece(slice, needsManualReview = false))
            pageCount++

            if (isVertical) {
                currentY += currentSliceLength
            } else {
                currentX += currentSliceLength
            }

            // Reset page count if we've reached maxPagesPerFile
            // (This would create a new "file" in the actual export)
            if (pageCount >= maxPagesPerFile) {
                pageCount = 0
            }
        }

        return slices
    }

    fun sliceBitmapDetailed(
        source: Bitmap,
        settings: MochiStitchSettings
    ): List<SlicedPiece> {
        return sliceBitmapDetailed(
            source = source,
            splitMode = settings.splitMode,
            maxPixelLength = settings.maxPixelLength,
            direction = settings.readingDirection,
            mochiSmartEnabled = settings.mochiSmartEnabled,
            tolerance = settings.mochiSmartTolerance,
            sensitivity = settings.mochiSmartSensitivity,
            maxPagesPerFile = settings.maxPagesPerFile
        )
    }

    fun sliceBitmap(
        source: Bitmap,
        splitMode: SplitMode,
        maxPixelLength: Int,
        direction: ReadingDirection
    ): List<Bitmap> {
        return sliceBitmapDetailed(
            source = source,
            splitMode = splitMode,
            maxPixelLength = maxPixelLength,
            direction = direction,
            mochiSmartEnabled = false
        ).map { it.bitmap }
    }
}
