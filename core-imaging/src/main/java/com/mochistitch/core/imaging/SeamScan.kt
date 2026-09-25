package com.mochistitch.core.imaging

/**
 * Rencana potong satu halaman raksasa (v7 — Cropybara-inspired).
 *
 * @property cuts posisi potongan dalam koordinat piksel gambar yang dipindai,
 *   urut naik. Pemanggil mengonversi ke koordinat sumber.
 * @property tailSafe false bila sisa setelah potongan terakhir tidak punya
 *   celah aman (pemanggil membiarkannya utuh + flag).
 */
data class CutPlan(val cuts: List<Int>, val tailSafe: Boolean = true)

/**
 * Pindai sambungan/potongan aman v7 — terinspirasi Cropybara PixelComparisonDetector.
 *
 * Algoritma:
 * 1. Untuk setiap baris, cek selisih luminansi antar-piksel tetangga.
 *    Jika semua selisih < threshold → baris tersebut "aman" untuk dipotong.
 * 2. Threshold = floor(255 * (1 - sensitivity)), makin tinggi sensitivity
 *    makin ketat (hanya baris dengan perubahan sangat halus yang lolos).
 * 3. Cari baris aman dengan mencari ke atas dari titik ideal potong.
 * 4. Fallback ke titik ideal bila tak ada baris aman dalam jendela pencarian.
 *
 * Lebih sederhana dan lebih andal dari v6 karena:
 * - Tidak perlu estimasi kertas (paper-aware) yang bisa salah pada komik dengan background gelap.
 * - Tidak perlu cek vertikal terpisah; baris aman sudah memastikan perubahan horizontal minimal.
 * - Sensitivity memberi kontrol langsung: 0.9 = sangat ketat, 0.3 = longgar.
 */
object SeamScan {

    /**
     * Parameter deteksi potongan ala Cropybara PixelComparisonDetector.
     *
     * @param maxDistance jarak maksimal antar-potongan (seperti slice height).
     * @param sensitivity 0.0–1.0; makin tinggi makin ketat (hanya baris sangat homogen yang lolos).
     * @param margins piksel di tepi kiri-kanan yang diabaikan saat pengecekan.
     * @param step langkah pencocokan baris (semakin besar semakin cepat tapi kurang presisi).
     * @param maxSearchDeviationFactor faktor deviasi ke atas dari titik ideal sebelum fallback.
     */
    data class Config(
        val maxDistance: Int,
        val sensitivity: Float,
        val margins: Int,
        val step: Int,
        val maxSearchDeviationFactor: Float
    )

    /** Preset longgar: sensitivitas rendah, cocok untuk halaman dengan banyak variasi. */
    fun configLoose() = Config(
        maxDistance = 2000,
        sensitivity = 0.3f,
        margins = 10,
        step = 4,
        maxSearchDeviationFactor = 0.3f
    )

    /** Preset seimbang: rekomendasi default — sensitivitas sedang. */
    fun configBalanced() = Config(
        maxDistance = 1500,
        sensitivity = 0.5f,
        margins = 8,
        step = 3,
        maxSearchDeviationFactor = 0.4f
    )

    /** Preset akurat: sensitivitas tinggi, hanya potong di baris sangat homogen. */
    fun configStrict() = Config(
        maxDistance = 1000,
        sensitivity = 0.7f,
        margins = 6,
        step = 2,
        maxSearchDeviationFactor = 0.5f
    )

    // ── Util piksel ────────────────────────────────────────────────

    /** Luminansi perseptual 0..255 (heavyweight RGB → grayscale). */
    fun luminance(p: Int): Int {
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        return (r * 77 + g * 150 + b * 29) shr 8
    }

    private fun absI(v: Int): Int = if (v < 0) -v else v

