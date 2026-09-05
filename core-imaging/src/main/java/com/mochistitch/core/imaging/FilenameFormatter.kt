package com.mochistitch.core.imaging

import com.mochistitch.core.settings.OutputFormat

object FilenameFormatter {
    fun formatFilename(
        template: String,
        project: String,
        chapter: String,
        index: Int,
        indexPaddingDigits: Int,
        format: OutputFormat
    ): String {
        val extension = ImageCompressor.getFileExtension(format)
        val paddedIndex = index.toString().padStart(indexPaddingDigits.coerceAtLeast(1), '0')
        val formattedName = template
            .replace("{project}", project.ifBlank { "MochiStitch" })
            .replace("{chapter}", chapter.ifBlank { "1" })
            .replace("{index}", paddedIndex)

        return if (formattedName.endsWith(".$extension", ignoreCase = true)) {
            formattedName
        } else {
            "$formattedName.$extension"
        }
    }
}
