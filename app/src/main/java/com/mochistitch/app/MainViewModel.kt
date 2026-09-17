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
import com.mochistitch.core.common.StitchProject
import com.mochistitch.core.imaging.FilenameFormatter
import com.mochistitch.core.imaging.ImageCompressor
import com.mochistitch.core.imaging.ProcessingStage
import com.mochistitch.core.imaging.StitchProcessor
import com.mochistitch.core.imaging.StitchResultItem
import com.mochistitch.core.settings.MochiStitchSettings
import com.mochistitch.core.settings.MochiStitchSettingsRepository
import com.mochistitch.core.settings.OutputWrapperFormat
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Screen { MAIN, SETTINGS, PREVIEW }

data class ExportResultInfo(
    val width: Int,
    val height: Int,
    val bytesWritten: Long,
    val outputCount: Int,
    val itemsNeedingManualReview: Int = 0,
    val exportFolderPath: String? = null
)

data class BulkExportResult(
    val projectName: String,
    val outputPath: String?,
    val outputCount: Int,
    val bytesWritten: Long,
    val errorMessage: String? = null
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
    val errorMessage: String? = null,
    val userMessage: String? = null,
    val projects: List<StitchProject> = emptyList(),
    val activeProjectId: String? = null,
    val activeSourceName: String? = null,
    val bulkResults: List<BulkExportResult> = emptyList()
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
                val initial = repo.settingsFlow.first()
                _uiState.update { it.copy(settings = initial) }
                repo.settingsFlow.collect { s -> _uiState.update { it.copy(settings = s) } }
            }
        }
    }

    fun navigateTo(screen: Screen) {
        _uiState.update { it.copy(currentScreen = screen) }
    }

    fun saveSettings(settings: MochiStitchSettings) {
        _uiState.update { it.copy(settings = settings) }
        viewModelScope.launch { settingsRepository?.updateSettings(settings) }
    }

    fun addImages(uris: List<Uri>, context: Context) {
        initSettings(context)
        viewModelScope.launch(Dispatchers.IO) {
            val known = _uiState.value.selectedImages.map { it.uri.toString() }.toSet()
            val fresh = uris.distinctBy { it.toString() }.filterNot { known.contains(it.toString()) }
            val dupes = uris.size - fresh.size
            if (fresh.isEmpty()) {
                if (dupes > 0) _uiState.update { it.copy(userMessage = "Gambar duplikat diabaikan ($dupes)") }
                return@launch
            }
            val items = fresh.map { uri ->
                try {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: SecurityException) {
                    // Abaikan.
                }
                ImageItem(uri = uri, name = queryFileName(context, uri) ?: uri.lastPathSegment ?: "Gambar")
            }
            _uiState.update { s ->
                s.copy(
                    selectedImages = s.selectedImages + items,
                    userMessage = if (dupes > 0) "Gambar duplikat diabaikan ($dupes)" else null
                )
            }
        }
    }

    fun moveUp(index: Int) {
        if (index <= 0) return
        _uiState.update { s ->
            val list = s.selectedImages.toMutableList()
            val item = list.removeAt(index)
            list.add(index - 1, item)
            s.copy(selectedImages = list)
        }
    }

    fun moveDown(index: Int) {
        _uiState.update { s ->
            val list = s.selectedImages.toMutableList()
            if (index < 0 || index >= list.size - 1) return@update s
            val item = list.removeAt(index)
            list.add(index + 1, item)
            s.copy(selectedImages = list)
        }
    }

    fun remove(index: Int) {
        _uiState.update { s ->
            val list = s.selectedImages.toMutableList()
            if (index in list.indices) list.removeAt(index)
            s.copy(selectedImages = list)
        }
    }

    fun clearAll() {
        clearPreviewSlices()
        _uiState.update { it.copy(selectedImages = emptyList(), activeSourceName = null) }
    }

    fun dismissUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun dismissResult() {
        _uiState.update { it.copy(exportResult = null) }
    }

    fun dismissBulkResults() {
        _uiState.update { it.copy(bulkResults = emptyList()) }
    }

    // ── Bulk projek ──────────────────────────────────────────────────

    fun saveCurrentAsProject(sourceName: String) {
        val images = _uiState.value.selectedImages
        if (images.isEmpty()) {
            _uiState.update { it.copy(userMessage = "Tidak ada gambar untuk dijadikan projek.") }
            return
        }
        val project = StitchProject(
            sourceName = sourceName.ifBlank { "projek-${_uiState.value.projects.size + 1}" },
            imageUris = images.map { it.uri },
            imageNames = images.map { it.name }
        )
        _uiState.update { s ->
            s.copy(
                projects = s.projects + project,
                activeProjectId = project.id,
                activeSourceName = project.sourceName,
                userMessage = "Projek \"${project.sourceName}\" disimpan (${images.size} gambar)."
            )
        }
    }

    fun importArchive(uri: Uri, context: Context) {
        initSettings(context)
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isProcessing = true, processingStep = "Mengekstrak arsip", progress = 0f, errorMessage = null) }
            try {
                val displayName = queryFileName(context, uri)
                    ?: uri.lastPathSegment?.substringAfterLast('/') ?: "arsip"
                if (!ArchiveHandler.isSupportedArchive(displayName)) {
                    _uiState.update { it.copy(isProcessing = false, errorMessage = "Format arsip tidak didukung: $displayName") }
                    return@launch
                }
                try {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: SecurityException) {
                    // Abaikan.
                }
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("Tidak dapat membuka arsip: $displayName")
                val extracted = input.use { ArchiveHandler.extractImages(it, displayName) }
                if (extracted.isEmpty()) {
                    _uiState.update { it.copy(isProcessing = false, errorMessage = "Tidak ada gambar di dalam $displayName.") }
                    return@launch
                }
                val stem = ArchiveHandler.stripKnownExtension(displayName).ifBlank { "arsip" }
                val importDir = File(File(context.cacheDir, "mochi_import"), stem).apply { mkdirs() }
                val items = extracted.mapIndexed { index, img ->
                    val dest = File(importDir, "%03d_%s".format(index + 1, img.name.ifBlank { "halaman.png" }))
                    dest.outputStream().use { out -> out.write(img.bytes) }
                    ImageItem(uri = Uri.fromFile(dest), name = img.name)
                }
                val project = StitchProject(
                    sourceName = displayName,
                    imageUris = items.map { it.uri },
                    imageNames = items.map { it.name }
                )
                _uiState.update { s ->
                    s.copy(
                        isProcessing = false,
                        selectedImages = items,
                        projects = s.projects + project,
                        activeProjectId = project.id,
                        activeSourceName = displayName,
                        userMessage = "Arsip \"$displayName\" diimpor (${items.size} gambar)."
                    )
                }
            } catch (e: Throwable) {
                _uiState.update { it.copy(isProcessing = false, errorMessage = e.message ?: "Gagal mengimpor arsip.") }
            }
        }
    }

    fun loadProject(id: String) {
        val project = _uiState.value.projects.find { it.id == id } ?: return
        clearPreviewSlices()
        val items = project.imageUris.mapIndexed { i, uri ->
            ImageItem(uri = uri, name = project.imageNames.getOrElse(i) { "Halaman ${i + 1}" })
        }
        _uiState.update {
            it.copy(selectedImages = items, activeProjectId = project.id, activeSourceName = project.sourceName)
        }
    }

    fun updateProjectWrapper(id: String, wrapper: OutputWrapperFormat?) {
        _uiState.update { s ->
            s.copy(projects = s.projects.map { if (it.id == id) it.copy(wrapperOverride = wrapper) else it })
        }
    }

    fun deleteProject(id: String) {
        _uiState.update { s ->
            val remaining = s.projects.filterNot { it.id == id }
            val cleared = s.activeProjectId == id
            s.copy(
                projects = remaining,
                activeProjectId = if (cleared) null else s.activeProjectId,
                activeSourceName = if (cleared) null else s.activeSourceName
            )
        }
    }

    fun processAllProjects(context: Context) {
        initSettings(context)
        val projects = _uiState.value.projects.filter { it.imageUris.isNotEmpty() }
        if (projects.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Tidak ada projek bulk untuk diproses.") }
            return
        }
        _uiState.update { it.copy(isProcessing = true, processingStep = "Memproses bulk", progress = 0f, errorMessage = null, bulkResults = emptyList()) }
        viewModelScope.launch {
            val results = mutableListOf<BulkExportResult>()
            val base = _uiState.value.settings
            val processor = StitchProcessor(context)
            projects.forEachIndexed { pi, project ->
                _uiState.update { s ->
                    s.copy(
                        processingStep = "Memproses ${project.sourceName} (${pi + 1}/${projects.size})",
                        progress = pi.toFloat() / projects.size.toFloat()
                    )
                }
                val wrapper = project.effectiveWrapper(base.wrapperFormat)
                val ps = base.copy(wrapperFormat = wrapper)
                try {
                    val processed = processor.process(
                        imageUris = project.imageUris,
                        settings = ps,
                        onProgress = { _, p ->
                            _uiState.update { it.copy(progress = (pi + p) / projects.size.toFloat()) }
                        }
                    ).getOrThrow()
                    val ext = ImageCompressor.getFileExtension(ps.outputFormat)
                    val info = exportItemsToDisk(
                        files = processed.map { "${it.filename}.$ext" to it.cacheFile },
                        sourceName = project.sourceName,
                        wrapper = wrapper,
                        settings = ps,
                        width = processed.maxOfOrNull { it.width } ?: 0,
                        height = processed.sumOf { it.height },
                        manualReviewCount = processed.count { it.needsManualReview }
                    )
                    processed.forEach { item ->
                        try { item.cacheFile.delete() } catch (t: Throwable) { }
                    }
                    results.add(
                        BulkExportResult(project.sourceName, info.exportFolderPath, info.outputCount, info.bytesWritten)
                    )
                } catch (e: Throwable) {
                    val oom = e is OutOfMemoryError || (e.message?.contains("OutOfMemory", ignoreCase = true) == true)
                    results.add(
                        BulkExportResult(project.sourceName, null, 0, 0L, if (oom) "Memori tidak cukup." else (e.message ?: "Gagal memproses."))
                    )
                }
            }
            _uiState.update { it.copy(isProcessing = false, progress = 1f, bulkResults = results) }
        }
    }

    // ── Pratinjau & ekspor ───────────────────────────────────────────

    fun clearPreviewSlices() {
        _uiState.value.previewSlices.forEach { slice ->
            slice.cacheFilePath?.let { path ->
                try { File(path).delete() } catch (t: Throwable) { }
            }
        }
        _uiState.update { it.copy(previewSlices = emptyList()) }
    }

    fun getExportMimeType(): String {
        return when (_uiState.value.settings.wrapperFormat) {
            OutputWrapperFormat.CBZ -> "application/x-cbz"
            OutputWrapperFormat.ZIP -> "application/zip"
            OutputWrapperFormat.LOOSE_FILES -> ImageCompressor.getMimeType(_uiState.value.settings.outputFormat)
        }
    }

    fun getExportDefaultFilename(): String {
        val s = _uiState.value.settings
        val project = s.projectName.ifBlank { "MochiStitch" }
        val chapter = s.chapterName.ifBlank { "1" }
        return when (s.wrapperFormat) {
            OutputWrapperFormat.CBZ -> "${project}_ch${chapter}.cbz"
            OutputWrapperFormat.ZIP -> "${project}_ch${chapter}.zip"
            OutputWrapperFormat.LOOSE_FILES -> FilenameFormatter.formatFilename(
                s.filenameTemplate, project, chapter, 1, s.indexPaddingDigits, s.outputFormat
            )
        }
    }

    fun generatePreview(context: Context) {
        initSettings(context)
        if (_uiState.value.selectedImages.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Tidak ada gambar yang dipilih.") }
            return
        }
        clearPreviewSlices()
        _uiState.update {
            it.copy(isProcessing = true, processingStep = "Menata halaman", progress = 0f, errorMessage = null, exportResult = null)
        }
        viewModelScope.launch {
            val processor = StitchProcessor(context)
            try {
                val settings = _uiState.value.settings
                val result = processor.process(
                    imageUris = _uiState.value.selectedImages.map { it.uri },
                    settings = settings,
                    onProgress = { stage, p -> _uiState.update { it.copy(processingStep = stage.stepName, progress = p) } }
                )
                if (result.isFailure) throw result.exceptionOrNull() ?: IllegalStateException("Gagal memproses.")
                val ext = ImageCompressor.getFileExtension(settings.outputFormat)
                val slices = result.getOrThrow().map { item ->
                    PreviewSliceItem(
                        index = item.index,
                        filename = "${item.filename}.$ext",
                        bitmap = item.previewBitmap,
                        cacheFilePath = item.cacheFile.absolutePath,
                        width = item.width,
                        height = item.height,
                        needsManualReview = item.needsManualReview,
                        reviewReason = item.reviewReason,
                        bytesWritten = item.estimatedBytes
                    )
                }
                _uiState.update { it.copy(isProcessing = false, previewSlices = slices, currentScreen = Screen.PREVIEW) }
            } catch (e: Throwable) {
                val oom = e is OutOfMemoryError || (e.message?.contains("OutOfMemory", ignoreCase = true) == true)
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        errorMessage = if (oom) "Memori tidak cukup untuk memproses gambar." else (e.message ?: "Gagal membuat pratinjau.")
                    )
                }
            }
        }
    }

    fun exportResult(context: Context) {
        val slices = _uiState.value.previewSlices
        if (slices.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Tidak ada pratinjau untuk diekspor.") }
            return
        }
        val settings = _uiState.value.settings
        val sourceName = _uiState.value.activeSourceName
        _uiState.update { it.copy(isProcessing = true, processingStep = ProcessingStage.EXPORTING.stepName, progress = 0f, errorMessage = null) }
        viewModelScope.launch {
            try {
                val info = exportItemsToDisk(
                    files = slices.map { it.filename to it.cacheFilePath?.let { path -> File(path) } },
                    sourceName = sourceName,
                    wrapper = settings.wrapperFormat,
                    settings = settings,
                    width = slices.maxOfOrNull { it.width } ?: 0,
                    height = slices.sumOf { it.height },
                    manualReviewCount = slices.count { it.needsManualReview },
                    onProgress = { p -> _uiState.update { it.copy(progress = p) } }
                )
                _uiState.update { it.copy(isProcessing = false, exportResult = info) }
            } catch (e: Throwable) {
                val oom = e is OutOfMemoryError || (e.message?.contains("OutOfMemory", ignoreCase = true) == true)
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        errorMessage = if (oom) "Gagal menyimpan: memori tidak cukup." else (e.message ?: "Gagal mengekspor.")
                    )
                }
            }
        }
    }

    fun exportLooseFiles(context: Context) {
        exportResult(context)
    }

    /**
     * Menulis potongan ke disk. Aturan nama arsip: sumber arsip didukung
     * ("komik.zip") -> output memakai basename yang SAMA ("komik.zip" /
     * "komik.cbz", tanpa timestamp). Selain itu + timestamp anti-timpa.
     */
    private suspend fun exportItemsToDisk(
        files: List<Pair<String, File?>>,
        sourceName: String?,
        wrapper: OutputWrapperFormat,
        settings: MochiStitchSettings,
        width: Int = 0,
        height: Int = 0,
        manualReviewCount: Int = 0,
        onProgress: suspend (Float) -> Unit = {}
    ): ExportResultInfo = withContext(Dispatchers.IO) {
        var bytesWritten = 0L
        val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val targetDir = File(picturesDir, "MochiStitch").apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())

        if (wrapper == OutputWrapperFormat.CBZ || wrapper == OutputWrapperFormat.ZIP) {
            val archiveName = if (sourceName != null && ArchiveHandler.isSupportedArchive(sourceName)) {
                FilenameFormatter.resolveArchiveOutputName(sourceName, wrapper)
            } else {
                FilenameFormatter.resolveArchiveOutputName(getExportDefaultFilename(), wrapper, timestamp)
            }
            val outFile = File(targetDir, archiveName)
            val entries = files.mapIndexed { i, (name, src) ->
                onProgress((i + 1).toFloat() / files.size.toFloat())
                ArchiveEntry(name) { out ->
                    if (src != null && src.exists()) src.inputStream().use { it.copyTo(out) }
                }
            }
            bytesWritten = ArchiveHandler.createArchive(entries, outFile.outputStream())
            ExportResultInfo(width, height, bytesWritten, files.size, manualReviewCount, outFile.absolutePath)
        } else {
            val folder = File(targetDir, "export_$timestamp").apply { mkdirs() }
            files.forEachIndexed { i, (name, src) ->
                onProgress((i + 1).toFloat() / files.size.toFloat())
                val dest = File(folder, name)
                if (src != null && src.exists()) {
                    src.copyTo(dest, overwrite = true)
                    bytesWritten += dest.length()
                }
            }
            ExportResultInfo(width, height, bytesWritten, files.size, manualReviewCount, folder.absolutePath)
        }
    }

    fun shareExportedFolder(context: Context, folderPath: String) {
        val folder = File(folderPath)
        if (!folder.exists() || !folder.isDirectory) {
            _uiState.update { it.copy(errorMessage = "Folder ekspor tidak ditemukan.") }
            return
        }
        val files = folder.listFiles { _, name ->
            val lower = name.lowercase()
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp")
        }?.toList() ?: emptyList()
        if (files.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Tidak ada gambar di folder ekspor.") }
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", files[0])
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Bagikan hasil MochiStitch").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun onCleared() {
        super.onCleared()
        clearPreviewSlices()
    }

    private fun queryFileName(context: Context, uri: Uri): String? {
        if (uri.scheme != "content") return null
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx != -1) c.getString(idx) else null
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }
}
