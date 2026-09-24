package com.mochistitch.core.imaging

import android.graphics.Bitmap
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * Template-matching banner via OpenCV (port setia bannercut2/index.html).
 *
 * Alur per sisi halaman: region 200px -> gray -> blur+Canny(45,135) +
 * dark-mask; tiap region di-resize ke ukuran template, lalu 5 sinyal:
 * mask/absdiff (0.25) + gray NCC (0.30) + edge NCC (0.18) + dark F1/IoU
 * (0.25) + diff (0.02), dikali keyakinan lebar. Keputusan:
 * normalMatch (skor ≥ 0.58 + bukti bentuk + widthDiff ≤ 0.50),
 * strongText, atau veryHigh — sama persis dengan web.
 *
 * Bila OpenCV gagal init (exception/linkage), pemanggil WAJIB fallback ke
 * [BannerTemplate] NCC murni — lihat [isAvailable].
 */
object BannerOcv {

    const val MATCH_THRESHOLD = 0.58
    const val WIDTH_MATCH_TOLERANCE = 0.25

    @Volatile
    private var available: Boolean? = null

    /** true bila native OpenCV siap dipakai (sekali per proses). */
    fun isAvailable(): Boolean {
        available?.let { return it }
        val ok = try {
            // Artefak 4.5.3 (2021) belum punya initLocal(); initDebug()
            // memuat .so yang dibundel AAR — API yang tepat untuk versi ini.
            OpenCVLoader.initDebug()
        } catch (t: Throwable) {
            false
        }
        available = ok
        return ok
    }

    data class Tmpl(
        val id: String,
        val width: Int,
        val height: Int,
        val raw: Mat,
        val gray: Mat,
        val edge: Mat,
        val dark: Mat,
        val mask: Mat,
        val maskPixels: Int,
        val edgePixels: Int,
        val darkPixels: Int
    )

    data class Region(
        val width: Int,
        val raw: Mat,
        val edge: Mat,
        val dark: Mat,
        val edgePixels: Int,
        val darkPixels: Int
    )

    data class Scored(
        val score: Double,
        val grayScore: Double,
        val edgeScore: Double,
        val diffScore: Double,
        val maskScore: Double,
        val darkScore: Double,
        val widthDiff: Double,
        val templateId: String
    )

    /** Preproses satu template dari bitmap aset (sekali per build). */
    fun preprocessTemplate(id: String, bmp: Bitmap): Tmpl? {
        val safe = if (bmp.config == Bitmap.Config.ARGB_8888 && bmp.isMutable) bmp
        else bmp.copy(Bitmap.Config.ARGB_8888, true) ?: return null
        val rgba = Mat()
        val grayRaw = Mat()
        try {
            Utils.bitmapToMat(safe, rgba)
            Imgproc.cvtColor(rgba, grayRaw, Imgproc.COLOR_RGBA2GRAY)
        } finally {
            rgba.release()
        }
        val w = grayRaw.cols()
        val h = grayRaw.rows()
        if (w <= 0 || h <= 0) {
            grayRaw.release()
            return null
        }
        // Sama seperti web: resize no-op ke ukuran sendiri + equalize + edge + dark.
        val raw = Mat()
        val gray = Mat()
        val blurred = Mat()
        val edge = Mat()
        val dark = Mat()
        try {
            Imgproc.resize(grayRaw, raw, Size(w.toDouble(), h.toDouble()), 0.0, 0.0, Imgproc.INTER_AREA)
            Imgproc.equalizeHist(raw, gray)
            Imgproc.GaussianBlur(gray, blurred, Size(3.0, 3.0), 0.0)
            Imgproc.Canny(blurred, edge, 45.0, 135.0)
            darkMaskInto(raw, dark)
        } finally {
            grayRaw.release()
            blurred.release()
        }
        val mask = templateMask(raw)
        val maskPixels = Core.countNonZero(mask)
        if (maskPixels < 250) {
            raw.release(); gray.release(); edge.release(); dark.release(); mask.release()
            return null
        }
        return Tmpl(
            id = id, width = w, height = h,
            raw = raw, gray = gray, edge = edge, dark = dark, mask = mask,
            maskPixels = maskPixels,
            edgePixels = Core.countNonZero(edge),
            darkPixels = Core.countNonZero(dark)
        )
    }

