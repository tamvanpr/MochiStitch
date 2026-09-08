package com.mochistitch.core.mochismart

import android.graphics.Bitmap
import com.mochistitch.core.settings.DetectionSensitivity
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

data class BoundingBox(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

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
        if (!initOpenCV()) {
            return emptyList()
        }

        return try {
            val mat = Mat()
            Utils.bitmapToMat(bitmap, mat)

            val grayMat = Mat()
            Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_RGBA2GRAY)

            val blurMat = Mat()
            Imgproc.GaussianBlur(grayMat, blurMat, Size(3.0, 3.0), 0.0)

            // Method 1: Adaptive Thresholding (catches speech bubbles with low contrast / soft outlines)
            val adaptiveMat = Mat()
            val (blockSize, cVal) = when (sensitivity) {
                DetectionSensitivity.LOW -> Pair(15, 5.0)
                DetectionSensitivity.MEDIUM -> Pair(11, 3.0)
                DetectionSensitivity.HIGH -> Pair(7, 2.0)
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

            // Method 2: Canny Edge Detection with adjusted thresholds based on sensitivity
            val edgesMat = Mat()
            val (lowThresh, highThresh) = when (sensitivity) {
                DetectionSensitivity.LOW -> Pair(40.0, 120.0)
                DetectionSensitivity.MEDIUM -> Pair(20.0, 80.0)
                DetectionSensitivity.HIGH -> Pair(10.0, 50.0)
            }
            Imgproc.Canny(blurMat, edgesMat, lowThresh, highThresh)

            // Combine Adaptive Thresholding and Canny edge maps via bitwise OR
            val combinedMat = Mat()
            Core.bitwise_or(edgesMat, adaptiveMat, combinedMat)

            // Morphological Closing to connect nearby edges and seal speech bubble outlines
            val closeKernelSize = when (sensitivity) {
                DetectionSensitivity.LOW -> 3
                DetectionSensitivity.MEDIUM -> 5
                DetectionSensitivity.HIGH -> 3
            }
            val closeKernel = Imgproc.getStructuringElement(
                Imgproc.MORPH_ELLIPSE,
                Size(closeKernelSize.toDouble(), closeKernelSize.toDouble())
            )
            val closedMat = Mat()
            Imgproc.morphologyEx(combinedMat, closedMat, Imgproc.MORPH_CLOSE, closeKernel)
            closeKernel.release()

            // Morphological Opening after Closing to eliminate small isolated noise pixels
            val openKernel = Imgproc.getStructuringElement(
                Imgproc.MORPH_ELLIPSE,
                Size(3.0, 3.0)
            )
            val processedMat = Mat()
            Imgproc.morphologyEx(closedMat, processedMat, Imgproc.MORPH_OPEN, openKernel)
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

            // Lower minimum area ratios to catch smaller speech bubbles
            val minAreaRatio = when (sensitivity) {
                DetectionSensitivity.LOW -> 0.0001
                DetectionSensitivity.MEDIUM -> 0.00005
                DetectionSensitivity.HIGH -> 0.00002
            }
            val minArea = totalArea * minAreaRatio
            val maxArea = totalArea * 0.95

            // Tighter aspect ratio filter (max 5.0) and min dimension constraints
            // to drastically reduce false positives from text lines/strokes
            val maxAspectRatio = 5.0
            val minDimension = 8

            val boundingBoxes = mutableListOf<BoundingBox>()

            for (contour in contours) {
                val openCVRect = Imgproc.boundingRect(contour)
                val w = openCVRect.width.toDouble()
                val h = openCVRect.height.toDouble()
                val area = w * h

                if (area in minArea..maxArea && openCVRect.width >= minDimension && openCVRect.height >= minDimension) {
                    val aspectRatio = maxOf(w / h, h / w)
                    if (aspectRatio <= maxAspectRatio) {
                        boundingBoxes.add(
                            BoundingBox(
                                left = openCVRect.x,
                                top = openCVRect.y,
                                right = openCVRect.x + openCVRect.width,
                                bottom = openCVRect.y + openCVRect.height
                            )
                        )
                    }
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
            processedMat.release()
            hierarchy.release()

            boundingBoxes
        } catch (t: Throwable) {
            emptyList()
        }
    }

    /**
     * Determines the optimal split coordinate within [candidate - tolerance, candidate + tolerance].
     *
     * @param totalLength total length along primary axis (height for vertical, width for horizontal)
     * @param candidate initial candidate split coordinate
     * @param tolerance allowed deviation range in pixels (e.g. 150)
     * @param isVertical true if splitting along Y axis (height), false if X axis (width)
     * @param boundingBoxes detected speech bubble / text region bounding boxes
     * @param bitmap optional source bitmap to evaluate pixel edge density for tie-breaking
     */
    fun findSafeSplitPoint(
        totalLength: Int,
        candidate: Int,
        tolerance: Int,
        isVertical: Boolean,
        boundingBoxes: List<BoundingBox>,
        bitmap: Bitmap? = null
    ): SmartSplitResult {
        if (candidate <= 0 || candidate >= totalLength) {
            return SmartSplitResult(candidate.coerceIn(0, totalLength), needsManualReview = false)
        }

        val minPos = (candidate - tolerance).coerceAtLeast(1)
        val maxPos = (candidate + tolerance).coerceAtMost(totalLength - 1)

        fun collides(pos: Int): Boolean {
            return boundingBoxes.any { rect ->
                if (isVertical) {
                    pos in rect.top..rect.bottom
                } else {
                    pos in rect.left..rect.right
                }
            }
        }

        if (!collides(candidate)) {
            return SmartSplitResult(candidate, needsManualReview = false)
        }

        val safePositions = mutableListOf<Int>()
        for (pos in minPos..maxPos) {
            if (!collides(pos)) {
                safePositions.add(pos)
            }
        }

        if (safePositions.isEmpty()) {
            return SmartSplitResult(candidate, needsManualReview = true)
        }

        val bestPos = safePositions.minByOrNull { pos ->
            val dist = kotlin.math.abs(pos - candidate)
            val density = calculatePixelEdgeDensity(bitmap, pos, isVertical)
            dist * 1000 + density
        } ?: candidate

        return SmartSplitResult(bestPos, needsManualReview = false)
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
