package com.mochistitch.core.imaging

import android.graphics.Bitmap
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

object ProtectedRegions {
    fun find(bitmap: Bitmap): List<IntRange> {
        if (bitmap.width <= 0 || bitmap.height <= 0) return emptyList()
        if (!runCatching { OpenCVLoader.initDebug() }.getOrDefault(false)) return emptyList()
        val rgba = Mat()
        val gray = Mat()
        val blur = Mat()
        val edges = Mat()
        val closed = Mat()
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 3.0))
        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        return try {
            Utils.bitmapToMat(bitmap, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, blur, Size(3.0, 3.0), 0.0)
            Imgproc.Canny(blur, edges, 30.0, 100.0)
            Imgproc.morphologyEx(edges, closed, Imgproc.MORPH_CLOSE, kernel)
            Imgproc.findContours(closed, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
            val totalArea = bitmap.width.toDouble() * bitmap.height.toDouble()
            val minArea = totalArea * 0.00015
            val maxArea = totalArea * 0.85
            contours.mapNotNull { contour ->
                val rect = Imgproc.boundingRect(contour)
                val area = Imgproc.contourArea(contour)
                val rectArea = rect.width.toDouble() * rect.height.toDouble()
                val valid = rect.width >= 8 && rect.height >= 8 &&
                    area >= minArea && area <= maxArea && rectArea > 0.0 &&
                    area / rectArea >= 0.12
                if (!valid) null else {
                    val top = (rect.y - 12).coerceAtLeast(0)
                    val bottom = (rect.y + rect.height + 12).coerceAtMost(bitmap.height - 1)
                    top..bottom
                }
            }
        } catch (_: Throwable) {
            emptyList()
        } finally {
            contours.forEach { it.release() }
            hierarchy.release()
            kernel.release()
            closed.release()
            edges.release()
            blur.release()
            gray.release()
            rgba.release()
        }
    }
}
