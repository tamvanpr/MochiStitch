package com.mochistitch.core.imaging

import android.graphics.Bitmap
import android.graphics.Color
import com.mochistitch.core.settings.ImageFormat
import com.mochistitch.core.settings.MatteColor
import com.mochistitch.core.settings.StitchSettings

/** Opsi render strip vertikal. Tidak ada mode crop dalam bentuk apa pun. */
enum class StripMatte(val colorInt: Int) {
    WHITE(Color.WHITE),
    BLACK(Color.BLACK),
    CLEAR(Color.TRANSPARENT)
}

data class StripConfig(
    val matte: StripMatte = StripMatte.WHITE,
    val compressFormat: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
    val quality: Int = 90
) {
    companion object {
        fun fromSettings(s: StitchSettings): StripConfig {
            val fmt = when (s.imageFormat) {
                ImageFormat.JPG -> Bitmap.CompressFormat.JPEG
                ImageFormat.PNG -> Bitmap.CompressFormat.PNG
                // WEBP_LOSSY hanya ada di API 30+; perangkat lama pakai WEBP.
                ImageFormat.WEBP -> if (android.os.Build.VERSION.SDK_INT >= 30) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
            }
            val q = when (s.imageFormat) {
                ImageFormat.JPG -> s.jpgQuality.coerceIn(10, 100)
                ImageFormat.WEBP -> s.webpQuality.coerceIn(10, 100)
                ImageFormat.PNG -> 100
            }
            return StripConfig(
                matte = when (s.matteColor) {
                    MatteColor.WHITE -> StripMatte.WHITE
                    MatteColor.BLACK -> StripMatte.BLACK
                    MatteColor.CLEAR -> StripMatte.CLEAR
                },
                compressFormat = fmt,
                quality = q
            )
        }
    }
}
