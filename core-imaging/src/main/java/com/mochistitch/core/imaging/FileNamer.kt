package com.mochistitch.core.imaging

import com.mochistitch.core.settings.ImageFormat
import com.mochistitch.core.settings.PackFormat

object FileNamer {

    fun numbered(
        pattern: String,
        series: String,
        chapter: String,
        n: Int,
        width: Int,
        format: ImageFormat
    ): String {
        val padded = n.toString().padStart(width.coerceAtLeast(1), '0')
        return pattern
            .replace("{series}", series.ifBlank { "MochiStitch" })
            .replace("{chapter}", chapter.ifBlank { "1" })
            .replace("{n}", padded)
    }

    private val PACK_ENDINGS = setOf("zip", "cbz", "cbr", "rar", "7z", "cb7")
    private val PIC_ENDINGS = setOf("png", "jpg", "jpeg", "webp", "bmp", "gif")

    /**
     * Nama arsip output = basename input arsip ("k.zip"+ZIP -> "k.zip",
     * "k.zip"+CBZ -> "k.cbz"). Tanpa timestamp.
     */
    fun packName(origin: String, pack: PackFormat): String {
        val raw = origin.substringAfterLast('/').substringAfterLast('\\').trim()
        val base = if (raw.isBlank()) "MochiStitch" else raw
        val lower = base.lowercase()
        var stem = base
        for (ext in PACK_ENDINGS + PIC_ENDINGS) {
            if (lower.endsWith(".$ext") && stem.length > ext.length + 1) {
                stem = stem.dropLast(ext.length + 1)
                break
            }
        }
        val outExt = if (pack == PackFormat.CBZ) "cbz" else "zip"
        return "${stem.ifBlank { "MochiStitch" }}.$outExt"
    }

    /** Varian bertimestamp untuk sumber non-arsip. */
    fun packName(origin: String, pack: PackFormat, timestamp: String?): String {
        val plain = packName(origin, pack)
        if (timestamp.isNullOrBlank()) return plain
        return "${plain.substringBeforeLast('.')}_${timestamp}.${plain.substringAfterLast('.')}"
    }

    fun extensionOf(format: ImageFormat): String = when (format) {
        ImageFormat.JPG -> "jpg"
        ImageFormat.PNG -> "png"
        ImageFormat.WEBP -> "webp"
    }

    fun mimeOf(format: ImageFormat): String = when (format) {
        ImageFormat.JPG -> "image/jpeg"
        ImageFormat.PNG -> "image/png"
        ImageFormat.WEBP -> "image/webp"
    }
}
