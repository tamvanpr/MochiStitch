package com.mochistitch.core.settings

/** Format gambar per potongan strip. */
enum class ImageFormat { JPG, PNG, WEBP }

/** Kemasan output. Default = ZIP. */
enum class PackFormat { ZIP, CBZ, FILES }

/** Aturan membagi strip menjadi beberapa berkas. */
enum class SplitRule { WHOLE, MAX_HEIGHT, PAGES_PER_PACK }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class CutStrictness { LOOSE, BALANCED, STRICT }

/** Warna latar strip. */
enum class MatteColor { WHITE, BLACK, CLEAR }

data class StitchSettings(
    val imageFormat: ImageFormat = ImageFormat.JPG,
    val jpgQuality: Int = 90,
    val webpQuality: Int = 90,
    val packFormat: PackFormat = PackFormat.ZIP,
    val seriesTitle: String = "MochiStitch",
    val chapterLabel: String = "1",
    val namePattern: String = "{series}_ch{chapter}_{n}",
    val numberWidth: Int = 3,
    val splitRule: SplitRule = SplitRule.MAX_HEIGHT,
    val maxStripHeight: Int = 10000,
    val pagesPerPack: Int = 10,
    val showReviewFlags: Boolean = true,
    val matteColor: MatteColor = MatteColor.WHITE,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Basis URL worker Trial Fetch (fase 1: sumber RAW). Kosong = unduhan nonaktif. */
    val workerUrl: String = "",
    /** Ketegasan mesin potong: LONGGAR (lebih banyak halaman utuh) — BALANCED — AKURAT (cut paling ketat). */
    val cutStrictness: CutStrictness = CutStrictness.BALANCED,
    /** Pakai banner template OpenCV + gate konsistensi. */
    val enableBannerCut: Boolean = true
)
