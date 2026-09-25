package com.mochistitch.core.imaging

/**
 * Rencana potong satu halaman raksasa (v6).
 *
 * @property cuts posisi potongan dalam koordinat piksel gambar yang dipindai,
 *   urut naik. Pemanggil mengonversi ke koordinat sumber.
 * @property tailSafe false bila sisa setelah potongan terakhir tidak punya
 *   celah aman (pemanggil membiarkannya utuh + flag).
 */
data class CutPlan(val cuts: List<Int>, val tailSafe: Boolean = true)

/**
 * Pindai sambungan/potongan aman v6 — TANPA OpenCV/ML, murni Kotlin.
 *
 * Rombak total dari v5. Akar masalah "potong di area yang tidak seharusnya":
 *
 * 1. v5 hanya cek beda HORIZONTAL dalam satu baris. Baris hitam merata
 *    (panel border horizontal, balok hitam) lolos sebagai "aman" karena
 *    tidak ada variasi horizontal — padahal itu tinta penuh. v6 menolak
 *    baris yang luminansinya jauh dari kertas (paper-aware).
 * 2. v5 tidak cek beda VERTIKAL antar-baris. Garis horizontal tipis
 *    (border panel 1-2px) hanya terlihat sebagai beda antar-baris, bukan
 *    dalam-baris. v6 menandai kedua baris di sisi tepi vertikal.
 * 3. v5 memotong di TEPI pita aman (1px dari tinta). Geser rounding saat
 *    mapping decode->sumber lalu mengiris tinta. v6 memotong di TENGAH
 *    pita dengan margin dari kedua sisi.
 * 4. v5 continuityMap membandingkan patch 48px secara offset-penuh
 *    (piksel dalam vs piksel dalam, bukan piksel seam vs seam) sehingga
 *    pinning halaman bersambung hampir tidak pernah tepat. v6 hanya
 *    membandingkan baris-baris yang bersebelahan di seam.
 *
 * Semua fungsi bekerja pada array murni agar bisa diuji di JVM.
 */
object SeamScan {

    /** Konfigurasi ketegasan potong sesuai preset [com.mochistitch.core.settings.CutStrictness]. */
    data class Config(
        val edgeTau: Int,
        val vertTau: Int,
        val minBand: Int,
        val bandMargin: Int,
        val darkTau: Int,
        val paperTau: Int
    )

    /** Preset longgar: minim potongan, paling aman dari salah tebas. */
    fun configLoose() = Config(edgeTau = 8, vertTau = 6, minBand = 40, bandMargin = 12, darkTau = 16, paperTau = 20)

    /** Preset seimbang: rekomendasi default — sangat konservatif. */
    fun configBalanced() = Config(edgeTau = 10, vertTau = 8, minBand = 50, bandMargin = 16, darkTau = 20, paperTau = 24)

    /** Preset akurat: potong lebih sering, threshold ketat. */
    fun configStrict() = Config(edgeTau = 14, vertTau = 10, minBand = 60, bandMargin = 20, darkTau = 28, paperTau = 32)

    /** Ambang tepi horizontal: langkah luminansi antar piksel tetangga. */
    const val EDGE_TAU = 24

    /** Ambang tepi vertikal: langkah luminansi antar baris (kolom sama). */
    const val VERT_TAU = 20

    /** Pita baris aman minimal agar layak jadi celah potong. */
    const val MIN_BAND = 16

    /** Margin dari sisi pita: potongan tidak boleh lebih dekat dari ini ke tinta. */
    const val BAND_MARGIN = 4

    /** Ambang sambungan tepi antar-halaman (per piksel seam). */
    const val LINK_TAU = 24

    /** Fraksi pasangan piksel seam harus cocok agar dinyatakan bersambung. */
    const val LINK_MIN_FRAC = 0.9

    /** Sebaran |ΣRGB| minimal agar patch dianggap berisi konten. */
    const val CONTENT_SPREAD_TAU = 64

    /** Jarak luminansi median baris dari kertas agar masih dianggap kertas. */
    const val PAPER_TAU = 48

    /** Di bawah luminansi ini baris dianggap tinta merata (balok/border). */
    const val DARK_TAU = 40

    /** Jumlah baris seam yang dibandingkan saat uji kesinambungan. */
    const val SEAM_ROWS = 4

    // ── Util piksel ────────────────────────────────────────────────

    /** Luminansi perseptual 0..255. */
    fun luminance(p: Int): Int {
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        // 0.299R + 0.587G + 0.114B, integer.
        return (r * 77 + g * 150 + b * 29) shr 8
    }

    private fun absI(v: Int): Int = if (v < 0) -v else v