    /**
     * Cek apakah satu baris piksel "aman" untuk dipotong.
     * Baris aman bila selisih luminansi antar-piksel tetangga < threshold.
     * Threshold = floor(255 * (1 - sensitivity)).
     */
    fun rowIsSafe(pixels: IntArray, offset: Int, length: Int, sensitivity: Float, margins: Int = 0): Boolean {
        if (length < 2) return true
        val threshold = (255.0 * (1.0 - sensitivity.coerceIn(0f, 1f))).toInt().coerceAtLeast(0)
        val start = margins.coerceAtLeast(0)
        val end = (length - margins).coerceAtMost(length)
        if (start + 1 >= end) return true
        var prev = luminance(pixels[offset + start])
        var i = start + 1
        while (i < end) {
            val cur = luminance(pixels[offset + i])
            if (absI(cur - prev) > threshold) return false
            prev = cur
            i++
        }
        return true
    }

    /**
     * Pindai semua baris dan kembalikan mask boolean: true = baris aman dipotong.
     * Mengambil sampel baris setiap [step] piksel untuk efisiensi.
     */
    fun scanSafeRows(
        getRow: (y: Int, out: IntArray) -> Unit,
        width: Int,
        height: Int,
        cfg: Config
    ): BooleanArray {
        val out = BooleanArray(height) { false }
        if (width <= 0 || height <= 0) return out
        val threshold = (255.0 * (1.0 - cfg.sensitivity.coerceIn(0f, 1f))).toInt().coerceAtLeast(0)
        val margin = cfg.margins.coerceAtLeast(0)
        val step = cfg.step.coerceAtLeast(1)
        val buf = IntArray(width)
        // Sample every `step` rows for efficiency
        var y = 0
        while (y < height) {
            getRow(y, buf)
            // Check if this row is safe
            var safe = true
            var prev = luminance(buf[margin])
            var x = margin + 1
            while (x < width - margin) {
                val cur = luminance(buf[x])
                if (absI(cur - prev) > threshold) {
                    safe = false
                    break
                }
                prev = cur
                x++
            }
            if (safe && width - margin * 2 > 0) {
                out[y] = true
            }
            y += step
        }
        // Fill in between sampled rows for continuity
        for (y in 1 until height) {
            if (!out[y] && y - 1 >= 0 && out[y - 1]) {
                // Check if adjacent row is also safe
                getRow(y, buf)
                var safe = true
                var prev = luminance(buf[margin])
                var x = margin + 1
                while (x < width - margin) {
                    val cur = luminance(buf[x])
                    if (absI(cur - prev) > threshold) {
                        safe = false
                        break
                    }
                    prev = cur
                    x++
                }
                if (safe) out[y] = true
            }
        }
        return out
    }

    /**
     * Rencana potongan v7 (Cropybara-style).
     * Cari baris aman dengan mencari ke atas dari titik ideal, dengan fallback.
     */
    fun planCuts(
        safe: BooleanArray,
        limit: Int,
        cfg: Config,
        overflow: Int = 0
    ): CutPlan {
        if (limit <= 0 || safe.size <= limit) return CutPlan(emptyList(), true)
        val maxSearchUp = (limit * cfg.maxSearchDeviationFactor).toInt().coerceAtLeast(1)
        val cuts = mutableListOf<Int>()
        var y = 0
        while (y + limit < safe.size) {
            val ideal = y + limit
            // Cari ke atas dari ideal sejauh maxSearchUp
            val searchStart = ideal
            val searchEnd = maxOf(y + 1, ideal - maxSearchUp)
            var foundCut: Int? = null
            for (yy in searchStart downTo searchEnd) {
                if (yy < safe.size && safe[yy]) {
                    foundCut = yy
                    break
                }
            }
            if (foundCut != null) {
                cuts.add(foundCut)
                y = foundCut
            } else if (overflow > 0) {
                // Fallback: perluas pencarian ke atas lagi
                val extendedEnd = maxOf(y + 1, ideal - maxSearchUp - overflow)
                for (yy in searchStart downTo extendedEnd) {
                    if (yy >= 0 && safe[yy]) {
                        cuts.add(yy)
                        y = yy
                        break
                    }
                }
                if (y == 0 && cuts.isEmpty()) return CutPlan(cuts, tailSafe = false)
            } else {
                // Fallback ke titik ideal
                if (ideal < safe.size) {
                    cuts.add(ideal)
                    y = ideal
                } else {
                    break
                }
            }
        }
        return CutPlan(cuts, tailSafe = true)
    }

