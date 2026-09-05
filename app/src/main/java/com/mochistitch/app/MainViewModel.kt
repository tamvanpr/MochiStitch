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
import com.mochistitch.core.imaging.StitchProcessor
import com.mochistitch.core.settings.AlignmentModeSetting
import com.mochistitch.core.settings.MochiStitchSettings
import com.mochistitch.core.settings.MochiStitchSettingsRepository
import com.mochistitch.core.settings.OutputFormat
import com.mochistitch.core.settings.OutputWrapperFormat
import com.mochistitch.core.settings.PaddingColorSetting
import com.mochistitch.core.settings.ReadingDirection
import com.mochistitch.core.ui.ImageItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.OutputStream

enum class Screen {
    MAIN, SETTINGS
}

data class ExportResultInfo(
    val width: Int,
    val height: Int,
    val bytesWritten: Long,
    val outputCount: Int
)

data class MainUiState(
    val currentScreen: Screen = Screen.MAIN,
    val selectedImages: List<ImageItem> = emptyList(),
    val settings: MochiStitchSettings = MochiStitchSettings(),
    val isProcessing: Boolean = false,
    val progress: Float = 0f,
    val exportResult: ExportResultInfo? = null,
    val resultOutputUri: Uri? = null,
    val errorMessage: String? = null
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

    fun clearAll() {
        _uiState.update { it.copy(selectedImages = emptyList()) }
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

    fun startMerge(outputUri: Uri, context: Context) {
        initSettings(context)
        val images = _uiState.value.selectedImages
        if (images.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "No images selected to merge.") }
            return
        }

        val settings = _uiState.value.settings

        _uiState.update {
            it.copy(
                isProcessing = true,
                progress = 0f,
                errorMessage = null,
                exportResult = null,
                resultOutputUri = null
            )
        }

        viewModelScope.launch {
            val processor = StitchProcessor(context)
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

                val result = processor.process(
                    imageUris = images.map { it.uri },
                    settings = settings,
                    onProgress = { progress ->
                        _uiState.update { state -> state.copy(progress = progress) }
                    }
                )

                if (result.isFailure) {
                    outputStream.close()
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            errorMessage = result.exceptionOrNull()?.message ?: "Failed to process image stitching."
                        )
                    }
                    return@launch
                }

                val items = result.getOrThrow()
                var bytesWritten = 0L

                if (settings.wrapperFormat == OutputWrapperFormat.CBZ || settings.wrapperFormat == OutputWrapperFormat.ZIP) {
                    val archiveEntries = items.map { item ->
                        val baos = ByteArrayOutputStream()
                        ImageCompressor.compress(
                            bitmap = item.bitmap,
                            format = settings.outputFormat,
                            quality = if (settings.outputFormat == OutputFormat.JPG) settings.jpgQuality else settings.webpQuality,
                            webpLossless = settings.webpLossless,
                            outputStream = baos
                        )
                        ArchiveEntry(item.filename, baos.toByteArray())
                    }
                    bytesWritten = ArchiveHandler.createArchive(archiveEntries, outputStream)
                } else {
                    val byteCountingStream = ByteCountingOutputStream(outputStream)
                    for (item in items) {
                        ImageCompressor.compress(
                            bitmap = item.bitmap,
                            format = settings.outputFormat,
                            quality = if (settings.outputFormat == OutputFormat.JPG) settings.jpgQuality else settings.webpQuality,
                            webpLossless = settings.webpLossless,
                            outputStream = byteCountingStream
                        )
                    }
                    byteCountingStream.flush()
                    bytesWritten = byteCountingStream.bytesWritten
                    outputStream.close()
                }

                val maxW = items.maxOfOrNull { it.width } ?: 0
                val totalH = items.sumOf { it.height }
                processor.recycleAll(items)

                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        exportResult = ExportResultInfo(
                            width = maxW,
                            height = totalH,
                            bytesWritten = bytesWritten,
                            outputCount = items.size
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
