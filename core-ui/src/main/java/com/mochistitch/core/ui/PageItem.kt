package com.mochistitch.core.ui

import android.graphics.Bitmap
import android.net.Uri
import java.util.UUID

/** Satu halaman di workbench. */
data class PageItem(
    val key: String = UUID.randomUUID().toString(),
    val uri: Uri,
    val title: String = ""
)

/** Satu strip hasil untuk pratinjau. */
data class SliceInfo(
    val order: Int,
    val fileName: String,
    val bitmap: Bitmap?,
    val cachePath: String?,
    val width: Int,
    val height: Int,
    val flagged: Boolean = false,
    val flagReason: String? = null,
    val bytes: Long = 0L,
    /** Strip banner situs dicrop di berkas ini (info, bukan peringatan). */
    val bannerCut: Boolean = false
)
