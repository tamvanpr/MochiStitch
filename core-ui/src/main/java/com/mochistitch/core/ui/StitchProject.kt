package com.mochistitch.core.ui

import com.mochistitch.core.settings.OutputWrapperFormat
import java.util.UUID

/**
 * Satu projek bulk MochiStitch: sekumpulan halaman dari satu sumber
 * (gambar pilihan manual atau hasil ekstraksi arsip ZIP/CBZ/RAR/CBR)
 * dengan format output yang bisa ditentukan SENDIRI per-proyek.
 *
 * @param sourceName nama sumber, mis. "komik_ch1.zip" atau "pilihan manual".
 *   Dipakai sebagai basis penamaan file output agar nama output arsip
 *   SAMA dengan basename input arsipnya.
 * @param wrapperOverride bila null, memakai wrapper global dari pengaturan.
 */
data class StitchProject(
    val id: String = UUID.randomUUID().toString(),
    val sourceName: String,
    val images: List<ImageItem> = emptyList(),
    val wrapperOverride: OutputWrapperFormat? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    /** Format wrapper efektif: override per-proyek bila ada, jika tidak global. */
    fun effectiveWrapper(global: OutputWrapperFormat): OutputWrapperFormat =
        wrapperOverride ?: global
}
