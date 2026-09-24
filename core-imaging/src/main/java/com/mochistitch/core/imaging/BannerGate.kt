package com.mochistitch.core.imaging

import com.mochistitch.core.common.BannerPolicy

/**
 * Gerbang strip banner v1 (murni array, unit-testable, tanpa OpenCV).
 *
 * Masalah: beberapa sumber (baozimh) menempel strip banner situs setinggi
 * [BannerPolicy.stripPx] (200px) di ATAS dan/atau BAWAH tiap gambar halaman.
 * Mesin potong v6 tidak akan memotong di situ (teks/logo = baris tidak
 * aman), tapi juga tidak akan membuangnya — banner ikut terjahit.
 *
 * Solusi tanpa template: strip banner situs IDENTIK di semua halaman
 * chapter yang sama, sedangkan art komik berbeda. Jadi strip 200px atas
 * (dan bawah) dibandingkan antar-halaman berurutan via [SeamScan.rowsContinue]
 * (irisan tengah, toleran noise JPEG): cocok di semua pasangan = banner =
 * crop. Tidak cocok / halaman terlalu sedikit = biarkan utuh (arah aman).
 */
object BannerGate {

    data class Strip(val px: IntArray, val w: Int, val h: Int)

    /** Indeks (dalam daftar kandidat) yang diputuskan sebagai banner. */
    data class Decision(val top: Set<Int>, val bottom: Set<Int>)

    /**
     * Keputusan per posisi: cari kelompok strip terbesar yang saling cocok
     * (acuan = strip pertama, fallback strip terakhir untuk kasus cover
     * pembuka/penutup yang beda sendiri). Kelompok ≥ minPages -> crop.
     * Halaman di luar kelompok dibiarkan utuh (arah aman: tahan banner
     * campuran, mis. halaman pertama berjudul beda).
     */
    fun decide(
        tops: List<Strip>,
        bottoms: List<Strip>,
        policy: BannerPolicy
    ): Decision {
        val need = policy.minPages.coerceAtLeast(2)
        return Decision(
            top = if (policy.checkTop) group(tops, policy.minFrac, need) else emptySet(),
            bottom = if (policy.checkBottom) group(bottoms, policy.minFrac, need) else emptySet()
        )
    }

    private fun group(strips: List<Strip>, minFrac: Double, need: Int): Set<Int> {
        if (strips.size < need) return emptySet()
        // Coba acuan strip pertama, lalu strip terakhir.
        for (refIdx in listOf(0, strips.lastIndex).distinct()) {
            val ref = strips[refIdx]
            val g = strips.indices.filter { match(ref, strips[it], minFrac) }.toSet()
            if (g.size >= need) return g
        }
        return emptySet()
    }

    private fun match(a: Strip, b: Strip, minFrac: Double): Boolean {
        if (a.px.isEmpty() || b.px.isEmpty()) return false
        return SeamScan.rowsContinue(a.px, b.px, minFrac = minFrac)
    }
}