    fun releaseTemplate(t: Tmpl) {
        t.raw.release(); t.gray.release(); t.edge.release(); t.dark.release(); t.mask.release()
    }

    /** Siapkan region strip halaman dari bitmap strip penuh (lebar × 200). */
    fun preprocessRegion(stripBmp: Bitmap): Region? {
        val safe = if (stripBmp.config == Bitmap.Config.ARGB_8888 && stripBmp.isMutable) stripBmp
        else stripBmp.copy(Bitmap.Config.ARGB_8888, true) ?: return null
        val rgba = Mat()
        val grayRaw = Mat()
        try {
            Utils.bitmapToMat(safe, rgba)
            Imgproc.cvtColor(rgba, grayRaw, Imgproc.COLOR_RGBA2GRAY)
        } finally {
            rgba.release()
        }
        if (grayRaw.cols() <= 0 || grayRaw.rows() <= 0) {
            grayRaw.release()
            return null
        }
        val blurred = Mat()
        val edge = Mat()
        val dark = Mat()
        try {
            Imgproc.GaussianBlur(grayRaw, blurred, Size(3.0, 3.0), 0.0)
            Imgproc.Canny(blurred, edge, 45.0, 135.0)
            darkMaskInto(grayRaw, dark)
        } finally {
            blurred.release()
        }
        val out = Region(
            width = grayRaw.cols(), raw = grayRaw, edge = edge, dark = dark,
            edgePixels = Core.countNonZero(edge),
            darkPixels = Core.countNonZero(dark)
        )
        return out
    }

    fun releaseRegion(r: Region) {
        r.raw.release(); r.edge.release(); r.dark.release()
    }

    /**
     * Skor terbaik region terhadap semua template + keputusan ala web.
     * @return Triple(isBanner, skorTerbaik, idTemplate)
     */
    fun check(region: Region, templates: List<Tmpl>): Triple<Boolean, Double, String> {
        var best = Scored(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, Double.MAX_VALUE, "-")
        for (t in templates) {
            val s = scoreAgainst(region, t)
            if (s.score > best.score) best = s
        }
        val normalMatch = best.score >= MATCH_THRESHOLD &&
            (best.grayScore >= 0.30 || best.edgeScore >= 0.18 || best.darkScore >= 0.28) &&
            best.widthDiff <= 0.50
        val strongText = best.darkScore >= 0.42 &&
            (best.grayScore >= 0.22 || best.edgeScore >= 0.12 || best.maskScore >= 0.88)
        val veryHigh = best.maskScore >= 0.93 && best.grayScore >= 0.45
        return Triple(normalMatch || strongText || veryHigh, best.score, best.templateId)
    }

    private fun scoreAgainst(region: Region, t: Tmpl): Scored {
        val widthDiff = kotlin.math.abs(region.width - t.width).toDouble() / region.width.toDouble()
        val widthConfidence = when {
            widthDiff <= WIDTH_MATCH_TOLERANCE -> 1.0
            widthDiff <= 0.50 -> 0.7
            else -> 0.4
        }
        val resizedRaw = Mat()
        val resizedGray = Mat()
        val resizedEdge = Mat()
        val resizedDark = Mat()
        try {
            Imgproc.resize(region.raw, resizedRaw, Size(t.width.toDouble(), t.height.toDouble()), 0.0, 0.0, Imgproc.INTER_AREA)
            Imgproc.equalizeHist(resizedRaw, resizedGray)
            Imgproc.resize(region.edge, resizedEdge, Size(t.width.toDouble(), t.height.toDouble()), 0.0, 0.0, Imgproc.INTER_NEAREST)
            Imgproc.resize(region.dark, resizedDark, Size(t.width.toDouble(), t.height.toDouble()), 0.0, 0.0, Imgproc.INTER_NEAREST)
            val maskScore = maskedMeanAbsSimilarity(resizedRaw, t.raw, t.mask)
            val grayScore = matchSameSize(resizedGray, t.gray)
            val diffScore = meanAbsSimilarity(resizedRaw, t.raw)
            var edgeScore = 0.0
            if (region.edgePixels > 60 && t.edgePixels > 60) edgeScore = matchSameSize(resizedEdge, t.edge)
            var darkScore = 0.0
            if (region.darkPixels > 20 && t.darkPixels > 20) {
                darkScore = darkMaskSimilarity(resizedDark, t.dark, region.darkPixels, t.darkPixels)
            }
            val combined = maskScore * 0.25 + grayScore * 0.30 + edgeScore * 0.18 + darkScore * 0.25 + diffScore * 0.02
            return Scored(
                score = combined * widthConfidence,
                grayScore = grayScore, edgeScore = edgeScore, diffScore = diffScore,
                maskScore = maskScore, darkScore = darkScore,
                widthDiff = widthDiff, templateId = t.id
            )
        } finally {
            resizedRaw.release(); resizedGray.release(); resizedEdge.release(); resizedDark.release()
        }
    }

