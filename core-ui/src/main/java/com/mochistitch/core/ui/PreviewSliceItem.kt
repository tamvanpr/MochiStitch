package com.mochistitch.core.ui

import android.graphics.Bitmap

data class PreviewSliceItem(
    val index: Int,
    val filename: String,
    val bitmap: Bitmap,
    val cacheFilePath: String? = null,
    val width: Int,
    val height: Int,
    val needsManualReview: Boolean = false,
    val reviewReason: String? = null,
    val bytesWritten: Long = 0L
)
