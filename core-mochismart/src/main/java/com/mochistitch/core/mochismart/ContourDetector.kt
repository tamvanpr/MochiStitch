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
    /**
     * Readable text and dialogue elements that MUST be protected from splitting.
     * Includes:
     * 1. Speech balloons (round/oval, with or without tails).
     * 2. Sharp-cornered dialogue boxes & system windows (system notifications, status boxes, skill windows).
     * 3. Monologue & caption boxes (usually at top/bottom/corner with solid background and border).
     * 4. Uncontained readable text (standalone thoughts, styled narration lines without a container box).
     */
    PROTECTED_BALLOON,

    /**
     * Decorative sound effect graphics (SFX) embedded in artwork.
     * Allowed to be split during image slicing.
     */
    SFX
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

    /**
     * True bila native OpenCV siap dipakai. Dipakai sebagai gerbang ketat:
     * bila Mochi Smart diminta tapi OpenCV tidak siap, pemotong WAJIB
     * menandai hasilnya perlu tinjauan manual alih-alih diam-diam lolos.
     */
    fun isReady(): Boolean = isOpenCVInitialized

    fun detectBoundingBoxes(
        bitmap: Bitmap,
        sensitivity: DetectionSensitivity = DetectionSensitivity.MEDIUM,
        useOtsuThreshold: Boolean = false
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
                val (lowThresh, highThresh) = if (useOtsuThreshold) {
                    val dummyMat = Mat()
                    val otsuVal = Imgproc.threshold(blurMat, dummyMat, 0.0, 255.0, Imgproc.THRESH_OTSU)
                    dummyMat.release()

                    val sensitivityMultiplier = when (sensitivity) {
                        DetectionSensitivity.LOW -> 1.2
                        DetectionSensitivity.MEDIUM -> 1.0
                        DetectionSensitivity.HIGH -> 0.8
                    }
                    val calculatedHigh = otsuVal * sensitivityMultiplier
                    Pair(calculatedHigh * 0.5, calculatedHigh)
                } else {
                    when (sensitivity) {
                        DetectionSensitivity.LOW -> Pair(40.0, 120.0)
                        DetectionSensitivity.MEDIUM -> Pair(20.0, 80.0)
                        DetectionSensitivity.HIGH -> Pair(10.0, 50.0)
                    }
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

                    val aspectRatio = if (h > 0.0 && w > 0.0) maxOf(w / h, h / w) else 0.0
                    val solidity = if (rectArea > 0.0) area / rectArea else 0.0

                    // Early noise filter: skip pure noise contours early (not SFX, not protected)
                    if (area < minArea * 0.3 || (solidity < 0.15 && aspectRatio > 3.0)) {
                        contour.release()
                        continue
                    }

                    if (rectArea in minArea..maxArea && openCVRect.width >= 8 && openCVRect.height >= 8) {

                        val contour2f = MatOfPoint2f(*contour.toArray())
                        val approx2f = MatOfPoint2f()
                        val epsilon = 0.02 * Imgproc.arcLength(contour2f, true)
                        Imgproc.approxPolyDP(contour2f, approx2f, epsilon, true)
                        val verticesCount = approx2f.total()
                        contour2f.release()
                        approx2f.release()

                        // Square system boxes and monologue frames have high aspect ratio but high solidity (>= 0.70).
                        // SFX graphics typically have low solidity (< 0.70) with high aspect ratio or irregular outlines.
                        val isSfx = (aspectRatio > 6.0 && solidity < 0.70) || (solidity < 0.20 && verticesCount > 10)
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
                val rowPixels = IntArray(width)

                for (y in 0 until height step rowStep) {
                    bitmap.getPixels(rowPixels, 0, width, 0, y, width, 1)
                    var nonWhiteCount = 0
                    for (x in 0 until width step 8) {
                        val pixel = rowPixels[x]
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

        return mergeUncontainedTextLines(boundingBoxes)
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


        fun calculateEffectiveTolerance(
        baseTolerance: Int = 150,
        canvasWidth: Int,
        canvasLength: Int,
        nearestProtectedBoxHeight: Int,
        marginFactor: Float = 1.25f
    ): Int {
        // Diketatkan: ekspansi maksimum 2x (dulu 3x) dan maksimal 15%
        // panjang canvas (dulu 20%) agar titik potong tidak meleset jauh.
        val effectiveBaseTolerance = (baseTolerance * (canvasWidth / 1000f)).toInt()
        val expandedTolerance = maxOf(effectiveBaseTolerance, (nearestProtectedBoxHeight * marginFactor).toInt())
        val maxExpandedTolerance = minOf(effectiveBaseTolerance * 2, (canvasLength * 0.15f).toInt())
        return expandedTolerance.coerceAtMost(maxExpandedTolerance)
    }

    fun calculateAdaptiveTolerance(
        baseTolerance: Int = 150,
        canvasWidth: Int,
        canvasLength: Int,
        protectedBoxes: List<BoundingBox>,
        isVertical: Boolean,
        targetPos: Int,
        marginFactor: Float = 1.25f
    ): Int {
        val nearestProtectedBox = protectedBoxes.filter { it.isProtected }.minByOrNull { box ->
            val center = if (isVertical) (box.top + box.bottom) / 2 else (box.left + box.right) / 2
            abs(center - targetPos)
        }
        val nearestHeight = nearestProtectedBox?.let { if (isVertical) it.bottom - it.top else it.right - it.left } ?: 0
        return calculateEffectiveTolerance(
            baseTolerance = baseTolerance,
            canvasWidth = canvasWidth,
            canvasLength = canvasLength,
            nearestProtectedBoxHeight = nearestHeight,
            marginFactor = marginFactor
        )
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
        val canvasWidth = bitmap?.width ?: 1000
        val effectiveTolerance = calculateAdaptiveTolerance(
            baseTolerance = tolerance,
            canvasWidth = canvasWidth,
            canvasLength = totalLength,
            protectedBoxes = boundingBoxes.filter { it.isProtected },
            isVertical = isVertical,
            targetPos = candidate
        )
        return findSafeSplitPointDetailed(
            totalLength = totalLength,
            currentPos = 0,
            targetPos = candidate,
            tolerance = effectiveTolerance,
            safeGaps = calculateSafeGaps(totalLength, boundingBoxes, isVertical),
            allowExceedOnNoSafeGap = true,
            preferShorterOverLonger = true,
            sfxBoxes = boundingBoxes.filter { !it.isProtected },
            protectedBoxes = boundingBoxes.filter { it.isProtected },
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
        protectedBoxes: List<BoundingBox> = emptyList(),
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

        fun findDistanceToNearestProtectedBoundary(pos: Int): Pair<Int, Int> {
            val relevantBoxes = protectedBoxes.filter { it.isProtected }
            if (relevantBoxes.isEmpty()) return Pair(Int.MAX_VALUE, 0)

            val nearestBox = relevantBoxes.minByOrNull { box ->
                val topOrLeft = if (isVertical) box.top else box.left
                val bottomOrRight = if (isVertical) box.bottom else box.right
                if (pos < topOrLeft) topOrLeft - pos
                else if (pos > bottomOrRight) pos - bottomOrRight
                else 0
            } ?: return Pair(Int.MAX_VALUE, 0)

            val topOrLeft = if (isVertical) nearestBox.top else nearestBox.left
            val bottomOrRight = if (isVertical) nearestBox.bottom else nearestBox.right

            val dist = if (pos < topOrLeft) topOrLeft - pos
            else if (pos > bottomOrRight) pos - bottomOrRight
            else 0

            val boxHeight = (nearestBox.bottom - nearestBox.top)
            return Pair(dist, boxHeight)
        }

        // 1. Prioritas 1 & Prioritas 2: Check for safe gaps overlapping search window [searchMin..searchMax]
        val gapsInTolerance = safeGaps.mapNotNull { gap ->
            val overlapStart = maxOf(gap.start, searchMin)
            val overlapEnd = minOf(gap.end, searchMax)
            if (overlapStart <= overlapEnd) {
                SafeGap(overlapStart, overlapEnd)
            } else null
        }

        if (gapsInTolerance.isNotEmpty()) {
            // Evaluasi Prioritas 1 terlebih dahulu (jarak ke tepi protected box >= minSafeMargin)
            var p1BestPos = -1
            var p1MinDist = Int.MAX_VALUE

            for (gap in gapsInTolerance) {
                for (pos in gap.start..gap.end) {
                    val (distToBox, boxHeight) = findDistanceToNearestProtectedBoundary(pos)
                    // Diketatkan: margin aman 20% tinggi balon agar potongan
                    // tidak menyerempet tepi balon.
                    val minSafeMargin = boxHeight * 0.2f
                    if (distToBox >= minSafeMargin) {
                        val distToTarget = abs(pos - clampedTarget)
                        if (distToTarget < p1MinDist || (distToTarget == p1MinDist && preferShorterOverLonger && pos <= clampedTarget)) {
                            p1BestPos = pos
                            p1MinDist = distToTarget
                        }
                    }
                }
            }

            if (p1BestPos != -1) {
                val finalPos = if (selectBestInGap != null) {
                    // Filter gap that contains p1BestPos
                    val containingGap = gapsInTolerance.firstOrNull { p1BestPos in it.start..it.end }
                    if (containingGap != null) {
                        selectBestInGap(containingGap.start, containingGap.end).coerceIn(containingGap.start, containingGap.end)
                    } else p1BestPos
                } else p1BestPos

                val (finalDistToBox, finalBoxHeight) = findDistanceToNearestProtectedBoundary(finalPos)
                val finalMinMargin = finalBoxHeight * 0.2f
                val isP1 = finalDistToBox >= finalMinMargin

                return SmartSplitResult(
                    splitPosition = finalPos.coerceIn(currentPos + 1, totalLength - 1),
                    needsManualReview = !isP1,
                    reviewReason = if (!isP1) "Celah aman dekat tepi balon (margin tipis)" else null
                )
            }

            // Prioritas 2: Safe gap ada, tapi jarak ke tepi protected box < minSafeMargin (margin tipis)
            var p2BestPos = -1
            var p2MinDist = Int.MAX_VALUE

            for (gap in gapsInTolerance) {
                val pos = getBestPointInRange(gap.start, gap.end)
                val distToTarget = abs(pos - clampedTarget)
                if (distToTarget < p2MinDist || (distToTarget == p2MinDist && preferShorterOverLonger && pos <= clampedTarget)) {
                    p2BestPos = pos
                    p2MinDist = distToTarget
                }
            }

            if (p2BestPos != -1) {
                return SmartSplitResult(
                    splitPosition = p2BestPos.coerceIn(currentPos + 1, totalLength - 1),
                    needsManualReview = true,
                    reviewReason = "Celah aman dekat tepi balon (margin tipis)"
                )
            }
        }

        // 3. Prioritas 3 (fallback TERAKHIR): safe gaps di luar search window (exceed tolerance / potong paksa)
        val gapBefore = safeGaps.lastOrNull { it.start <= searchMin - 1 && it.end >= currentPos + 1 }
        val distBefore = gapBefore?.let { abs(searchMin - minOf(it.end, searchMin - 1)) } ?: Int.MAX_VALUE

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
                    return SmartSplitResult(
                        splitPosition = pos.coerceIn(currentPos + 1, totalLength - 1),
                        needsManualReview = true,
                        reviewReason = "Melebihi batas potong demi menghindari balon"
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

        // Forced split at box edge or clampedTarget
        val edgePos = safeGaps.lastOrNull { it.start <= clampedTarget }?.end ?: clampedTarget
        val forcedPos = if (edgePos > currentPos) edgePos else clampedTarget
        return SmartSplitResult(
            splitPosition = forcedPos.coerceIn(currentPos + 1, totalLength - 1),
            needsManualReview = true,
            reviewReason = "Dipotong paksa di tepi balon / balon raksasa"
        )
    }

    /**
     * Pengaman keras: tidak boleh ada titik potong di dalam balon yang dilindungi.
     *
     * Bila [pos] jatuh di dalam salah satu [protectedBoxes], kembalikan tepi
     * aman terdekat (sebelum/sesudah balon, yang paling dekat posisi semula;
     * seri dimenangkan tepi sebelum agar potongan lebih pendek). Hasil selalu
     * dijepit ke [minPos]..[maxPos]. Bila tidak ada tepi aman dalam rentang
     * (balon menutup seluruh rentang), kembalikan posisi semula — pemanggil
     * WAJIB menandai hasil perlu tinjauan manual.
     */
    fun clampOutsideProtected(
        pos: Int,
        protectedBoxes: List<BoundingBox>,
        minPos: Int,
        maxPos: Int,
        isVertical: Boolean = true
    ): Int {
        if (maxPos <= minPos) return minPos
        var current = pos.coerceIn(minPos, maxPos)
        val boxes = protectedBoxes.filter { it.isProtected }
        repeat(boxes.size + 1) {
            val hit = boxes.firstOrNull { box ->
                if (isVertical) current in box.top..box.bottom else current in box.left..box.right
            } ?: return current
            val before = (if (isVertical) hit.top - 1 else hit.left - 1)
            val after = (if (isVertical) hit.bottom + 1 else hit.right + 1)
            val beforeOk = before in minPos..maxPos
            val afterOk = after in minPos..maxPos
            current = when {
                beforeOk && afterOk -> {
                    val distBefore = current - before
                    val distAfter = after - current
                    if (distBefore <= distAfter) before else after
                }
                beforeOk -> before
                afterOk -> after
                else -> return current
            }
        }
        return current
    }

    fun mergeUncontainedTextLines(boxes: List<BoundingBox>): List<BoundingBox> {
        if (boxes.isEmpty()) return emptyList()

        val protectedBoxes = boxes.filter { it.isProtected }.sortedBy { it.top }
        val sfxBoxes = boxes.filter { !it.isProtected }

        if (protectedBoxes.isEmpty()) return boxes

        val mergedProtected = mutableListOf<BoundingBox>()
        val visited = BooleanArray(protectedBoxes.size)

        for (i in protectedBoxes.indices) {
            if (visited[i]) continue
            visited[i] = true

            var current = protectedBoxes[i]
            var mergedInPass: Boolean

            do {
                mergedInPass = false
                for (j in protectedBoxes.indices) {
                    if (visited[j]) continue
                    val next = protectedBoxes[j]

                    val xOverlap = maxOf(0, minOf(current.right, next.right) - maxOf(current.left, next.left))
                    val minWidth = minOf(current.right - current.left, next.right - next.left)
                    val hasHorizontalOverlap = xOverlap >= (0.25 * minWidth).toInt() || xOverlap >= 12

                    if (hasHorizontalOverlap) {
                        val verticalGap = maxOf(0, maxOf(current.top, next.top) - minOf(current.bottom, next.bottom))
                        val lineH = minOf(current.bottom - current.top, next.bottom - next.top)
                        val maxAllowedGap = (1.8 * lineH).toInt().coerceAtLeast(10)

                        if (verticalGap <= maxAllowedGap) {
                            current = BoundingBox(
                                left = minOf(current.left, next.left),
                                top = minOf(current.top, next.top),
                                right = maxOf(current.right, next.right),
                                bottom = maxOf(current.bottom, next.bottom),
                                type = BoundingBoxType.PROTECTED_BALLOON
                            )
                            visited[j] = true
                            mergedInPass = true
                        }
                    }
                }
            } while (mergedInPass)

            mergedProtected.add(current)
        }

        return mergedProtected + sfxBoxes
    }
}