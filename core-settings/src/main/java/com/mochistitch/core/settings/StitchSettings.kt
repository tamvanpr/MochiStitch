package com.mochistitch.core.settings

/** Format gambar per potongan strip. */
enum class ImageFormat { JPG, PNG, WEBP }

/** Kemasan output. Default = ZIP. */
enum class PackFormat { ZIP, CBZ, FILES }

/** Aturan membagi strip menjadi beberapa berkas. */
enum class SplitRule { WHOLE, MAX_HEIGHT, PAGES_PER_PACK }

enum class Strictness { LENIENT, STRICT }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Cara menyesuaikan lebar halaman ke lebar strip. */
enum class FitMode { FIT_WIDTH, CROP_CENTER, LETTERBOX }

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
    val smartCut: Boolean = true,
    val strictness: Strictness = Strictness.STRICT,
    val showReviewFlags: Boolean = true,
    val paperSensitivity: Float = 12f,
    val fitMode: FitMode = FitMode.FIT_WIDTH,
    val matteColor: MatteColor = MatteColor.WHITE,
    val themeMode: ThemeMode = ThemeMode.SYSTEM
)
