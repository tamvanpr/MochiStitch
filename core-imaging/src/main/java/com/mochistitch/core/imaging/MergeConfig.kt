package com.mochistitch.core.imaging

import android.graphics.Bitmap
import android.graphics.Color

/**
 * Arah penggabungan halaman komik.
 *
 * MochiStitch hanya mendukung penggabungan VERTIKAL (strip webtoon panjang).
 * Varian horizontal (kiri-kanan) dihapus total — tidak ada cabang kode,
 * opsi UI, maupun pengaturan untuk arah horizontal di mana pun.
 */
enum class MergeDirection {
    VERTICAL
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