    private fun matchSameSize(src: Mat, templ: Mat): Double {
        val result = Mat()
        try {
            Imgproc.matchTemplate(src, templ, result, Imgproc.TM_CCOEFF_NORMED)
            return Core.minMaxLoc(result).maxVal.coerceIn(0.0, 1.0)
        } finally {
            result.release()
        }
    }

    private fun meanAbsSimilarity(src: Mat, templ: Mat): Double {
        val diff = Mat()
        try {
            Core.absdiff(src, templ, diff)
            val mean = Core.mean(diff).`val`[0]
            return (1.0 - mean / 255.0).coerceIn(0.0, 1.0)
        } finally {
            diff.release()
        }
    }

    private fun maskedMeanAbsSimilarity(src: Mat, templ: Mat, mask: Mat): Double {
        val diff = Mat()
        try {
            Core.absdiff(src, templ, diff)
            val mean = Core.mean(diff, mask).`val`[0]
            return (1.0 - mean / 255.0).coerceIn(0.0, 1.0)
        } finally {
            diff.release()
        }
    }

    private fun darkMaskSimilarity(regionDark: Mat, templateDark: Mat, regionPx: Int, templatePx: Int): Double {
        if (regionPx <= 0 || templatePx <= 0) return 0.0
        val inter = Mat()
        val union = Mat()
        try {
            Core.bitwise_and(regionDark, templateDark, inter)
            Core.bitwise_or(regionDark, templateDark, union)
            val interCount = Core.countNonZero(inter).toDouble()
            val unionCount = Core.countNonZero(union).toDouble()
            val recall = interCount / templatePx
            val precision = interCount / regionPx
            val iou = if (unionCount > 0) interCount / unionCount else 0.0
            val f1 = if (precision + recall > 0) 2 * precision * recall / (precision + recall) else 0.0
            return (f1 * 0.75 + iou * 0.25).coerceIn(0.0, 1.0)
        } finally {
            inter.release(); union.release()
        }
    }

    private fun darkMaskInto(raw: Mat, dark: Mat) {
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        try {
            Imgproc.threshold(raw, dark, 135.0, 255.0, Imgproc.THRESH_BINARY_INV)
            Imgproc.morphologyEx(dark, dark, Imgproc.MORPH_OPEN, kernel)
            Imgproc.dilate(dark, dark, kernel)
        } finally {
            kernel.release()
        }
    }

    private fun templateMask(raw: Mat): Mat {
        val mask = Mat()
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
        try {
            Imgproc.threshold(raw, mask, 246.0, 255.0, Imgproc.THRESH_BINARY_INV)
            Imgproc.dilate(mask, mask, kernel)
        } finally {
            kernel.release()
        }
        return mask
    }

    /** Bitmap ARGB dari IntArray piksel (untuk strip hasil edgePatch). */
    fun bitmapOf(px: IntArray, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.setPixels(px, 0, w, 0, 0, w, h)
        return bmp
    }

    @Suppress("unused")
    private fun grayVariance(gray: Mat): Double {
        val mean = MatOfDouble()
        val std = MatOfDouble()
        try {
            Core.meanStdDev(gray, mean, std)
            val s = std.get(0, 0)?.get(0) ?: 0.0
            return s * s
        } finally {
            mean.release(); std.release()
        }
    }
}
