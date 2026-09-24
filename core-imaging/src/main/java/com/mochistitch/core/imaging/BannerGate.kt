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

    data class Decision(val cropTop: Boolean, val cropBottom: Boolean)

    fun decide(
        tops: List<Strip>,
        bottoms: List<Strip>,
        policy: BannerPolicy
    ): Decision {
        val need = policy.minPages.coerceAtLeast(2)
        val topOk = policy.checkTop && tops.size >= need &&
            tops.zipWithNext().all { (a, b) -> match(a, b, policy.minFrac) }
        val botOk = policy.checkBottom && bottoms.size >= need &&
            bottoms.zipWithNext().all { (a, b) -> match(a, b, policy.minFrac) }
        return Decision(topOk, botOk)
    }

    private fun match(a: Strip, b: Strip, minFrac: Double): Boolean {
        if (a.px.isEmpty() || b.px.isEmpty()) return false
        return SeamScan.rowsContinue(a.px, b.px, minFrac = minFrac)
    }
}
