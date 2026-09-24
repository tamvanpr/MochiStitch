package com.mochistitch.core.common

/**
 * Kebijakan strip banner per sumber unduhan (mis. baozimh: strip 200px
 * yang bisa muncul di ATAS dan/atau BAWAH tiap gambar halaman).
 * Dieksekusi sebagai gerbang konsistensi: strip hanya dicrop bila isinya
 * nyaris identik di seluruh halaman chapter. Tanpa template, tanpa OpenCV.
 */
data class BannerPolicy(
    val stripPx: Int = 200,
    val checkTop: Boolean = true,
    val checkBottom: Boolean = true,
    /** Halaman lebih pendek dari ini tidak di-crop (demi keamanan). */
    val minPageH: Int = 600,
    /** Butuh minimal sekian halaman untuk membandingkan (arah aman). */
    val minPages: Int = 3,
    /** Fraksi piksel strip harus cocok agar dinyatakan banner. */
    val minFrac: Double = 0.97
)
