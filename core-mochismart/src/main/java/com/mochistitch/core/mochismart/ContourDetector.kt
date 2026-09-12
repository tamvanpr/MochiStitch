package com.mochistitch.core.mochismart

import android.graphics.Bitmap
import com.mochistitch.core.settings.DetectionSensitivity
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class BoundingBoxType {
    PROTECTED_BALLOON,  // Speech balloons, dialogue boxes, system windows, monologue boxes, skill text, monologue lines
    SFX                 // Background sound effect graphics
}

data class BoundingBox(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val type: BoundingBoxType = BoundingBoxType.PROTECTED_BALLOON
) {
    val isProtected: Boolean
        get() = type == BoundingBoxType.PROTECTED_BALLOON
}

data class SafeGap(
    val start: Int,
    val end: Int
) {
    val length: Int get() = max(0, end - start + 1)
    operator fun contains(pos: Int): Boolean = pos in start..end
}

data class SmartSplitResult(
    val splitPosition: Int,
    val needsManualReview: Boolean = false,
    val reviewReason: String? = null
)

object ContourDetector {

    private var isOpenCVInitialized = false

    fun initOpenCV(): Boolean {
        if (isOpenCVInitialized) return true
        isOpenCVInitialized = try {
            OpenCVLoader.initLocal()
        } catch (t: Throwable) {
            false
        }
        return isOpenCVInitialized
    }

    fun detectBoundingBoxes(
        bitmap: Bitmap,
        sensitivity: DetectionSensitivity = DetectionSensitivity.MEDIUM
    ): List<BoundingBox> {
        val boundingBoxes = mutableListOf<BoundingBox>()
        val opencvSuccess = initOpenCV()

        if (opencvSuccess) {
            try {
                val mat = Mat()
                Utils.bitmapToMat(bitmap, mat)

                val grayMat = Mat()
                Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_RGBA2GRAY)

                val blurMat = Mat()
                Imgproc.GaussianBlur(grayMat, blurMat, Size(3.0, 3.0), 0.0)

                val adaptiveMat = Mat()
                val (blockSize, cVal) = when (sensitivity) {
                    DetectionSensitivity.LOW -> Pair(17, 6.0)
                    DetectionSensitivity.MEDIUM -> Pair(13, 4.0)
                    DetectionSensitivity.HIGH -> Pair(9, 2.0)
                }
                Imgproc.adaptiveThreshold(
                    blurMat,
                    adaptiveMat,
                    255.0,
                    Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                    Imgproc.THRESH_BINARY_INV,
                    blockSize,
                    cVal
                )

                val edgesMat = Mat()
                val (lowThresh, highThresh) = when (sensitivity) {
                    DetectionSensitivity.LOW -> Pair(40.0, 120.0)
                    DetectionSensitivity.MEDIUM -> Pair(20.0, 80.0)
                    DetectionSensitivity.HIGH -> Pair(10.0, 50.0)
                }
                Imgproc.Canny(blurMat, edgesMat, lowThresh, highThresh)

                val combinedMat = Mat()
                Core.bitwise_or(edgesMat, adaptiveMat, combinedMat)

                val closeKernelSize = when (sensitivity) {
                    DetectionSensitivity.LOW -> 5
                    DetectionSensitivity.MEDIUM -> 7
                    DetectionSensitivity.HIGH -> 5
                }
                val closeKernel = Imgproc.getStructuringElement(
                    Imgproc.MORPH_ELLIPSE,
                    Size(closeKernelSize.toDouble(), closeKernelSize.toDouble())
                )
                val closedMat = Mat()
                Imgproc.morphologyEx(combinedMat, closedMat, Imgproc.MORPH_CLOSE, closeKernel)
                closeKernel.release()

                val textKernel = Imgproc.getStructuringElement(
                    Imgproc.MORPH_RECT,
                    Size(15.0, 3.0)
                )
                val textGroupedMat = Mat()
                Imgproc.morphologyEx(closedMat, textGroupedMat, Imgproc.MORPH_DILATE, textKernel)
                textKernel.release()

                val openKernel = Imgproc.getStructuringElement(
                    Imgproc.MORPH_ELLIPSE,
                    Size(3.0, 3.0)
                )
                val processedMat = Mat()
                Imgproc.morphologyEx(textGroupedMat, processedMat, Imgproc.MORPH_OPEN, openKernel)
                openKernel.release()

                val contours = ArrayList<MatOfPoint>()
                val hierarchy = Mat()
                Imgproc.findContours(
                    processedMat,
                    contours,
                    hierarchy,
                    Imgproc.RETR_EXTERNAL,
                    Imgproc.CHAIN_APPROX_SIMPLE
                )

                val imageWidth = bitmap.width
                val imageHeight = bitmap.height
                val totalArea = imageWidth.toDouble() * imageHeight.toDouble()

                val minAreaRatio = when (sensitivity) {
                    DetectionSensitivity.LOW -> 0.0001
                    DetectionSensitivity.MEDIUM -> 0.00003
                    DetectionSensitivity.HIGH -> 0.00001
                }
                val minArea = totalArea * minAreaRatio
                val maxArea = totalArea * 0.95

                for (contour in contours) {
                    val openCVRect = Imgproc.boundingRect(contour)
                    val w = openCVRect.width.toDouble()
                    val h = openCVRect.height.toDouble()
                    val area = Imgproc.contourArea(contour)
                    val rectArea = w * h

                    if (rectArea in minArea..maxArea && openCVRect.width >= 8 && openCVRect.height >= 8) {
                        val aspectRatio = maxOf(w / h, h / w)
                        val solidity = if (rectArea > 0) area / rectArea else 0.0

                        val contour2f = MatOfPoint2f(*contour.toArray())
                        val approx2f = MatOfPoint2f()
                        val epsilon = 0.02 * Imgproc.arcLength(contour2f, true)
                        Imgproc.approxPolyDP(contour2f, approx2f, epsilon, true)
                        val verticesCount = approx2f.total()
                        contour2f.release()
                        approx2f.release()

                        val isSfx = aspectRatio > 6.0 || (solidity < 0.20 && verticesCount > 10)
                        val boxType = if (isSfx) BoundingBoxType.SFX else BoundingBoxType.PROTECTED_BALLOON

                        boundingBoxes.add(
                            BoundingBox(
                                left = openCVRect.x,
                                top = openCVRect.y,
                                right = openCVRect.x + openCVRect.width,
                                bottom = openCVRect.y + openCVRect.height,
                                type = boxType
                            )
                        )
                    }
                    contour.release()
                }

                mat.release()
                grayMat.release()
                blurMat.release()
                adaptiveMat.release()
                edgesMat.release()
                combinedMat.release()
                closedMat.release()
                textGroupedMat.release()
                processedMat.release()
                hierarchy.release()
            } catch (t: Throwable) {
                // OpenCV detection failed
            }
        }

