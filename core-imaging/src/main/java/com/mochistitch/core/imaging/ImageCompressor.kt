package com.mochistitch.core.imaging

import android.graphics.Bitmap
import android.os.Build
import com.mochistitch.core.settings.OutputFormat
import java.io.OutputStream

object ImageCompressor {
    fun compress(
        bitmap: Bitmap,
        format: OutputFormat,
        quality: Int,
        webpLossless: Boolean,
        outputStream: OutputStream
    ): Boolean {
        val compressFormat = when (format) {
            OutputFormat.PNG -> Bitmap.CompressFormat.PNG
            OutputFormat.JPG -> Bitmap.CompressFormat.JPEG
            OutputFormat.WEBP -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (webpLossless) Bitmap.CompressFormat.WEBP_LOSSLESS else Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
            }
        }
        val clampedQuality = quality.coerceIn(0, 100)
        return bitmap.compress(compressFormat, clampedQuality, outputStream)
    }

    fun getFileExtension(format: OutputFormat): String {
        return when (format) {
            OutputFormat.PNG -> "png"
            OutputFormat.JPG -> "jpg"
            OutputFormat.WEBP -> "webp"
        }
    }

    fun getMimeType(format: OutputFormat): String {
        return when (format) {
            OutputFormat.PNG -> "image/png"
            OutputFormat.JPG -> "image/jpeg"
            OutputFormat.WEBP -> "image/webp"
        }
    }
}
