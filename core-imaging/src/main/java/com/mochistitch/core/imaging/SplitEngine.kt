package com.mochistitch.core.imaging

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import com.mochistitch.core.mochismart.BoundingBox
import com.mochistitch.core.mochismart.ContourDetector
import com.mochistitch.core.mochismart.PixelComparisonDetector
import com.mochistitch.core.mochismart.SmartSplitResult
import com.mochistitch.core.settings.DetectionSensitivity
import com.mochistitch.core.settings.MochiStitchSettings
import com.mochistitch.core.settings.ReadingDirection
import com.mochistitch.core.settings.SplitMode
import kotlin.math.abs
import kotlin.math.min

data class SlicedPiece(
    val bitmap: Bitmap,
    val needsManualReview: Boolean = false,
    val reviewReason: String? = null
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
        pixelComparisonMaxDeviationFactor: Float = 0.2f,
        allowExceedOnNoSafeGap: Boolean = true,
        preferShorterOverLonger: Boolean = true
    ): List<SlicedPiece> {
        if (splitMode == SplitMode.NO_LIMIT || (splitMode != SplitMode.MAX_PIXELS && splitMode != SplitMode.PAGES_PER_FILE) || maxPixelLength <= 0) {
            return listOf(SlicedPiece(copyBitmap(source), needsManualReview = false))
        }

        val isVertical = direction == ReadingDirection.VERTICAL
        val totalLength = if (isVertical) source.height else source.width

        if (splitMode == SplitMode.PAGES_PER_FILE && maxPagesPerFile > 0) {
            return sliceWithPageLimit(source, maxPixelLength, direction, maxPagesPerFile)
        }

        if (totalLength <= maxPixelLength) {
            return listOf(SlicedPiece(copyBitmap(source), needsManualReview = false))
        }

        // 1. Detect bounding boxes if Mochi Smart is enabled
        val boundingBoxes = if (mochiSmartEnabled) {
            ContourDetector.detectBoundingBoxes(source, sensitivity)
        } else {
            emptyList()
        }

        // 2. Calculate safe gaps along primary axis
        val safeGaps = ContourDetector.calculateSafeGaps(
            totalLength = totalLength,
            boundingBoxes = boundingBoxes,
            isVertical = isVertical
        )

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

                val targetCutY = currentY + maxPixelLength

                // Pixel comparison gutter selector within gap range
                val selectBestInGap: ((Int, Int) -> Int)? = if (autoGutterDetectionEnabled) {
                    { gapStart: Int, gapEnd: Int ->
                        var bestY = (targetCutY).coerceIn(gapStart, gapEnd)
                        var minVariance = Float.MAX_VALUE
                        var minDistance = Int.MAX_VALUE
                        var foundClean = false

                        val effectiveStep = pixelComparisonStep.coerceAtLeast(1)
                        var y = gapStart
                        while (y <= gapEnd) {
                            val isClean = PixelComparisonDetector.canSliceRow(
                                source, y, pixelComparisonSensitivity, pixelComparisonMargins
                            )
                            val dist = abs(y - targetCutY)

                            if (isClean) {
                                if (!foundClean || dist < minDistance || (dist == minDistance && preferShorterOverLonger && y <= targetCutY)) {
                                    foundClean = true
                                    bestY = y
                                    minDistance = dist
                                }
                            } else if (!foundClean) {
                                val variance = PixelComparisonDetector.calculateRowPixelVariance(
                                    source, y, pixelComparisonMargins
                                )
                                if (variance < minVariance - 0.001f || (abs(variance - minVariance) <= 0.001f && dist < minDistance)) {
                                    minVariance = variance
                                    minDistance = dist
                                    bestY = y
                                }
                            }
                            y += effectiveStep
                        }
                        bestY
                    }
                } else null

                val splitResult = if (mochiSmartEnabled || autoGutterDetectionEnabled) {
                    ContourDetector.findSafeSplitPointDetailed(
                        totalLength = totalLength,
                        currentPos = currentY,
                        targetPos = targetCutY,
                        tolerance = tolerance,
                        safeGaps = safeGaps,
                        allowExceedOnNoSafeGap = allowExceedOnNoSafeGap,
                        preferShorterOverLonger = preferShorterOverLonger,
                        sfxBoxes = boundingBoxes.filter { !it.isProtected },
                        isVertical = true,
                        selectBestInGap = selectBestInGap
                    )
                } else {
                    SmartSplitResult(targetCutY.coerceAtMost(totalLength - 1), needsManualReview = false)
                }

                val effectiveSplitPos = splitResult.splitPosition.coerceIn(currentY + 1, totalLength - 1)
                val sliceHeight = (effectiveSplitPos - currentY).coerceIn(1, remaining)
                val slice = createIndependentSlice(source, 0, currentY, source.width, sliceHeight)
                slices.add(SlicedPiece(slice, splitResult.needsManualReview, splitResult.reviewReason))
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

                val targetCutX = currentX + maxPixelLength

                val splitResult = if (mochiSmartEnabled || autoGutterDetectionEnabled) {
                    ContourDetector.findSafeSplitPointDetailed(
                        totalLength = totalLength,
                        currentPos = currentX,
                        targetPos = targetCutX,
                        tolerance = tolerance,
                        safeGaps = safeGaps,
                        allowExceedOnNoSafeGap = allowExceedOnNoSafeGap,
                        preferShorterOverLonger = preferShorterOverLonger,
                        sfxBoxes = boundingBoxes.filter { !it.isProtected },
                        isVertical = false,
                        selectBestInGap = null
                    )
                } else {
                    SmartSplitResult(targetCutX.coerceAtMost(totalLength - 1), needsManualReview = false)
                }

                val effectiveSplitPos = splitResult.splitPosition.coerceIn(currentX + 1, totalLength - 1)
                val sliceWidth = (effectiveSplitPos - currentX).coerceIn(1, remaining)
                val slice = createIndependentSlice(source, currentX, 0, sliceWidth, source.height)
                slices.add(SlicedPiece(slice, splitResult.needsManualReview, splitResult.reviewReason))
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
            pixelComparisonMaxDeviationFactor = settings.pixelComparisonMaxDeviationFactor,
            allowExceedOnNoSafeGap = settings.allowExceedOnNoSafeGap,
            preferShorterOverLonger = settings.preferShorterOverLonger
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
