package com.mochistitch.core.imaging

/**
 * Template-matching banner murni Kotlin (tanpa OpenCV).
 *
 * Ini port ide bannercut2 (matchTemplate terhadap banner yang dikenal)
 * tanpa mesin OpenCV: strip halaman + template sama-sama diskalakan ke
 * grid luminansi kecil ([GW]×[GH]) lalu dibandingkan dengan Normalized
 * Cross-Correlation (ekuivalen praktis TM_CCOEFF_NORMED untuk kasus ini).
 *
 * Keunggulan vs gerbang konsistensi: keputusan PER HALAMAN (tahan chapter
 * 1-2 halaman dan banner campuran), deterministik terhadap 4 banner yang
 * sudah dikenal. Gerbang konsistensi tetap dipakai sebagai pendamping
 * untuk banner yang belum dikenal.
 */
object BannerTemplate {

    const val GW = 96
    const val GH = 16

    /** Skor NCC ≥ ini = banner pasti (langsung crop). */
    const val STRONG = 0.90

    /** Skor ≥ ini = kandidat, crop hanya bila gerbang konsistensi setuju. */
    const val MEDIUM = 0.70

    /** Signature template: grid luminansi [GW]×[GH]. */
    data class Sig(val lum: IntArray) {
        init {
            require(lum.size == GW * GH)
        }
    }

    /** ARGB -> grid luminansi [GW]×[GH] (bilinear, satu pass). */
    fun downscale(px: IntArray, w: Int, h: Int): IntArray {
        require(px.size >= w * h && w > 0 && h > 0)
        val out = IntArray(GW * GH)
        for (gy in 0 until GH) {
            val sy = (gy + 0.5f) * h / GH - 0.5f
            val y0 = sy.toInt().coerceIn(0, h - 1)
            val y1 = (y0 + 1).coerceIn(0, h - 1)
            val fy = (sy - y0).coerceIn(0f, 1f)
            for (gx in 0 until GW) {
                val sx = (gx + 0.5f) * w / GW - 0.5f
                val x0 = sx.toInt().coerceIn(0, w - 1)
                val x1 = (x0 + 1).coerceIn(0, w - 1)
                val fx = (sx - x0).coerceIn(0f, 1f)
                val a = lumOf(px[y0 * w + x0])
                val b = lumOf(px[y0 * w + x1])
                val c = lumOf(px[y1 * w + x0])
                val d = lumOf(px[y1 * w + x1])
                out[gy * GW + gx] = ((a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy).toInt()
            }
        }
        return out
    }

    private fun lumOf(p: Int): Float {
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        return (r * 77 + g * 150 + b * 29) / 256f
    }

    /**
     * NCC zero-mean [-1, 1]. Strip/template datar (varians 0) -> 0
     * (tidak pernah cocok; arah aman).
     */
    fun ncc(a: IntArray, b: IntArray): Double {
        require(a.size == b.size && a.isNotEmpty())
        val n = a.size
        var ma = 0.0
        var mb = 0.0
        for (i in 0 until n) {
            ma += a[i]
            mb += b[i]
        }
        ma /= n
        mb /= n
        var sab = 0.0
        var saa = 0.0
        var sbb = 0.0
        for (i in 0 until n) {
            val da = a[i] - ma
            val db = b[i] - mb
            sab += da * db
            saa += da * da
            sbb += db * db
        }
        if (saa <= 0.0 || sbb <= 0.0) return 0.0
        return (sab / kotlin.math.sqrt(saa * sbb)).coerceIn(-1.0, 1.0)
    }

    /** Skor terbaik strip (grid) terhadap semua template. */
    fun bestScore(stripGrid: IntArray, templates: List<Sig>): Double {
        var best = 0.0
        for (t in templates) {
            val s = ncc(stripGrid, t.lum)
            if (s > best) best = s
        }
        return best
    }
}
