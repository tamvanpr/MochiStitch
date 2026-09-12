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

data class SmartSplitResult(
    val splitPosition: Int,
    val needsManualReview: Boolean
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

                // Method 1: Adaptive Thresholding (catches speech bubbles, system dialog boxes, monologue boxes)
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

                // Method 2: Canny Edge Detection
                val edgesMat = Mat()
                val (lowThresh, highThresh) = when (sensitivity) {
                    DetectionSensitivity.LOW -> Pair(40.0, 120.0)
                    DetectionSensitivity.MEDIUM -> Pair(20.0, 80.0)
                    DetectionSensitivity.HIGH -> Pair(10.0, 50.0)
                }
                Imgproc.Canny(blurMat, edgesMat, lowThresh, highThresh)

                // Combine adaptive thresholding and edge maps
                val combinedMat = Mat()
                Core.bitwise_or(edgesMat, adaptiveMat, combinedMat)

                // Morphological Closing to seal speech bubble outlines and box boundaries
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

                // Horizontal morphological dilation to group horizontal text rows (monologues, skill names, narration) into text block boxes
                val textKernel = Imgproc.getStructuringElement(
                    Imgproc.MORPH_RECT,
                    Size(15.0, 3.0)
                )
                val textGroupedMat = Mat()
                Imgproc.morphologyEx(closedMat, textGroupedMat, Imgproc.MORPH_DILATE, textKernel)
                textKernel.release()

                // Morphological Opening to eliminate small isolated noise
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
                // OpenCV detection failed, fall back to projection profile
            }
        }

        // Robust Pure Kotlin Row Projection Profile Fallback / Supplementary Protection
        // Guarantees that speech balloons and text blocks are protected even if OpenCV fails or is uninitialized
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
     * Determines the optimal split coordinate that STRICTLY preserves protected regions
     * (speech balloons, dialogue boxes, system windows, monologue text, skill text).
     *
     * @param totalLength total length along primary axis
     * @param candidate initial candidate split coordinate
     * @param tolerance preferred allowed deviation range in pixels (e.g. 150)
     * @param isVertical true if splitting along Y axis, false if X axis
     * @param boundingBoxes detected bounding boxes
     * @param bitmap optional source bitmap to evaluate pixel edge density for tie-breaking
     */
    fun findSafeSplitPoint(
        totalLength: Int,
        candidate: Int,
        tolerance: Int,
        isVertical: Boolean,
        boundingBoxes: List<BoundingBox>,
        bitmap: Bitmap? = null,
        maxSearchDeviationFactor: Float = 0.2f
    ): SmartSplitResult {
        if (candidate <= 0 || candidate >= totalLength) {
            return SmartSplitResult(candidate.coerceIn(0, totalLength), needsManualReview = false)
        }

        val protectedBoxes = boundingBoxes.filter { it.isProtected }
        val sfxBoxes = boundingBoxes.filter { !it.isProtected }

        fun collidesWithProtected(pos: Int): Boolean {
            return protectedBoxes.any { rect ->
                if (isVertical) {
                    pos in rect.top..rect.bottom
                } else {
                    pos in rect.left..rect.right
                }
            }
        }

        fun collidesWithSfx(pos: Int): Boolean {
            return sfxBoxes.any { rect ->
                if (isVertical) {
                    pos in rect.top..rect.bottom
                } else {
                    pos in rect.left..rect.right
                }
            }
        }

        val initialWindow = max(tolerance, (candidate * maxSearchDeviationFactor).toInt()).coerceAtLeast(150)

        // Function to find best row in a given search range (scoring all rows to avoid null fallback)
        fun findBestRowInRange(searchMin: Int, searchMax: Int): Pair<Int, Boolean> {
            var bestPos = candidate
            var minCost = Double.MAX_VALUE
            var bestCollidesProtected = true

            for (pos in searchMin..searchMax) {
                val collidesProtected = collidesWithProtected(pos)
                val sfxCollision = collidesWithSfx(pos)
                val density = calculatePixelEdgeDensity(bitmap, pos, isVertical)
                val dist = abs(pos - candidate)

                // Cost function:
                // 1. Massive penalty if it hits a protected speech bubble/text box
                // 2. Moderate penalty for SFX
                // 3. Distance penalty from candidate
                // 4. Strong bias favoring shorter slices (pos <= candidate) over longer ones (pos > candidate)
                // 5. Pixel edge density penalty
                val protectedPenalty = if (collidesProtected) 10000.0 else 0.0
                val sfxPenalty = if (sfxCollision) 100.0 else 0.0
                val longerSlicePenalty = if (pos > candidate) 80.0 else 0.0

                val cost = protectedPenalty + sfxPenalty + (dist * 10.0) + longerSlicePenalty + (density * 5.0)

                if (cost < minCost) {
                    minCost = cost
                    bestPos = pos
                    bestCollidesProtected = collidesProtected
                }
            }
            return Pair(bestPos, bestCollidesProtected)
        }

        // 1. Initial search window around candidate based on maxSearchDeviationFactor / tolerance
        val initialMin = (candidate - initialWindow).coerceAtLeast(1)
        val initialMax = (candidate + initialWindow).coerceAtMost(totalLength - 1)
        var (bestPos, collidesProtected) = findBestRowInRange(initialMin, initialMax)

        // 2. If initial window hit a protected box, try expanding window to find a clean gutter (prioritizing shorter/upwards first)
        if (collidesProtected) {
            var expandedWindow = initialWindow * 2
            while (expandedWindow <= totalLength * 2) {
                val expMin = (candidate - expandedWindow).coerceAtLeast(1)
                val expMax = (candidate + (expandedWindow / 2)).coerceAtMost(totalLength - 1) // search upwards (shorter) more aggressively
                val (expPos, expCollides) = findBestRowInRange(expMin, expMax)
                if (!expCollides) {
                    bestPos = expPos
                    collidesProtected = false
                    break
                }
                expandedWindow *= 2
            }
        }

        val finalSplitPos = bestPos.coerceIn(1, totalLength - 1)
        return SmartSplitResult(splitPosition = finalSplitPos, needsManualReview = collidesProtected)
    }

    private fun calculatePixelEdgeDensity(bitmap: Bitmap?, pos: Int, isVertical: Boolean): Int {
        if (bitmap == null || bitmap.isRecycled) return 0
        return try {
            val width = bitmap.width
            val height = bitmap.height
            var nonWhiteCount = 0
            val sampleStep = 4

            if (isVertical) {
                val y = pos.coerceIn(0, height - 1)
                for (x in 0 until width step sampleStep) {
                    val pixel = bitmap.getPixel(x, y)
                    val r = (pixel shr 16) and 0xFF
                    val g = (pixel shr 8) and 0xFF
                    val b = pixel and 0xFF
                    if (r < 240 || g < 240 || b < 240) {
                        nonWhiteCount++
                    }
                }
            } else {
                val x = pos.coerceIn(0, width - 1)
                for (y in 0 until height step sampleStep) {
                    val pixel = bitmap.getPixel(x, y)
                    val r = (pixel shr 16) and 0xFF
                    val g = (pixel shr 8) and 0xFF
                    val b = pixel and 0xFF
                    if (r < 240 || g < 240 || b < 240) {
                        nonWhiteCount++
                    }
                }
            }
            nonWhiteCount
        } catch (t: Throwable) {
            0
        }
    }
}