    private fun pixelDelta(a: Int, b: Int): Int {
        val dr = ((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)
        val dg = ((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)
        val db = (a and 0xFF) - (b and 0xFF)
        val da = ((a ushr 24) and 0xFF) - ((b ushr 24) and 0xFF)
        return absI(dr) + absI(dg) + absI(db) + absI(da)
    }

    /** Median luminansi baris (sampling agar murah). */
    fun rowMedianLum(pixels: IntArray, offset: Int, length: Int): Int {
        if (length <= 0) return 255
        // Sampling maks ~256 titik untuk median murah.
        val stride = maxOf(1, length / 256)
        var count = 0
        // Kumpulkan ke buffer kecil lalu sort parsial via insertion ke array.
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

    /** Langkah horizontal maksimum dalam baris (skala luminansi). */
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

    /**
     * Estimasi luminansi kertas dari sampel baris: ambil nilai terang yang
     * dominan (komik umumnya berlatar terang). Pure-array agar testable.
     */
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
        // Cari bin terang dengan hit terbanyak (atas 4 bin diprioritaskan).
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

    // ── Uji baris ──────────────────────────────────────────────────

    /**
     * Baris aman dipotong bila: tak ada langkah horizontal berarti, median
     * dekat kertas, dan tidak gelap merata. Tanpa konteks vertikal, baris
     * border-horizontal 1px yang SAMA PERSIS dengan tetangganya tetap bisa
     * lolos di sini — karena itu pemanggil WAJIB menggabungkan dengan
     * [rowsVertSafe] (cek vertikal). Overload tanpa paper memakai aturan
     * konservatif: tolak baris gelap merata saja.
     */
    fun rowIsSafe(pixels: IntArray): Boolean = rowIsSafe(pixels, 0, pixels.size)

    fun rowIsSafe(pixels: IntArray, offset: Int, length: Int): Boolean {
        if (length < 2) return true
        if (rowMaxStep(pixels, offset, length) > EDGE_TAU) return false
        val med = rowMedianLum(pixels, offset, length)
        // Baris gelap merata = tinta penuh (balok hitam / border tebal).
        if (med < DARK_TAU) return false
        return true
    }

    /** Varian dengan konfigurasi ketegasan dari preset [CutStrictness]. */
    fun rowIsSafe(pixels: IntArray, offset: Int, length: Int, cfg: Config): Boolean {
        if (length < 2) return true
        if (rowMaxStep(pixels, offset, length) > cfg.edgeTau) return false
        val med = rowMedianLum(pixels, offset, length)
        if (med < cfg.darkTau) return false
        return true
    }

    /** Varian paper-aware: baris juga harus dekat luminansi kertas. */
    fun rowIsSafe(pixels: IntArray, offset: Int, length: Int, paperLum: Int): Boolean {
        if (!rowIsSafe(pixels, offset, length)) return false
        val med = rowMedianLum(pixels, offset, length)
        return absI(med - paperLum) <= PAPER_TAU
    }

    /** Varian paper-aware + konfigurasi ketegasan. */
    fun rowIsSafe(pixels: IntArray, offset: Int, length: Int, paperLum: Int, cfg: Config): Boolean {
        if (!rowIsSafe(pixels, offset, length, cfg)) return false
        val med = rowMedianLum(pixels, offset, length)
        return absI(med - paperLum) <= cfg.paperTau
    }

    /**
     * Masker aman vertikal: baris y tidak aman bila beda terhadap baris
     * atas ATAU bawah melebihi [VERT_TAU] di kolom mana pun (border
     * horizontal / tepi balon mendatar terdeteksi di sini).
     *
     * @param getRow mengisi [out] dengan piksel baris y (panjang = width).
     * @return BooleanArray aman-vertikal sepanjang height.
     */
    fun rowsVertSafe(
        width: Int,
        height: Int,
        getRow: (y: Int, out: IntArray) -> Unit,
        tau: Int = VERT_TAU
    ): BooleanArray {
        return rowsVertSafe(width, height, getRow, Config(edgeTau = 24, vertTau = tau, minBand = 16, bandMargin = 4, darkTau = 40, paperTau = 48))
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
        for (y in 1 until height) {
            getRow(y, cur)
            for (x in 0 until width) {
                val l = luminance(cur[x])
                if (absI(l - prevLum[x]) > cfg.vertTau) {
                    out[y] = false
                    out[y - 1] = false
                }
                prevLum[x] = l
            }
            System.arraycopy(cur, 0, prev, 0, width)
        }
        return out
    }

    /** Gabung masker horizontal & vertikal (+paper bila ada) jadi satu. */
    fun combineSafe(horiz: BooleanArray, vert: BooleanArray): BooleanArray {
        require(horiz.size == vert.size)
        return BooleanArray(horiz.size) { i -> horiz[i] && vert[i] }
    }

    /** Pita baris berurutan yang seluruhnya aman, panjang ≥ [minBand]. */
    fun findBands(safe: BooleanArray, minBand: Int = MIN_BAND): List<IntRange> =
        findBands(safe, Config(edgeTau = 24, vertTau = 20, minBand = minBand, bandMargin = 4, darkTau = 40, paperTau = 48))

    fun findBands(safe: BooleanArray, cfg: Config): List<IntRange> {
        val band = cfg.minBand.coerceAtLeast(1)
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
     * Rencana potongan v6 untuk [safe] sepanjang [safe.size] baris dengan
     * batas tinggi [limit]. Potongan = TITIK TENGAH pita (dengan [margin]
     * dari tiap sisi tinta) yang terdekat ke batas, dalam jendela
     * [minChunk, limit]. Bila tak ada celah dalam jendela, jendela
     * diperpanjang sampai [overflow] demi celah aman.
     */
    fun planCuts(
        safe: BooleanArray,
        limit: Int,
        minBand: Int = MIN_BAND,
        minChunk: Int = limit / 2,
        overflow: Int = 0,
        margin: Int = BAND_MARGIN
    ): CutPlan = planCuts(safe, limit, Config(edgeTau = 24, vertTau = 20, minBand = minBand, bandMargin = margin, darkTau = 40, paperTau = 48), minChunk, overflow)

    fun planCuts(
        safe: BooleanArray,
        limit: Int,
        cfg: Config,
        minChunk: Int = limit / 2,
        overflow: Int = 0
    ): CutPlan {
        if (limit <= 0 || safe.size <= limit) return CutPlan(emptyList(), true)
        val minC = minChunk.coerceAtLeast(1)
        val bands = findBands(safe, cfg)
        if (bands.isEmpty()) return CutPlan(emptyList(), tailSafe = false)
        val cuts = mutableListOf<Int>()
        var y = 0
        while (safe.size - y > limit) {
            val lo = y + minC
            val hi = y + limit
            val c = nearestCenterInBands(bands, lo, hi, hi, cfg.bandMargin)
                ?: if (overflow > 0) nearestCenterInBands(bands, hi + 1, hi + overflow, hi, cfg.bandMargin) else null
            if (c == null) return CutPlan(cuts, tailSafe = false)
            // Mencegah potongan kembar / mundur akibat margin.
            if (c <= y) return CutPlan(cuts, tailSafe = false)
            cuts.add(c)
            y = c
        }
        return CutPlan(cuts, tailSafe = true)
    }

    /**
     * Titik tengah pita yang dapat dipakai (pita dipangkas [margin] dari
     * tiap sisi; pita yang tersisa < 1 dilewati), yang terdekat ke target.
     */
    private fun nearestCenterInBands(
        bands: List<IntRange>,
        lo: Int,
        hi: Int,
        target: Int,
        margin: Int
    ): Int? {
        var best: Int? = null
        var bestDist = Int.MAX_VALUE
        for (band in bands) {
            val usableFirst = band.first + margin
            val usableLast = band.last - margin
            if (usableFirst > usableLast) continue
            val oLo = maxOf(lo, usableFirst)
            val oHi = minOf(hi, usableLast)
            if (oLo > oHi) continue
            // Tengah area irisan, dijepit ke area yang bisa dipakai.
            val center = (oLo + oHi) / 2
            // Pilih kandidat dalam irisan yang terdekat ke target; karena
            // target biasanya hi (batas), ini = titik sedekat mungkin ke
            // batas TANPA menempel ke tinta.
            val cand = target.coerceIn(oLo, oHi)
            // Condongkan ke tengah bila kandidat menempel di ujung area
            // pakai (kurangi risiko rounding mengiris tinta): geser separuh
            // jalan ke tengah bila jaraknya > 2px.
            val biased = if (cand == oLo || cand == oHi) (cand + center) / 2 else cand
            val dist = absI(biased - target)
            if (dist < bestDist) {
                bestDist = dist
                best = biased
            }
        }
        return best
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
            if (mx - mn > CONTENT_SPREAD_TAU) return true
            i += stride
        }
        return mx - mn > CONTENT_SPREAD_TAU
    }

    /**
     * Pembanding pasangan baris mentah (level rendah, untuk uji).
     * Untuk keputusan sambungan antar-halaman pakai [seamContinues] yang
     * hanya membandingkan baris-baris seam yang bersebelahan.
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

    /**
     * v6: uji kesinambungan SEAM yang benar. [bottom] = strip tepi bawah
     * halaman atas (widthB x hB, row-major), [top] = strip tepi atas halaman
     * bawah (widthT x hT). Hanya [seamRows] baris terakhir [bottom] vs
     * [seamRows] baris pertama [top] yang dibandingkan, kolom irisan tengah.
     * Ini menggantikan pemakaian rowsContinue atas patch penuh yang salah
     * (membandingkan interior-vs-interior yang berjarak 48px).
     */
    fun seamContinues(
        bottom: IntArray,
        bottomW: Int,
        bottomH: Int,
        top: IntArray,
        topW: Int,
        topH: Int,
        seamRows: Int = SEAM_ROWS,
        tau: Int = LINK_TAU,
        minFrac: Double = LINK_MIN_FRAC
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
            // Baris ke-r dari seam: bawah = baris (bottomH-1-r), atas = baris r.
            // Bobot: baris TEPAT di seam (r=0) dihitung ganda agar putus
            // di garis art yang kontinu langsung terdeteksi.
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
}
