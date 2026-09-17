package com.mochistitch.core.imaging

import android.graphics.Bitmap
import com.mochistitch.core.settings.MochiStitchSettings
import com.mochistitch.core.settings.OutputFormat

object ImageCompressor {

    fun getFileExtension(format: OutputFormat): String = when (format) {
        OutputFormat.JPG -> "jpg"
        OutputFormat.PNG -> "png"
        OutputFormat.WEBP -> "webp"
    }

    fun getMimeType(format: OutputFormat): String = when (format) {
        OutputFormat.JPG -> "image/jpeg"
        OutputFormat.PNG -> "image/png"
        OutputFormat.WEBP -> "image/webp"
    }

    fun compressFormat(settings: MochiStitchSettings): Bitmap.CompressFormat = when (settings.outputFormat) {
        OutputFormat.JPG -> Bitmap.CompressFormat.JPEG
        OutputFormat.PNG -> Bitmap.CompressFormat.PNG
        OutputFormat.WEBP -> Bitmap.CompressFormat.WEBP_LOSSY
    }

    fun quality(settings: MochiStitchSettings): Int = when (settings.outputFormat) {
        OutputFormat.JPG -> settings.jpgQuality.coerceIn(10, 100)
        OutputFormat.WEBP -> settings.webpQuality.coerceIn(10, 100)
        OutputFormat.PNG -> 100
    }
}
