package com.mochistitch.core.imaging

import android.graphics.Bitmap
import android.graphics.Color
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
                com.mochistitch.core.settings.ImageFormat.JPG -> Bitmap.CompressFormat.JPEG
                com.mochistitch.core.settings.ImageFormat.PNG -> Bitmap.CompressFormat.PNG
                com.mochistitch.core.settings.ImageFormat.WEBP -> Bitmap.CompressFormat.WEBP_LOSSY
            }
            val q = when (s.imageFormat) {
                com.mochistitch.core.settings.ImageFormat.JPG -> s.jpgQuality.coerceIn(10, 100)
                com.mochistitch.core.settings.ImageFormat.WEBP -> s.webpQuality.coerceIn(10, 100)
                com.mochistitch.core.settings.ImageFormat.PNG -> 100
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

sealed interface StripOutcome {
    data class Done(val width: Int, val height: Int, val bytes: Long) : StripOutcome
    data class Failed(val message: String, val cause: Throwable? = null) : StripOutcome
}
