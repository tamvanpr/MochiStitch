package com.mochistitch.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mochistitch.core.archive.ArchiveEntry
import com.mochistitch.core.archive.ArchiveHandler
import com.mochistitch.core.imaging.AlignmentMode
import com.mochistitch.core.imaging.FilenameFormatter
import com.mochistitch.core.imaging.ImageCompressor
import com.mochistitch.core.imaging.MergeDirection
import com.mochistitch.core.imaging.PaddingColor
import com.mochistitch.core.imaging.ProcessingStage
import com.mochistitch.core.imaging.StitchProcessor
import com.mochistitch.core.settings.AlignmentModeSetting
import com.mochistitch.core.settings.MochiStitchSettings
import com.mochistitch.core.settings.MochiStitchSettingsRepository
import com.mochistitch.core.settings.OutputFormat
import com.mochistitch.core.settings.OutputWrapperFormat
import com.mochistitch.core.settings.PaddingColorSetting
import com.mochistitch.core.settings.ReadingDirection
import com.mochistitch.core.ui.ImageItem
import com.mochistitch.core.ui.PreviewSliceItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.OutputStream

enum class Screen {
    MAIN, SETTINGS, PREVIEW
}

data class ExportResultInfo(
    val width: Int,
    val height: Int,
    val bytesWritten: Long,
    val outputCount: Int,
    val itemsNeedingManualReview: Int = 0
)

data class MainUiState(
    val currentScreen: Screen = Screen.MAIN,
    val selectedImages: List<ImageItem> = emptyList(),
    val previewSlices: List<PreviewSliceItem> = emptyList(),
    val settings: MochiStitchSettings = MochiStitchSettings(),
    val isProcessing: Boolean = false,
    val processingStep: String = "",
    val progress: Float = 0f,
    val exportResult: ExportResultInfo? = null,
    val resultOutputUri: Uri? = null,
    val errorMessage: String? = null,
    val isSelectionMode: Boolean = false,
    val selectedIndexes: Set<Int> = emptySet()
)

class MainViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var settingsRepository: MochiStitchSettingsRepository? = null

    fun initSettings(context: Context) {
        if (settingsRepository == null) {
            val repo = MochiStitchSettingsRepository(context)
            settingsRepository = repo
            viewModelScope.launch {
                repo.settingsFlow.collect { newSettings ->
                    _uiState.update { it.copy(settings = newSettings) }
                }
            }
        }
    }

    fun navigateTo(screen: Screen) {
        _uiState.update { it.copy(currentScreen = screen) }
    }

    fun saveSettings(settings: MochiStitchSettings) {
        _uiState.update { it.copy(settings = settings) }
        viewModelScope.launch {
            settingsRepository?.updateSettings(settings)
        }
    }

    fun addImages(uris: List<Uri>, context: Context) {
        initSettings(context)
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

    // Multi-select methods
    fun toggleSelectionMode() {
        _uiState.update { it.copy(isSelectionMode = !it.isSelectionMode, selectedIndexes = emptySet()) }
    }

    fun toggleSelection(index: Int) {
        _uiState.update { state ->
            val selected = state.selectedIndexes.toMutableSet()
            if (index in selected) {
                selected.remove(index)
            } else {
                selected.add(index)
            }
            state.copy(selectedIndexes = selected)
        }
    }

    fun selectAll() {
        _uiState.update { state ->
            state.copy(selectedIndexes = state.selectedImages.indices.toSet())
        }
    }

    fun deselectAll() {
        _uiState.update { it.copy(selectedIndexes = emptySet()) }
    }

    fun deleteSelected() {
        _uiState.update { state ->
            val list = state.selectedImages.toMutableList()
            // Sort indices in descending order to avoid index shifting issues
            val sortedIndices = state.selectedIndexes.sortedDescending()
            for (index in sortedIndices) {
                if (index in list.indices) {
                    list.removeAt(index)
                }
            }
            state.copy(
                selectedImages = list,
                selectedIndexes = emptySet()
            )
        }
    }

    fun clearAll() {
        clearPreviewSlices()
        _uiState.update { it.copy(selectedImages = emptyList()) }
    }

    fun clearPreviewSlices() {
        val slices = _uiState.value.previewSlices
        slices.forEach { slice ->
            if (!slice.bitmap.isRecycled) {
                slice.bitmap.recycle()
            }
        }
        _uiState.update { it.copy(previewSlices = emptyList()) }
    }

    fun updateDirection(direction: MergeDirection) {
        val readingDir = when (direction) {
            MergeDirection.VERTICAL -> ReadingDirection.VERTICAL
            MergeDirection.HORIZONTAL_LTR -> ReadingDirection.LTR
            MergeDirection.HORIZONTAL_RTL -> ReadingDirection.RTL
        }
        val newSettings = _uiState.value.settings.copy(readingDirection = readingDir)
        saveSettings(newSettings)
    }

    fun updateAlignmentMode(alignmentMode: AlignmentMode) {
        val modeSetting = when (alignmentMode) {
            AlignmentMode.RESIZE_PROPORTIONAL -> AlignmentModeSetting.RESIZE_PROPORTIONAL
            AlignmentMode.CENTER_CROP -> AlignmentModeSetting.CENTER_CROP
            AlignmentMode.PADDING -> AlignmentModeSetting.PADDING
        }
        val newSettings = _uiState.value.settings.copy(alignmentMode = modeSetting)
        saveSettings(newSettings)
    }

    fun updatePaddingColor(paddingColor: PaddingColor) {
        val colorSetting = when (paddingColor) {
            PaddingColor.WHITE -> PaddingColorSetting.WHITE
            PaddingColor.BLACK -> PaddingColorSetting.BLACK
            PaddingColor.TRANSPARENT -> PaddingColorSetting.TRANSPARENT
        }
        val newSettings = _uiState.value.settings.copy(paddingColor = colorSetting)
        saveSettings(newSettings)
    }

    fun getExportMimeType(): String {
        val settings = _uiState.value.settings
        return when (settings.wrapperFormat) {
            OutputWrapperFormat.CBZ -> "application/x-cbz"
            OutputWrapperFormat.ZIP -> "application/zip"
            OutputWrapperFormat.LOOSE_FILES -> ImageCompressor.getMimeType(settings.outputFormat)
        }
    }

    fun getExportDefaultFilename(): String {
        val settings = _uiState.value.settings
        val project = settings.projectName.ifBlank { "MochiStitch" }
        val chapter = settings.chapterName.ifBlank { "1" }
        return when (settings.wrapperFormat) {
            OutputWrapperFormat.CBZ -> "${project}_ch${chapter}.cbz"
            OutputWrapperFormat.ZIP -> "${project}_ch${chapter}.zip"
            OutputWrapperFormat.LOOSE_FILES -> FilenameFormatter.formatFilename(
                template = settings.filenameTemplate,
                project = project,
                chapter = chapter,
                index = 1,
                indexPaddingDigits = settings.indexPaddingDigits,
                format = settings.outputFormat
            )
        }
    }

    fun generatePreview(context: Context) {
        initSettings(context)
        val images = _uiState.value.selectedImages
        if (images.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "No images selected to merge.") }
            return
        }

        clearPreviewSlices()

        val settings = _uiState.value.settings

        _uiState.update {
            it.copy(
                isProcessing = true,
                processingStep = "Aligning images",
                progress = 0f,
                errorMessage = null,
                exportResult = null,
                resultOutputUri = null
            )
        }

        viewModelScope.launch {
            val processor = StitchProcessor(context)
            try {
                val result = processor.process(
                    imageUris = images.map { it.uri },
                    settings = settings,
                    onProgress = { stage, progress ->
                        _uiState.update { state ->
                            state.copy(
                                processingStep = stage.stepName,
                                progress = progress
                            )
                        }
                    }
                )

                if (result.isFailure) {
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            errorMessage = result.exceptionOrNull()?.message ?: "Failed to process image stitching."
                        )
                    }
                    return@launch
                }

                val items = result.getOrThrow()
                val previewSlices = items.map { item ->
                    PreviewSliceItem(
                        index = item.index,
                        filename = item.filename,
                        bitmap = item.bitmap,
                        width = item.width,
                        height = item.height,
                        needsManualReview = item.needsManualReview,
                        bytesWritten = item.estimatedBytes
                    )
                }

                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        previewSlices = previewSlices,
                        currentScreen = Screen.PREVIEW
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        errorMessage = e.message ?: "Failed to generate preview."
                    )
                }
            }
        }
    }

    fun exportResult(outputUri: Uri, context: Context) {
        val previewSlices = _uiState.value.previewSlices
        if (previewSlices.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "No preview available to export.") }
            return
        }

        val settings = _uiState.value.settings

        _uiState.update {
            it.copy(
                isProcessing = true,
                processingStep = ProcessingStage.EXPORTING.stepName,
                progress = 0f,
                errorMessage = null
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

                var bytesWritten = 0L

                if (settings.wrapperFormat == OutputWrapperFormat.CBZ || settings.wrapperFormat == OutputWrapperFormat.ZIP) {
                    val archiveEntries = previewSlices.mapIndexed { index, slice ->
                        _uiState.update { it.copy(progress = (index + 1).toFloat() / previewSlices.size.toFloat()) }
                        val baos = ByteArrayOutputStream()
                        val quality = if (settings.outputFormat == OutputFormat.JPG) settings.jpgQuality else settings.webpQuality
                        ImageCompressor.compress(
                            bitmap = slice.bitmap,
                            format = settings.outputFormat,
                            quality = if (settings.outputFormat == OutputFormat.JPG) settings.jpgQuality else settings.webpQuality,
                            webpLossless = settings.webpLossless,
                            outputStream = baos
                        )
                        ArchiveEntry(slice.filename, baos.toByteArray())
                    }
                    bytesWritten = ArchiveHandler.createArchive(archiveEntries, outputStream)
                    // Recycle bitmaps setelah di-compress
                    previewSlices.forEach { it.bitmap.recycle() }
                } else {
                    val byteCountingStream = ByteCountingOutputStream(outputStream)
                    for ((index, slice) in previewSlices.withIndex()) {
                        _uiState.update { it.copy(progress = (index + 1).toFloat() / previewSlices.size.toFloat()) }
                        ImageCompressor.compress(
                            bitmap = slice.bitmap,
                            format = settings.outputFormat,
                            quality = if (settings.outputFormat == OutputFormat.JPG) settings.jpgQuality else settings.webpQuality,
                            webpLossless = settings.webpLossless,
                            outputStream = byteCountingStream
                        )
                        // Recycle bitmap setelah di-compress untuk hemat memori
                        if (!slice.bitmap.isRecycled) {
                            slice.bitmap.recycle()
                        }
                    }
                    byteCountingStream.flush()
                    bytesWritten = byteCountingStream.bytesWritten
                    outputStream.close()
                }

                val maxW = previewSlices.maxOfOrNull { it.width } ?: 0
                val totalH = previewSlices.sumOf { it.height }
                val manualReviewCount = previewSlices.count { it.needsManualReview }

                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        exportResult = ExportResultInfo(
                            width = maxW,
                            height = totalH,
                            bytesWritten = bytesWritten,
                            outputCount = previewSlices.size,
                            itemsNeedingManualReview = manualReviewCount
                        ),
                        resultOutputUri = outputUri
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        errorMessage = e.message ?: "Failed to perform export operation."
                    )
                }
            }
        }
    }

    fun dismissResult() {
        _uiState.update { it.copy(exportResult = null, resultOutputUri = null) }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        clearPreviewSlices()
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

    private class ByteCountingOutputStream(private val delegate: OutputStream) : OutputStream() {
        var bytesWritten: Long = 0
            private set

        override fun write(b: Int) {
            delegate.write(b)
            bytesWritten++
        }

        override fun write(b: ByteArray) {
            delegate.write(b)
            bytesWritten += b.size
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            delegate.write(b, off, len)
            bytesWritten += len
        }

        override fun flush() {
            delegate.flush()
        }

        override fun close() {
            delegate.close()
        }
    }
}
