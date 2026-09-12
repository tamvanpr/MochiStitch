package com.mochistitch.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Screen {
    MAIN, SETTINGS, PREVIEW
}

data class ExportResultInfo(
    val width: Int,
    val height: Int,
    val bytesWritten: Long,
    val outputCount: Int,
    val itemsNeedingManualReview: Int = 0,
    val exportFolderPath: String? = null
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
    val userMessage: String? = null
)

class MainViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var settingsRepository: MochiStitchSettingsRepository? = null

    fun initSettings(context: Context) {
        if (settingsRepository == null) {
            val repo = MochiStitchSettingsRepository(context)
            settingsRepository = repo
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    com.mochistitch.core.mochismart.ContourDetector.initOpenCV()
                } catch (t: Throwable) {
                    // Ignore background pre-warm failure
                }
                val initialSettings = repo.settingsFlow.first()
                _uiState.update { it.copy(settings = initialSettings) }
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
        viewModelScope.launch(Dispatchers.IO) {
            val currentUris = _uiState.value.selectedImages.map { it.uri.toString() }.toSet()
            val distinctUris = uris.distinctBy { it.toString() }
            val newUris = distinctUris.filterNot { currentUris.contains(it.toString()) }
            val duplicateCount = uris.size - newUris.size

            if (newUris.isEmpty()) {
                if (duplicateCount > 0) {
                    _uiState.update { it.copy(userMessage = "Gambar duplikat diabaikan ($duplicateCount gambar)") }
                }
                return@launch
            }

            val newItems = newUris.map { uri ->
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: SecurityException) {
                    // Ignore
                }
                val fileName = queryFileName(context, uri) ?: uri.lastPathSegment ?: "Gambar"
                ImageItem(uri = uri, name = fileName)
            }

            _uiState.update { state ->
                val msg = if (duplicateCount > 0) "Gambar duplikat diabaikan ($duplicateCount gambar)" else null
                state.copy(
                    selectedImages = state.selectedImages + newItems,
                    userMessage = msg
                )
            }
        }
    }

    fun dismissUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
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
        clearPreviewSlices()
        _uiState.update { it.copy(selectedImages = emptyList()) }
    }

    fun clearPreviewSlices() {
        val currentSlices = _uiState.value.previewSlices
        currentSlices.forEach { slice ->
            slice.cacheFilePath?.let { path ->
                try { File(path).delete() } catch (t: Throwable) {}
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
            _uiState.update { it.copy(errorMessage = "Tidak ada gambar yang dipilih untuk digabungkan.") }
            return
        }

        clearPreviewSlices()

        val settings = _uiState.value.settings

        _uiState.update {
            it.copy(
                isProcessing = true,
                processingStep = "Menata alur gambar",
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
                            errorMessage = run {
                                val ex = result.exceptionOrNull()
                                val isOom = ex is OutOfMemoryError || (ex?.message?.contains("OutOfMemory", ignoreCase = true) == true)
                                if (isOom) "Gagal membuat pratinjau: Memori tidak cukup untuk memproses gambar." else ex?.message ?: "Gagal memproses penggabungan gambar."
                            }
                        )
                    }
                    return@launch
                }

                val items = result.getOrThrow()
                val extension = ImageCompressor.getFileExtension(settings.outputFormat)
                val previewSlices = items.map { item ->
                    PreviewSliceItem(
                        index = item.index,
                        filename = "${item.filename}.${extension}",
                        bitmap = item.previewBitmap,
                        cacheFilePath = item.cacheFile.absolutePath,
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
            } catch (e: Throwable) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        errorMessage = run {
                            val isOom = e is OutOfMemoryError || (e.message?.contains("OutOfMemory", ignoreCase = true) == true)
                            if (isOom) "Gagal membuat pratinjau: Memori tidak cukup untuk memproses gambar." else e.message ?: "Gagal membuat pratinjau gambar."
                        }
                    )
                }
            }
        }
    }

    fun exportResult(context: Context) {
        val previewSlices = _uiState.value.previewSlices
        if (previewSlices.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Tidak ada pratinjau yang tersedia untuk diekspor.") }
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
                var bytesWritten = 0L
                val maxW = previewSlices.maxOfOrNull { it.width } ?: 0
                val totalH = previewSlices.sumOf { it.height }
                val manualReviewCount = previewSlices.count { it.needsManualReview }

                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val mochistitchDir = File(picturesDir, "MochiStitch")
                if (!mochistitchDir.exists()) mochistitchDir.mkdirs()

                val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                val timestamp = dateFormat.format(Date())

                if (settings.wrapperFormat == OutputWrapperFormat.CBZ || settings.wrapperFormat == OutputWrapperFormat.ZIP) {
                    val archiveName = getExportDefaultFilename().let { name ->
                        val extension = when (settings.wrapperFormat) {
                            OutputWrapperFormat.CBZ -> ".cbz"
                            OutputWrapperFormat.ZIP -> ".zip"
                            else -> ".zip"
                        }
                        val baseName = name.substringBeforeLast(".")
                        "${baseName}_$timestamp$extension"
                    }
                    val outputArchiveFile = File(mochistitchDir, archiveName)

                    val archiveEntries = previewSlices.mapIndexed { index, slice ->
                        _uiState.update { it.copy(progress = (index + 1).toFloat() / previewSlices.size.toFloat()) }
                        ArchiveEntry(slice.filename) { out ->
                            val srcFile = slice.cacheFilePath?.let { File(it) }
                            if (srcFile != null && srcFile.exists()) {
                                srcFile.inputStream().use { input ->
                                    input.copyTo(out)
                                }
                            }
                        }
                    }
                    bytesWritten = ArchiveHandler.createArchive(archiveEntries, outputArchiveFile.outputStream())

                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            exportResult = ExportResultInfo(
                                width = maxW, height = totalH, bytesWritten = bytesWritten,
                                outputCount = previewSlices.size,
                                itemsNeedingManualReview = manualReviewCount,
                                exportFolderPath = outputArchiveFile.absolutePath
                            ),
                            resultOutputUri = null
                        )
                    }
                } else {
                    val outputFolder = File(mochistitchDir, "export_$timestamp")
                    if (!outputFolder.exists()) outputFolder.mkdirs()

                    previewSlices.forEachIndexed { index, slice ->
                        _uiState.update { it.copy(progress = (index + 1).toFloat() / previewSlices.size.toFloat()) }
                        val destFile = File(outputFolder, slice.filename)
                        val srcFile = slice.cacheFilePath?.let { File(it) }
                        if (srcFile != null && srcFile.exists()) {
                            srcFile.copyTo(destFile, overwrite = true)
                            bytesWritten += destFile.length()
                        }
                    }

                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            exportResult = ExportResultInfo(
                                width = maxW, height = totalH, bytesWritten = bytesWritten,
                                outputCount = previewSlices.size,
                                itemsNeedingManualReview = manualReviewCount,
                                exportFolderPath = outputFolder.absolutePath
                            )
                        )
                    }
                }
            } catch (e: Throwable) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        errorMessage = run {
                            val isOom = e is OutOfMemoryError || (e.message?.contains("OutOfMemory", ignoreCase = true) == true)
                            if (isOom) "Gagal menyimpan: Memori tidak cukup untuk memproses gambar." else e.message ?: "Gagal melakukan proses ekspor."
                        }
                    )
                }
            }
        }
    }

    fun exportLooseFiles(context: Context) {
        exportResult(context)
    }

    fun dismissResult() {
        _uiState.update { it.copy(exportResult = null, resultOutputUri = null) }
    }

    fun shareExportedFolder(context: Context, folderPath: String) {
        val folder = File(folderPath)
        if (!folder.exists() || !folder.isDirectory) {
            _uiState.update { it.copy(errorMessage = "Folder ekspor tidak ditemukan.") }
            return
        }

        val files = folder.listFiles { _, name ->
            name.lowercase().endsWith(".jpg") ||
                    name.lowercase().endsWith(".jpeg") ||
                    name.lowercase().endsWith(".png") ||
                    name.lowercase().endsWith(".webp")
        }?.toList() ?: emptyList()

        if (files.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Tidak ada berkas gambar yang ditemukan di folder ekspor.") }
            return
        }

        val fileUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", files[0])

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, fileUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_SUBJECT, "MochiStitch Export")
            putExtra(Intent.EXTRA_TEXT, "Hasil ekspor dari MochiStitch: ${folder.absolutePath}")
        }

        val chooser = Intent.createChooser(shareIntent, "Bagikan hasil ekspor MochiStitch")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
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
}