    /**
     * true bila patch piksel mengandung konten (bukan latar datar): sebaran
     * kecerahan cukup besar. Dipakai agar margin putih-vs-putih tidak
     * disangka "bersambung".
     */
    fun hasContent(px: IntArray): Boolean {
        if (px.isEmpty()) return false
        val stride = maxOf(1, px.size / 512)
        var mn = Int.MAX_VALUE
        var mx = Int.MIN_VALUE
        var i = 0
        while (i < px.size) {
            val p = px[i]
            val s = ((p shr 16) and 0xFF) + ((p shr 8) and 0xFF) + (p and 0xFF)
            if (s < mn) mn = s
            if (s > mx) mx = s
            if (mx - mn > 64) return true
            i += stride
        }
        return mx - mn > 64
    }

    /**
     * Pembanding pasangan baris mentah (level rendah, untuk uji).
     * Untuk keputusan sambungan antar-halaman pakai [seamContinues] yang
     * hanya membandingkan baris-baris seam yang bersebelahan.
     */
    fun rowsContinue(
        a: IntArray,
        b: IntArray,
        tau: Int = 24,
        minFrac: Double = 0.9
    ): Boolean {
        if (a.isEmpty() || b.isEmpty()) return false
        val n = minOf(a.size, b.size)
        if (n == 0) return false
        val ao = (a.size - n) / 2
        val bo = (b.size - n) / 2
        val stride = maxOf(1, n / 512)
        var ok = 0
        var samples = 0
        var i = 0
        while (i < n) {
            if (pixelDelta(a[ao + i], b[bo + i]) <= tau) ok++
            samples++
            i += stride
        }
        return samples > 0 && ok.toDouble() / samples.toDouble() >= minFrac
    }

    /**
     * v7: uji kesinambungan SEAM yang benar. [bottom] = strip tepi bawah
     * halaman atas (widthB x hB, row-major), [top] = strip tepi atas halaman
     * bawah (widthT x hT). Hanya [seamRows] baris terakhir [bottom] vs
     * [seamRows] baris pertama [top] yang dibandingkan, kolom irisan tengah.
     */
    fun seamContinues(
        bottom: IntArray,
        bottomW: Int,
        bottomH: Int,
        top: IntArray,
        topW: Int,
        topH: Int,
        seamRows: Int = 4,
        tau: Int = 24,
        minFrac: Double = 0.9
    ): Boolean {
        if (bottom.isEmpty() || top.isEmpty()) return false
        if (bottomW <= 0 || bottomH <= 0 || topW <= 0 || topH <= 0) return false
        if (bottom.size < bottomW * bottomH || top.size < topW * topH) return false
        val rows = minOf(seamRows.coerceAtLeast(1), bottomH, topH)
        val w = minOf(bottomW, topW)
        if (w <= 0) return false
        val bOff = (bottomW - w) / 2
        val tOff = (topW - w) / 2
        val stride = maxOf(1, w / 256)
        var ok = 0
        var samples = 0
        for (r in 0 until rows) {
            val weight = if (r == 0) 2 else 1
            val bRow = (bottomH - 1 - r) * bottomW + bOff
            val tRow = r * topW + tOff
            var x = 0
            while (x < w) {
                repeat(weight) {
                    if (pixelDelta(bottom[bRow + x], top[tRow + x]) <= tau) ok++
                    samples++
                }
                x += stride
            }
        }
        return samples > 0 && ok.toDouble() / samples.toDouble() >= minFrac
    }

