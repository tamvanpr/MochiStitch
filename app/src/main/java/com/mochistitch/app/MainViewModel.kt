package com.mochistitch.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mochistitch.core.imaging.AlignmentMode
import com.mochistitch.core.imaging.MergeConfig
import com.mochistitch.core.imaging.MergeDirection
import com.mochistitch.core.imaging.MergeEngine
import com.mochistitch.core.imaging.MergeResult
import com.mochistitch.core.imaging.PaddingColor
import com.mochistitch.core.ui.ImageItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val selectedImages: List<ImageItem> = emptyList(),
    val direction: MergeDirection = MergeDirection.VERTICAL,
    val alignmentMode: AlignmentMode = AlignmentMode.RESIZE_PROPORTIONAL,
    val paddingColor: PaddingColor = PaddingColor.WHITE,
    val isProcessing: Boolean = false,
    val progress: Float = 0f,
    val mergeResult: MergeResult.Success? = null,
    val resultOutputUri: Uri? = null,
    val errorMessage: String? = null
)

class MainViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    fun addImages(uris: List<Uri>, context: Context) {
        val newItems = uris.map { uri ->
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: SecurityException) {
                // Ignore if uri permission cannot be persisted
            }
            val fileName = queryFileName(context, uri) ?: uri.lastPathSegment ?: "Image"
            ImageItem(uri = uri, name = fileName)
        }

        _uiState.update { state ->
            state.copy(selectedImages = state.selectedImages + newItems)
        }
    }

    fun moveUp(index: Int) {
        if (index <= 0) return
        _uiState.update { state ->
            val list = state.selectedImages.toMutableList()
            val item = list.removeAt(index)
            list.add(index - 1, item)
            state.copy(selectedImages = list)
        }
    }

    fun moveDown(index: Int) {
        _uiState.update { state ->
            val list = state.selectedImages.toMutableList()
            if (index < 0 || index >= list.size - 1) return@update state
            val item = list.removeAt(index)
            list.add(index + 1, item)
            state.copy(selectedImages = list)
        }
    }

    fun remove(index: Int) {
        _uiState.update { state ->
            val list = state.selectedImages.toMutableList()
            if (index in list.indices) {
                list.removeAt(index)
            }
            state.copy(selectedImages = list)
        }
    }

    fun clearAll() {
        _uiState.update { it.copy(selectedImages = emptyList()) }
    }

    fun updateDirection(direction: MergeDirection) {
        _uiState.update { it.copy(direction = direction) }
    }

    fun updateAlignmentMode(alignmentMode: AlignmentMode) {
        _uiState.update { it.copy(alignmentMode = alignmentMode) }
    }

    fun updatePaddingColor(paddingColor: PaddingColor) {
        _uiState.update { it.copy(paddingColor = paddingColor) }
    }

    fun startMerge(outputUri: Uri, context: Context) {
        val images = _uiState.value.selectedImages
        if (images.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "No images selected to merge.") }
            return
        }

        val config = MergeConfig(
            direction = _uiState.value.direction,
            alignmentMode = _uiState.value.alignmentMode,
            paddingColor = _uiState.value.paddingColor
        )

        _uiState.update {
            it.copy(
                isProcessing = true,
                progress = 0f,
                errorMessage = null,
                mergeResult = null,
                resultOutputUri = null
            )
        }

        viewModelScope.launch {
            try {
                val outputStream = context.contentResolver.openOutputStream(outputUri)
                if (outputStream == null) {
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            errorMessage = "Could not open output destination for writing."
                        )
                    }
                    return@launch
                }

                val engine = MergeEngine(context)
                val result = outputStream.use { stream ->
                    engine.merge(
                        imageUris = images.map { it.uri },
                        outputStream = stream,
                        config = config,
                        onProgress = { progress ->
                            _uiState.update { state -> state.copy(progress = progress) }
                        }
                    )
                }

                when (result) {
                    is MergeResult.Success -> {
                        _uiState.update {
                            it.copy(
                                isProcessing = false,
                                mergeResult = result,
                                resultOutputUri = outputUri
                            )
                        }
                    }
                    is MergeResult.Error -> {
                        _uiState.update {
                            it.copy(
                                isProcessing = false,
                                errorMessage = result.message
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        errorMessage = e.message ?: "Failed to perform merge operation."
                    )
                }
            }
        }
    }

    fun dismissResult() {
        _uiState.update { it.copy(mergeResult = null, resultOutputUri = null) }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun queryFileName(context: Context, uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use { c ->
                if (c.moveToFirst()) {
                    val nameIndex = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        name = c.getString(nameIndex)
                    }
                }
            }
        }
        return name
    }
}
