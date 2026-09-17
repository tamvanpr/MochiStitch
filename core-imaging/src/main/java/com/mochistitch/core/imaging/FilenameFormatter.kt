package com.mochistitch.core.imaging

import com.mochistitch.core.settings.OutputFormat
import com.mochistitch.core.settings.OutputWrapperFormat

object FilenameFormatter {

    fun formatFilename(
        template: String,
        project: String,
        chapter: String,
        index: Int,
        indexPaddingDigits: Int,
        format: OutputFormat
    ): String {
        val padded = index.toString().padStart(indexPaddingDigits.coerceAtLeast(1), '0')
        return template
            .replace("{project}", project.ifBlank { "MochiStitch" })
            .replace("{chapter}", chapter.ifBlank { "1" })
            .replace("{index}", padded)
    }

    private val ARCHIVE_EXTS = setOf("zip", "cbz", "cbr", "rar", "7z", "cb7")
    private val IMAGE_EXTS = setOf("png", "jpg", "jpeg", "webp", "bmp", "gif")

    /**
     * Nama output arsip = basename input (tanpa timestamp):
     * "komik.zip"+ZIP -> "komik.zip", "komik.zip"+CBZ -> "komik.cbz".
     */
    fun resolveArchiveOutputName(sourceName: String, wrapper: OutputWrapperFormat): String {
        val raw = sourceName.substringAfterLast('/').substringAfterLast('\\').trim()
        val base = if (raw.isBlank()) "MochiStitch" else raw
        val lower = base.lowercase()
        var stem = base
        for (ext in ARCHIVE_EXTS + IMAGE_EXTS) {
            if (lower.endsWith(".$ext") && stem.length > ext.length + 1) {
                stem = stem.dropLast(ext.length + 1)
                break
            }
        }
        val outExt = when (wrapper) {
            OutputWrapperFormat.CBZ -> "cbz"
            else -> "zip"
        }
        return "${stem.ifBlank { "MochiStitch" }}.$outExt"
    }

    /** Varian bertimestamp untuk sumber non-arsip (anti-timpa). */
    fun resolveArchiveOutputName(sourceName: String, wrapper: OutputWrapperFormat, timestamp: String?): String {
        val plain = resolveArchiveOutputName(sourceName, wrapper)
        if (timestamp.isNullOrBlank()) return plain
        return "${plain.substringBeforeLast('.')}_${timestamp}.${plain.substringAfterLast('.')}"
    }
}