        if (boundingBoxes.isEmpty() && !bitmap.isRecycled) {
            try {
                val width = bitmap.width
                val height = bitmap.height
                val rowStep = 4
                val nonWhiteThreshold = (width / 4) * (when (sensitivity) {
                    DetectionSensitivity.LOW -> 0.15
                    DetectionSensitivity.MEDIUM -> 0.08
                    DetectionSensitivity.HIGH -> 0.03
                })

                var inContentBlock = false
                var blockTop = 0

                for (y in 0 until height step rowStep) {
                    var nonWhiteCount = 0
                    for (x in 0 until width step 8) {
                        val pixel = bitmap.getPixel(x, y)
                        val r = (pixel shr 16) and 0xFF
                        val g = (pixel shr 8) and 0xFF
                        val b = pixel and 0xFF
                        if (r < 240 || g < 240 || b < 240) {
                            nonWhiteCount++
                        }
                    }

                    val isContentRow = nonWhiteCount >= nonWhiteThreshold
                    if (isContentRow && !inContentBlock) {
                        inContentBlock = true
                        blockTop = y
                    } else if (!isContentRow && inContentBlock) {
                        inContentBlock = false
                        val blockBottom = y
                        if (blockBottom - blockTop >= 12) {
                            boundingBoxes.add(
                                BoundingBox(
                                    left = 0,
                                    top = blockTop,
                                    right = width,
                                    bottom = blockBottom,
                                    type = BoundingBoxType.PROTECTED_BALLOON
                                )
                            )
                        }
                    }
                }
                if (inContentBlock) {
                    val blockBottom = height - 1
                    if (blockBottom - blockTop >= 12) {
                        boundingBoxes.add(
                            BoundingBox(
                                left = 0,
                                top = blockTop,
                                right = width,
                                bottom = blockBottom,
                                type = BoundingBoxType.PROTECTED_BALLOON
                            )
                        )
                    }
                }
            } catch (t: Throwable) {
                // fallback ignored
            }
        }

