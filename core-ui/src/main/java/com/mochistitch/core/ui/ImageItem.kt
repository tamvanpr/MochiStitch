package com.mochistitch.core.ui

import android.net.Uri
import java.util.UUID

data class ImageItem(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri,
    val name: String = ""
)