    private fun pixelDelta(a: Int, b: Int): Int {
        val dr = ((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)
        val dg = ((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)
        val db = (a and 0xFF) - (b and 0xFF)
        return absI(dr) + absI(dg) + absI(db)
    }

    // ── Metode kompatibilitas mundur untuk unit test ────────────────

    const val BAND_MARGIN = 4
    const val MIN_BAND = 16
    const val VERT_TAU = 20
    const val LINK_TAU = 24
    const val LINK_MIN_FRAC = 0.9
    const val CONTENT_SPREAD_TAU = 64
    const val PAPER_TAU = 48
    const val DARK_TAU = 40
    const val SEAM_ROWS = 4

    /** Versi lama rowIsSafe tanpa parameter cfg — pakai default sensitivity 0.5. */
    fun rowIsSafe(pixels: IntArray): Boolean = rowIsSafe(pixels, 0, pixels.size, 0.5f)

    /** Versi lama rowIsSafe dengan offset/length. */
    fun rowIsSafe(pixels: IntArray, offset: Int, length: Int): Boolean =
        rowIsSafe(pixels, offset, length, 0.5f)

    /** Versi lama rowIsSafe dengan sensitivity. */
    fun rowIsSafe(pixels: IntArray, offset: Int, length: Int, sensitivity: Float): Boolean {
        if (length < 2) return true
        return rowIsSafe(pixels, offset, length, sensitivity, 0)
    }

    /** rowsVertSafe sederhana untuk test — tandai baris yang beda vertikal > tau. */
    fun rowsVertSafe(
        width: Int,
        height: Int,
        getRow: (y: Int, out: IntArray) -> Unit
    ): BooleanArray {
        return rowsVertSafe(width, height, getRow, Config(
            maxDistance = 1500, sensitivity = 0.5f, margins = 8,
            step = 3, maxSearchDeviationFactor = 0.4f
        ))
    }

    fun rowsVertSafe(
        width: Int,
        height: Int,
        getRow: (y: Int, out: IntArray) -> Unit,
        cfg: Config
    ): BooleanArray {
        val out = BooleanArray(height) { true }
        if (width <= 0 || height <= 0) return out
        val prev = IntArray(width)
        val cur = IntArray(width)
        val prevLum = IntArray(width)
        getRow(0, cur)
        for (x in 0 until width) prevLum[x] = luminance(cur[x])
        System.arraycopy(cur, 0, prev, 0, width)
        // Gunakan vertTau = 20 (default v6) untuk backward compat
        val vertTau = 20
        for (y in 1 until height) {
            getRow(y, cur)
            for (x in 0 until width) {
                val l = luminance(cur[x])
                if (absI(l - prevLum[x]) > vertTau) {
                    out[y] = false
                    out[y - 1] = false
                }
                prevLum[x] = l
            }
            System.arraycopy(cur, 0, prev, 0, width)
        }
        return out
    }

    /** findBands dengan minBand — pakai config default. */
    fun findBands(safe: BooleanArray, minBand: Int = MIN_BAND): List<IntRange> {
        val band = minBand.coerceAtLeast(1)
        val out = mutableListOf<IntRange>()
        var start = -1
        for (i in safe.indices) {
            if (safe[i]) {
                if (start < 0) start = i
            } else {
                if (start >= 0 && i - start >= band) out.add(start until i)
                start = -1
            }
        }
        if (start >= 0 && safe.size - start >= band) out.add(start until safe.size)
        return out
    }

    /** planCuts dengan parameter lama — pakai config default. */
    fun planCuts(safe: BooleanArray, limit: Int, minChunk: Int = limit / 2, overflow: Int = 0): CutPlan {
        val cfg = Config(
            maxDistance = limit, sensitivity = 0.5f, margins = 8,
            step = 3, maxSearchDeviationFactor = 0.4f
        )
        return planCuts(safe, limit, cfg, overflow = overflow)
    }

    fun planCuts(safe: BooleanArray, limit: Int, cfg: Config, minChunk: Int = limit / 2, overflow: Int = 0): CutPlan {
        if (limit <= 0 || safe.size <= limit) return CutPlan(emptyList(), true)
        val minC = minChunk.coerceAtLeast(1)
        val maxSearchUp = (limit * cfg.maxSearchDeviationFactor).toInt().coerceAtLeast(1)
        val cuts = mutableListOf<Int>()
        var y = 0
        while (y + limit < safe.size) {
            val ideal = y + limit
            val searchStart = ideal
            val searchEnd = maxOf(y + 1, ideal - maxSearchUp)
            var foundCut: Int? = null
            for (yy in searchStart downTo searchEnd) {
                if (yy < safe.size && safe[yy]) {
                    foundCut = yy
                    break
                }
            }
            if (foundCut != null) {
                cuts.add(foundCut)
                y = foundCut
            } else if (overflow > 0) {
                val extendedEnd = maxOf(y + 1, ideal - maxSearchUp - overflow)
                for (yy in searchStart downTo extendedEnd) {
                    if (yy >= 0 && safe[yy]) {
                        cuts.add(yy)
                        y = yy
                        break
                    }
                }
                if (y == 0 && cuts.isEmpty()) return CutPlan(cuts, tailSafe = false)
            } else {
                if (ideal < safe.size) {
                    cuts.add(ideal)
                    y = ideal
                } else {
                    break
                }
            }
        }
        return CutPlan(cuts, tailSafe = true)
    }

    /** estimatePaper untuk backward compat — tidak dipakai di v7 tapi test mungkin butuh. */
    fun estimatePaper(rows: List<IntArray>): Int {
        if (rows.isEmpty()) return 255
        val hist = IntArray(16)
        for (row in rows) {
            if (row.isEmpty()) continue
            val stride = maxOf(1, row.size / 64)
            var i = 0
            while (i < row.size) {
                hist[((luminance(row[i]) * 16) shr 8).coerceIn(0, 15)]++
                i += stride
            }
        }
        var best = 15
        var bestCount = -1
        for (b in 15 downTo 0) {
            val boosted = if (b >= 12) hist[b] * 2 else hist[b]
            if (boosted > bestCount) {
                bestCount = boosted
                best = b
            }
        }
        return (best * 16 + 8).coerceIn(0, 255)
    }

    /** rowMedianLum untuk backward compat. */
    fun rowMedianLum(pixels: IntArray, offset: Int, length: Int): Int {
        if (length <= 0) return 255
        val stride = maxOf(1, length / 256)
        var count = 0
        val buf = IntArray((length + stride - 1) / stride)
        var i = offset
        val end = offset + length
        while (i < end) {
            buf[count++] = luminance(pixels[i])
            i += stride
        }
        buf.sort(0, count)
        return buf[count / 2]
    }

    /** rowMaxStep untuk backward compat. */
    fun rowMaxStep(pixels: IntArray, offset: Int, length: Int): Int {
        if (length < 2) return 0
        var prev = luminance(pixels[offset])
        var mx = 0
        val end = offset + length
        var i = offset + 1
        while (i < end) {
            val cur = luminance(pixels[i])
            val d = absI(cur - prev)
            if (d > mx) mx = d
            prev = cur
            i++
        }
        return mx
    }

    /** combineSafe untuk backward compat. */
    fun combineSafe(horiz: BooleanArray, vert: BooleanArray): BooleanArray {
        require(horiz.size == vert.size)
        return BooleanArray(horiz.size) { i -> horiz[i] && vert[i] }
    }

    /** rowIsSafe dengan paperLum untuk backward compat — ignore paperLum di v7. */
    fun rowIsSafe(pixels: IntArray, offset: Int, length: Int, paperLum: Int): Boolean {
        return rowIsSafe(pixels, offset, length, 0.5f)
    }

    fun rowIsSafe(pixels: IntArray, offset: Int, length: Int, paperLum: Int, sensitivity: Float): Boolean {
        return rowIsSafe(pixels, offset, length, sensitivity)
    }

    /** Config dengan parameter v6 untuk backward compat. */
    data class V6Config(
        val edgeTau: Int,
        val vertTau: Int,
        val minBand: Int,
        val bandMargin: Int,
        val darkTau: Int,
        val paperTau: Int
    )
}
