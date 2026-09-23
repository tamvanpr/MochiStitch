package com.mochistitch.core.imaging

/**
 * Rencana potong satu halaman raksasa.
 *
 * @property cuts posisi potongan dalam koordinat piksel gambar yang dipindai,
 *   urut naik. Pemanggil mengonversi ke koordinat sumber.
 * @property tailSafe false bila sisa setelah potongan terakhir tidak punya
 *   celah aman (pemanggil membiarkannya utuh + flag).
 */
data class CutPlan(val cuts: List<Int>, val tailSafe: Boolean = true)

/**
 * Pindai sambungan/ potongan aman TANPA OpenCV/ML, murni Kotlin:
 *
 * - Baris bebas-tepi tidak mungkin melintasi tinta balon, panel, atau teks,
 *   sehingga garis potong HANYA boleh jatuh di tengah pita baris aman.
 * - Dua halaman berurutan dinyatakan bersambung bila deret piksel tepi
 *   bawah halaman atas berlanjut mulus ke tepi atas halaman bawah.
 *
 * Semua fungsi bekerja pada array murni agar bisa diuji di JVM.
 */
object SeamScan {

    /** Ambang tepi: jumlah |ΔR|+|ΔG|+|ΔB|+|ΔA| pasangan piksel tetangga. */
    const val EDGE_TAU = 32

    /** Pita baris aman minimal agar layak jadi celah potong. */
    const val MIN_BAND = 8

    /** Ambang sambungan tepi antar-halaman. */
    const val LINK_TAU = 24

    /** Fraksi pasangan piksel harus cocok agar dinyatakan bersambung. */
    const val LINK_MIN_FRAC = 0.9

    /** Baris [pixels] (satu baris ARGB) aman dipotong: tak ada tepi berarti. */
    fun rowIsSafe(pixels: IntArray): Boolean = rowIsSafe(pixels, 0, pixels.size)

    fun rowIsSafe(pixels: IntArray, offset: Int, length: Int): Boolean {
        if (length < 2) return true
        var prev = pixels[offset]
        val end = offset + length
        var i = offset + 1
        while (i < end) {
            val cur = pixels[i]
            if (pixelDelta(prev, cur) > EDGE_TAU) return false
            prev = cur
            i++
        }
        return true
    }

    private fun pixelDelta(a: Int, b: Int): Int {
        val dr = ((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)
        val dg = ((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)
        val db = (a and 0xFF) - (b and 0xFF)
        val da = ((a ushr 24) and 0xFF) - ((b ushr 24) and 0xFF)
        return absI(dr) + absI(dg) + absI(db) + absI(da)
    }

    private fun absI(v: Int): Int = if (v < 0) -v else v

    /** Pita baris berurutan yang seluruhnya aman, panjang ≥ [minBand]. */
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

    /**
     * Rencana potongan untuk [safe] sepanjang [safe.size] baris dengan batas
     * tinggi [limit]. Potongan dipilih sebagai titik di dalam celah yang
     * terdekat ke batas, dalam jendela [minChunk, limit] — tidak pernah di
     * luar batas, tidak pernah di baris bertepi.
     */
    fun planCuts(
        safe: BooleanArray,
        limit: Int,
        minBand: Int = MIN_BAND,
        minChunk: Int = limit / 2
    ): CutPlan {
        if (limit <= 0 || safe.size <= limit) return CutPlan(emptyList(), true)
        val minC = minChunk.coerceAtLeast(1)
        val bands = findBands(safe, minBand)
        val cuts = mutableListOf<Int>()
        var y = 0
        while (safe.size - y > limit) {
            val lo = y + minC
            val hi = y + limit
            var best: Int? = null
            var bestDist = Int.MAX_VALUE
            for (band in bands) {
                val oLo = maxOf(lo, band.first)
                val oHi = minOf(hi, band.last)
                if (oLo > oHi) continue
                val cand = hi.coerceIn(oLo, oHi)
                val dist = absI(cand - hi)
                if (dist < bestDist) {
                    bestDist = dist
                    best = cand
                }
            }
            if (best == null) return CutPlan(cuts, tailSafe = false)
            cuts.add(best)
            y = best
        }
        return CutPlan(cuts, tailSafe = true)
    }
        return CutPlan(cuts, tailSafe = true)
    }

    /**
     * true bila deret piksel [a] (tepi bawah halaman atas) bersambung mulus
     * ke [b] (tepi atas halaman bawah). Lebar boleh beda: dibandingkan
     * sepanjang irisan tengah.
     */
    fun rowsContinue(
        a: IntArray,
        b: IntArray,
        tau: Int = LINK_TAU,
        minFrac: Double = LINK_MIN_FRAC
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
}
