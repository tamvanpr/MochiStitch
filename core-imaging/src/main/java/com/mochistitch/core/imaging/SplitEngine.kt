package com.mochistitch.core.imaging

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import com.mochistitch.core.mochismart.ContourDetector
import com.mochistitch.core.mochismart.PixelComparisonDetector
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

    private fun createIndependentSlice(
        source: Bitmap,
        x: Int,
        y: Int,
        width: Int,
        height: Int
    ): Bitmap {
        val config = source.config ?: Bitmap.Config.RGB_565
        val independent = Bitmap.createBitmap(width, height, config)
        val canvas = Canvas(independent)
        val srcRect = Rect(x, y, x + width, y + height)
        val dstRect = Rect(0, 0, width, height)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmap(source, srcRect, dstRect, paint)
        return independent
    }

    private fun copyBitmap(source: Bitmap): Bitmap {
        val config = source.config ?: Bitmap.Config.RGB_565
        return source.copy(config, true)
    }

    fun sliceBitmapDetailed(
        source: Bitmap,
        splitMode: SplitMode,
        maxPixelLength: Int,
        direction: ReadingDirection,
        mochiSmartEnabled: Boolean = false,
        tolerance: Int = 150,
        sensitivity: DetectionSensitivity = DetectionSensitivity.MEDIUM,
        maxPagesPerFile: Int = 1,
        autoGutterDetectionEnabled: Boolean = true,
        pixelComparisonSensitivity: Float = 0.5f,
        pixelComparisonMargins: Int = 0,
        pixelComparisonStep: Int = 5,
        pixelComparisonMaxDeviationFactor: Float = 0.2f
    ): List<SlicedPiece> {
        if (splitMode == SplitMode.NO_LIMIT || (splitMode != SplitMode.MAX_PIXELS && splitMode != SplitMode.PAGES_PER_FILE) || maxPixelLength <= 0) {
            return listOf(SlicedPiece(copyBitmap(source), needsManualReview = false))
        }

        val isVertical = direction == ReadingDirection.VERTICAL
        val totalLength = if (isVertical) source.height else source.width

        // For PAGES_PER_FILE mode, treat each "page" as maxPagesPerFile
        if (splitMode == SplitMode.PAGES_PER_FILE && maxPagesPerFile > 0) {
            return sliceWithPageLimit(source, maxPixelLength, direction, maxPagesPerFile)
        }

        if (totalLength <= maxPixelLength) {
            return listOf(SlicedPiece(copyBitmap(source), needsManualReview = false))
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
                    val slice = createIndependentSlice(source, 0, currentY, source.width, remaining)
                    slices.add(SlicedPiece(slice, needsManualReview = false))
                    break
                }

                var candidateCutY = currentY + maxPixelLength

                if (autoGutterDetectionEnabled) {
                    candidateCutY = PixelComparisonDetector.findSafeCutPoint(
                        bitmap = source,
                        startY = currentY,
                        maxDistance = maxPixelLength,
                        sensitivity = pixelComparisonSensitivity,
                        margins = pixelComparisonMargins,
                        step = pixelComparisonStep,
                        maxSearchDeviationFactor = pixelComparisonMaxDeviationFactor
                    )
                }

                val clampedCandidate = min(candidateCutY, totalLength - 1)
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

                val effectiveSplitPos = splitPos.coerceIn(currentY + 1, totalLength - 1)
                val sliceHeight = (effectiveSplitPos - currentY).coerceIn(1, remaining)
                val slice = createIndependentSlice(source, 0, currentY, source.width, sliceHeight)
                slices.add(SlicedPiece(slice, needsReview))
                currentY += sliceHeight
            }
        } else {
            var currentX = 0
            while (currentX < totalLength) {
                val remaining = totalLength - currentX
                if (remaining <= maxPixelLength) {
                    val slice = createIndependentSlice(source, currentX, 0, remaining, source.height)
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

                val effectiveSplitPos = splitPos.coerceIn(currentX + 1, totalLength - 1)
                val sliceWidth = (effectiveSplitPos - currentX).coerceIn(1, remaining)
                val slice = createIndependentSlice(source, currentX, 0, sliceWidth, source.height)
                slices.add(SlicedPiece(slice, needsReview))
                currentX += sliceWidth
            }
        }

        return slices
    }

    private fun sliceWithPageLimit(
        source: Bitmap,
        maxPixelLength: Int,
        direction: ReadingDirection,
        maxPagesPerFile: Int
    ): List<SlicedPiece> {
        val isVertical = direction == ReadingDirection.VERTICAL
        val totalLength = if (isVertical) source.height else source.width

        if (totalLength <= maxPixelLength) {
            return listOf(SlicedPiece(copyBitmap(source), needsManualReview = false))
        }

        val slices = mutableListOf<SlicedPiece>()
        var currentY = 0
        var currentX = 0
        var pageCount = 0

        while (if (isVertical) currentY < totalLength else currentX < totalLength) {
            val remaining = totalLength - if (isVertical) currentY else currentX
            val currentSliceLength = min(remaining, maxPixelLength)

            val slice = if (isVertical) {
                createIndependentSlice(source, 0, currentY, source.width, currentSliceLength)
            } else {
                createIndependentSlice(source, currentX, 0, currentSliceLength, source.height)
            }

            slices.add(SlicedPiece(slice, needsManualReview = false))
            pageCount++

            if (isVertical) {
                currentY += currentSliceLength
            } else {
                currentX += currentSliceLength
            }

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
            maxPagesPerFile = settings.maxPagesPerFile,
            autoGutterDetectionEnabled = settings.autoGutterDetectionEnabled,
            pixelComparisonSensitivity = settings.pixelComparisonSensitivity,
            pixelComparisonMargins = settings.pixelComparisonMargins,
            pixelComparisonStep = settings.pixelComparisonStep,
            pixelComparisonMaxDeviationFactor = settings.pixelComparisonMaxDeviationFactor
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
            mochiSmartEnabled = false,
            autoGutterDetectionEnabled = false
        ).map { it.bitmap }
    }
}
