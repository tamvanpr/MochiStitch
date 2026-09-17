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
        val paddedIndex = index.toString().padStart(indexPaddingDigits.coerceAtLeast(1), '0')
        val formattedName = template
            .replace("{project}", project.ifBlank { "MochiStitch" })
            .replace("{chapter}", chapter.ifBlank { "1" })
            .replace("{index}", paddedIndex)

        return formattedName
    }

    private val ARCHIVE_EXTENSIONS = setOf("zip", "cbz", "cbr", "rar", "7z", "cb7")

    /**
     * Nama file output arsip yang MEMPERTAHANKAN basename input.
     * "komik_ch1.zip" + ZIP -> "komik_ch1.zip"
     * "komik_ch1.zip" + CBZ -> "komik_ch1.cbz"
     * "komik_ch1.cbz" + ZIP -> "komik_ch1.zip"
     * "halaman01.png" + ZIP -> "halaman01.zip" (ekstensi gambar ikut dikupas)
     * "tanpa-ekstensi" + CBZ -> "tanpa-ekstensi.cbz"
     *
     * Murni Kotlin/JVM agar mudah diuji unit.
     */
    fun resolveArchiveOutputName(
        sourceName: String,
        wrapper: OutputWrapperFormat
    ): String {
        val raw = sourceName.substringAfterLast('/').substringAfterLast('\\').trim()
        val base = if (raw.isBlank()) "MochiStitch" else raw
        val lower = base.lowercase()
        var stem = base
        for (ext in ARCHIVE_EXTENSIONS + setOf("png", "jpg", "jpeg", "webp", "bmp", "gif")) {
            if (lower.endsWith(".$ext") && stem.length > ext.length + 1) {
                stem = stem.dropLast(ext.length + 1)
                break
            }
        }
        val outExt = when (wrapper) {
            OutputWrapperFormat.CBZ -> "cbz"
            OutputWrapperFormat.ZIP -> "zip"
            OutputWrapperFormat.LOOSE_FILES -> "zip"
        }
        return "${stem.ifBlank { "MochiStitch" }}.$outExt"
    }

    /**
     * Nama file output arsip dari sumber arsip + timestamp opsional.
     * Tanpa timestamp, nama SAMA PERSIS dengan basename input (kecuali ekstensi).
     */
    fun resolveArchiveOutputName(
        sourceName: String,
        wrapper: OutputWrapperFormat,
        timestamp: String?
    ): String {
        val plain = resolveArchiveOutputName(sourceName, wrapper)
        if (timestamp.isNullOrBlank()) return plain
        val stem = plain.substringBeforeLast('.')
        val ext = plain.substringAfterLast('.')
        return "${stem}_$timestamp.$ext"
    }
}