        return boundingBoxes
    }

    /**
     * Calculates safe gaps (intervals strictly clear of all protected bounding boxes)
     * along the main splitting axis.
     */
    fun calculateSafeGaps(
        totalLength: Int,
        boundingBoxes: List<BoundingBox>,
        isVertical: Boolean
    ): List<SafeGap> {
        if (totalLength <= 0) return emptyList()

        val protectedBoxes = boundingBoxes.filter { it.isProtected }
        if (protectedBoxes.isEmpty()) {
            return listOf(SafeGap(0, totalLength - 1))
        }

        val occupiedRanges = protectedBoxes.map { rect ->
            if (isVertical) {
                rect.top.coerceIn(0, totalLength - 1)..rect.bottom.coerceIn(0, totalLength - 1)
            } else {
                rect.left.coerceIn(0, totalLength - 1)..rect.right.coerceIn(0, totalLength - 1)
            }
        }.sortedBy { it.first }

        val mergedRanges = mutableListOf<IntRange>()
        for (range in occupiedRanges) {
            if (mergedRanges.isEmpty()) {
                mergedRanges.add(range)
            } else {
                val last = mergedRanges.last()
                if (range.first <= last.last + 1) {
                    val newLast = last.first..maxOf(last.last, range.last)
                    mergedRanges[mergedRanges.size - 1] = newLast
                } else {
                    mergedRanges.add(range)
                }
            }
        }

        val safeGaps = mutableListOf<SafeGap>()
        var currentStart = 0

        for (occ in mergedRanges) {
            if (occ.first > currentStart) {
                safeGaps.add(SafeGap(currentStart, occ.first - 1))
            }
            currentStart = maxOf(currentStart, occ.last + 1)
        }

        if (currentStart < totalLength) {
            safeGaps.add(SafeGap(currentStart, totalLength - 1))
        }

        return safeGaps
    }

    fun findSafeSplitPoint(
        totalLength: Int,
        candidate: Int,
        tolerance: Int,
        isVertical: Boolean,
        boundingBoxes: List<BoundingBox>,
        bitmap: Bitmap? = null,
        maxSearchDeviationFactor: Float = 0.2f
    ): SmartSplitResult {
        val effectiveTolerance = max(tolerance, (candidate * maxSearchDeviationFactor).toInt()).coerceAtLeast(150)
        return findSafeSplitPointDetailed(
            totalLength = totalLength,
            currentPos = 0,
            targetPos = candidate,
            tolerance = effectiveTolerance,
            safeGaps = calculateSafeGaps(totalLength, boundingBoxes, isVertical),
            allowExceedOnNoSafeGap = true,
            preferShorterOverLonger = true,
            sfxBoxes = boundingBoxes.filter { !it.isProtected },
            isVertical = isVertical
        )
    }

    fun findSafeSplitPointDetailed(
        totalLength: Int,
        currentPos: Int,
        targetPos: Int,
        tolerance: Int,
        safeGaps: List<SafeGap>,
        allowExceedOnNoSafeGap: Boolean = true,
        preferShorterOverLonger: Boolean = true,
        sfxBoxes: List<BoundingBox> = emptyList(),
        isVertical: Boolean = true,
        selectBestInGap: ((minPos: Int, maxPos: Int) -> Int)? = null
    ): SmartSplitResult {
        val clampedTarget = targetPos.coerceIn(currentPos + 1, totalLength - 1)
        val searchMin = (clampedTarget - tolerance).coerceAtLeast(currentPos + 1)
        val searchMax = (clampedTarget + tolerance).coerceAtMost(totalLength - 1)

        fun collidesWithSfx(pos: Int): Boolean {
            return sfxBoxes.any { rect ->
                if (isVertical) pos in rect.top..rect.bottom else pos in rect.left..rect.right
            }
        }

        fun getBestPointInRange(rangeStart: Int, rangeEnd: Int): Int {
            if (rangeStart > rangeEnd) return rangeStart
            if (selectBestInGap != null) {
                return selectBestInGap(rangeStart, rangeEnd).coerceIn(rangeStart, rangeEnd)
            }
            var bestPos = rangeStart
            var bestDist = Int.MAX_VALUE
            var bestIsSfx = true

            for (pos in rangeStart..rangeEnd) {
                val sfx = collidesWithSfx(pos)
                val dist = abs(pos - clampedTarget)
                if (!sfx && bestIsSfx) {
                    bestPos = pos
                    bestDist = dist
                    bestIsSfx = false
                } else if (sfx == bestIsSfx) {
                    if (dist < bestDist) {
                        bestPos = pos
                        bestDist = dist
                    } else if (dist == bestDist && preferShorterOverLonger && pos <= clampedTarget) {
                        bestPos = pos
                    }
                }
            }
            return bestPos
        }

        // 1. Check for safe gaps overlapping search window [searchMin..searchMax]
        val gapsInTolerance = safeGaps.mapNotNull { gap ->
            val overlapStart = maxOf(gap.start, searchMin)
            val overlapEnd = minOf(gap.end, searchMax)
            if (overlapStart <= overlapEnd) {
                SafeGap(overlapStart, overlapEnd)
            } else null
        }

        if (gapsInTolerance.isNotEmpty()) {
            var bestPos = -1
            var minDistance = Int.MAX_VALUE

            for (gap in gapsInTolerance) {
                val pos = getBestPointInRange(gap.start, gap.end)
                val dist = abs(pos - clampedTarget)

                val isBetter = when {
                    bestPos == -1 -> true
                    dist < minDistance -> true
                    dist == minDistance -> {
                        if (preferShorterOverLonger && pos <= clampedTarget && bestPos > clampedTarget) true
                        else false
                    }
                    else -> false
                }

                if (isBetter) {
                    bestPos = pos
                    minDistance = dist
                }
            }

            return SmartSplitResult(
                splitPosition = bestPos.coerceIn(currentPos + 1, totalLength - 1),
                needsManualReview = false,
                reviewReason = null
            )
        }

        // 2. Check for safe gaps ANYWHERE before searchMin or after searchMax
        // a. Find nearest safe gap BEFORE searchMin
        val gapBefore = safeGaps.lastOrNull { it.start <= searchMin - 1 && it.end >= currentPos + 1 }
        val distBefore = gapBefore?.let { abs(searchMin - minOf(it.end, searchMin - 1)) } ?: Int.MAX_VALUE

        // b. Find nearest safe gap AFTER searchMax
        val gapAfter = if (allowExceedOnNoSafeGap) safeGaps.firstOrNull { it.end >= searchMax + 1 } else null
        val distAfter = gapAfter?.let { abs(maxOf(it.start, searchMax + 1) - searchMax) } ?: Int.MAX_VALUE

        if (gapBefore != null || gapAfter != null) {
            val chooseBefore = when {
                gapBefore != null && gapAfter == null -> true
                gapBefore == null && gapAfter != null -> false
                distBefore < distAfter -> true
                distAfter < distBefore -> false
                else -> preferShorterOverLonger
            }

            if (chooseBefore && gapBefore != null) {
                val gapStart = maxOf(gapBefore.start, currentPos + 1)
                val gapEnd = minOf(gapBefore.end, searchMin - 1)
                if (gapStart <= gapEnd) {
                    val pos = getBestPointInRange(gapStart, gapEnd)
                    val isExceeded = pos > clampedTarget
                    return SmartSplitResult(
                        splitPosition = pos.coerceIn(currentPos + 1, totalLength - 1),
                        needsManualReview = isExceeded,
                        reviewReason = if (isExceeded) "Melebihi batas potong demi menghindari balon" else null
                    )
                }
            } else if (!chooseBefore && gapAfter != null) {
                val gapStart = maxOf(gapAfter.start, searchMax + 1)
                val gapEnd = minOf(gapAfter.end, totalLength - 1)
                if (gapStart <= gapEnd) {
                    val pos = getBestPointInRange(gapStart, gapEnd)
                    return SmartSplitResult(
                        splitPosition = pos.coerceIn(currentPos + 1, totalLength - 1),
                        needsManualReview = true,
                        reviewReason = "Melebihi batas potong demi menghindari balon"
                    )
                }
            }
        }

        // 3. Forced split at box edge if giant balloon spans whole remaining range
        val edgePos = safeGaps.lastOrNull { it.start <= clampedTarget }?.end ?: clampedTarget
        val forcedPos = if (edgePos > currentPos) edgePos else clampedTarget
        return SmartSplitResult(
            splitPosition = forcedPos.coerceIn(currentPos + 1, totalLength - 1),
            needsManualReview = true,
            reviewReason = "Dipotong paksa di tepi balon / balon raksasa"
        )
    }
}
