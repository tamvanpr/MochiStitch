package com.mochistitch.core.settings

enum class OutputFormat {
    PNG, JPG, WEBP
}

enum class OutputWrapperFormat {
    LOOSE_FILES, CBZ, ZIP
}

enum class SplitMode {
    NO_LIMIT, MAX_PIXELS, PAGES_PER_FILE
}

enum class ReadingDirection {
    LTR, RTL, VERTICAL
}

enum class AlignmentModeSetting {
    RESIZE_PROPORTIONAL, CENTER_CROP, PADDING
}

enum class PaddingColorSetting {
    WHITE, BLACK, TRANSPARENT
}

enum class DetectionSensitivity {
    LOW, MEDIUM, HIGH
}

data class MochiStitchSettings(
    val outputFormat: OutputFormat = OutputFormat.JPG,
    val jpgQuality: Int = 90,
    val webpQuality: Int = 90,
    val webpLossless: Boolean = false,
    val wrapperFormat: OutputWrapperFormat = OutputWrapperFormat.LOOSE_FILES,
    val projectName: String = "MochiStitch",
    val chapterName: String = "1",
    val filenameTemplate: String = "{project}_ch{chapter}_{index}",
    val indexPaddingDigits: Int = 3,
    val splitMode: SplitMode = SplitMode.MAX_PIXELS,
    val maxPixelLength: Int = 5000,
    val maxPagesPerFile: Int = 10,
    val readingDirection: ReadingDirection = ReadingDirection.LTR,
    val alignmentMode: AlignmentModeSetting = AlignmentModeSetting.RESIZE_PROPORTIONAL,
    val paddingColor: PaddingColorSetting = PaddingColorSetting.WHITE,
    val mochiSmartEnabled: Boolean = true,
    val mochiSmartTolerance: Int = 150,
    val mochiSmartSensitivity: DetectionSensitivity = DetectionSensitivity.MEDIUM,
    val showManualReviewMarkers: Boolean = true,
    val autoGutterDetectionEnabled: Boolean = true,
    val pixelComparisonSensitivity: Float = 0.5f,
    val pixelComparisonMargins: Int = 0,
    val pixelComparisonStep: Int = 5,
    val pixelComparisonMaxDeviationFactor: Float = 0.2f
)
