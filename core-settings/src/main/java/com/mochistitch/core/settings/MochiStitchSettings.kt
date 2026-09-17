package com.mochistitch.core.settings

/**
 * Pengaturan MochiStitch v2 (rombak total).
 *
 * Prinsip yang dikunci:
 * - Penggabungan HANYA vertikal — tidak ada enum/opsi arah sama sekali.
 * - Output default = arsip ZIP.
 * - Pemotongan hanya di batas halaman asli; Mochi Smart (gutter + jaga balon)
 *   hanya dipakai untuk halaman tunggal yang melebihi batas.
 */
enum class OutputFormat { JPG, PNG, WEBP }

enum class OutputWrapperFormat { ZIP, CBZ, LOOSE_FILES }

enum class SplitMode { NO_LIMIT, MAX_PIXELS, PAGES_PER_FILE }

enum class Strictness { NORMAL, STRICT }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class AlignmentModeSetting { RESIZE_PROPORTIONAL, CENTER_CROP, PADDING }

enum class PaddingColorSetting { WHITE, BLACK, TRANSPARENT }

data class MochiStitchSettings(
    val outputFormat: OutputFormat = OutputFormat.JPG,
    val jpgQuality: Int = 90,
    val webpQuality: Int = 90,
    val webpLossless: Boolean = false,
    val wrapperFormat: OutputWrapperFormat = OutputWrapperFormat.ZIP,
    val projectName: String = "MochiStitch",
    val chapterName: String = "1",
    val filenameTemplate: String = "{project}_ch{chapter}_{index}",
    val indexPaddingDigits: Int = 3,
    val splitMode: SplitMode = SplitMode.MAX_PIXELS,
    val maxPixelLength: Int = 10000,
    val maxPagesPerFile: Int = 10,
    val mochiSmartEnabled: Boolean = true,
    val strictness: Strictness = Strictness.STRICT,
    val showManualReviewMarkers: Boolean = true,
    val paperTolerance: Float = 12f,
    val alignmentMode: AlignmentModeSetting = AlignmentModeSetting.RESIZE_PROPORTIONAL,
    val paddingColor: PaddingColorSetting = PaddingColorSetting.WHITE,
    val themeMode: ThemeMode = ThemeMode.SYSTEM
)
