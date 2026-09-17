package com.mochistitch.core.common

import android.net.Uri
import com.mochistitch.core.settings.OutputWrapperFormat
import java.util.UUID

/**
 * Satu projek bulk: sekumpulan halaman dari satu sumber (pilihan gambar
 * manual atau hasil ekstraksi arsip) dengan format output yang bisa
 * ditentukan SENDIRI per-proyek.
 */
data class StitchProject(
    val id: String = UUID.randomUUID().toString(),
    val sourceName: String,
    val imageUris: List<Uri> = emptyList(),
    val imageNames: List<String> = emptyList(),
    val wrapperOverride: OutputWrapperFormat? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    /** Override per-proyek bila ada, jika tidak ikut global. */
    fun effectiveWrapper(global: OutputWrapperFormat): OutputWrapperFormat =
        wrapperOverride ?: global
}
