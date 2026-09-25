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
}
