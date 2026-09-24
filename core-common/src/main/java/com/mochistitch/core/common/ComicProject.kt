package com.mochistitch.core.common

import android.net.Uri
import com.mochistitch.core.settings.PackFormat
import java.util.UUID

/**
 * Satu komik dalam antrean: daftar halaman + kemasan output pilihan sendiri.
 * [origin] menyimpan nama arsip asal bila diimpor dari arsip — dipakai
 * sebagai basis nama file output agar SAMA dengan input.
 */
data class ComicProject(
    val id: String = UUID.randomUUID().toString(),
    val origin: String,
    val pageUris: List<Uri> = emptyList(),
    val pageNames: List<String> = emptyList(),
    val packOverride: PackFormat? = null,
    /** Id sumber unduhan (RawSources) bila dari fitur Unduh; null = manual/arsip. */
    val sourceId: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun packFor(global: PackFormat): PackFormat = packOverride ?: global
}
