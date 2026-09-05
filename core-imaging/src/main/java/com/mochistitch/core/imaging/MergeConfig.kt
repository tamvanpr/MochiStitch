package com.mochistitch.core.imaging

import android.graphics.Bitmap
import android.graphics.Color

enum class MergeDirection {
    VERTICAL,
    HORIZONTAL_LTR,
    HORIZONTAL_RTL
}

enum class AlignmentMode {
    RESIZE_PROPORTIONAL,
    CENTER_CROP,
    PADDING
}

enum class PaddingColor(val colorInt: Int) {
    WHITE(Color.WHITE),
    BLACK(Color.BLACK),
    TRANSPARENT(Color.TRANSPARENT)
}

data class MergeConfig(
    val direction: MergeDirection = MergeDirection.VERTICAL,
    val alignmentMode: AlignmentMode = AlignmentMode.RESIZE_PROPORTIONAL,
    val paddingColor: PaddingColor = PaddingColor.WHITE,
    val compressFormat: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
    val quality: Int = 90
)

sealed interface MergeResult {
    data class Success(
        val width: Int,
        val height: Int,
        val bytesWritten: Long
    ) : MergeResult

    data class Error(
        val message: String,
        val cause: Throwable? = null
    ) : MergeResult
}
