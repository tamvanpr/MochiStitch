package com.mochistitch.core.imaging

import android.graphics.Bitmap
import com.mochistitch.core.mochismart.ContourDetector
import com.mochistitch.core.mochismart.SmartSplitResult
import com.mochistitch.core.settings.DetectionSensitivity
import com.mochistitch.core.settings.MochiStitchSettings
import com.mochistitch.core.settings.ReadingDirection
import com.mochistitch.core.settings.SplitMode

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
        sensitivity: DetectionSensitivity = DetectionSensitivity.MEDIUM
    ): List<SlicedPiece> {
        if (splitMode != SplitMode.MAX_PIXELS || maxPixelLength <= 0) {
            return listOf(SlicedPiece(source, needsManualReview = false))
        }

        val isVertical = direction == ReadingDirection.VERTICAL
        val totalLength = if (isVertical) source.height else source.width

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
                val (splitPos, needsReview) = if (mochiSmartEnabled) {
                    ContourDetector.findSafeSplitPoint(
                        totalLength = totalLength,
                        candidate = candidate,
                        tolerance = tolerance,
                        isVertical = true,
                        boundingBoxes = boundingBoxes,
                        bitmap = source
                    )
                } else {
                    SmartSplitResult(candidate, false)
                }

                val targetPos = if (splitPos <= currentY) candidate else splitPos
                val sliceHeight = (targetPos - currentY).coerceIn(1, remaining)
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
                val (splitPos, needsReview) = if (mochiSmartEnabled) {
                    ContourDetector.findSafeSplitPoint(
                        totalLength = totalLength,
                        candidate = candidate,
                        tolerance = tolerance,
                        isVertical = false,
                        boundingBoxes = boundingBoxes,
                        bitmap = source
                    )
                } else {
                    SmartSplitResult(candidate, false)
                }

                val targetPos = if (splitPos <= currentX) candidate else splitPos
                val sliceWidth = (targetPos - currentX).coerceIn(1, remaining)
                val slice = Bitmap.createBitmap(source, currentX, 0, sliceWidth, source.height)
                slices.add(SlicedPiece(slice, needsReview))
                currentX += sliceWidth
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
            sensitivity = settings.mochiSmartSensitivity
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
