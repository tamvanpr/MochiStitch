package com.mochistitch.app

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.mochistitch.core.archive.ArchiveHandler
import com.mochistitch.core.common.StitchProject
import com.mochistitch.core.imaging.FilenameFormatter
import com.mochistitch.core.settings.OutputWrapperFormat
import com.mochistitch.core.ui.ImageReorderList
import com.mochistitch.core.ui.PreviewScreenContent
import com.mochistitch.core.ui.SettingsScreenContent
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel, onExitApp: () -> Unit = {}, modifier: Modifier = Modifier) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var backPressedTime by remember { mutableStateOf(0L) }

    if (uiState.currentScreen == Screen.SETTINGS) {
        SettingsScreenContent(
            settings = uiState.settings,
            onSettingsChanged = { viewModel.saveSettings(it) },
            onBackClicked = { viewModel.navigateTo(Screen.MAIN) },
            modifier = modifier
        )
        return
    }

    if (uiState.currentScreen == Screen.PREVIEW) {
        PreviewScreenContent(
            slices = uiState.previewSlices,
            showReviewMarkers = uiState.settings.showManualReviewMarkers,
            onExportClicked = { viewModel.exportResult(context) },
            onBackClicked = {
                viewModel.clearPreviewSlices()
                viewModel.navigateTo(Screen.MAIN)
            },
            modifier = modifier
        )
        ExportResultDialogs(uiState = uiState, viewModel = viewModel, context = context)
        ProcessingProgressDialog(uiState = uiState)
        return
    }

    BackHandler {
        val now = System.currentTimeMillis()
        if (now - backPressedTime < 2000) onExitApp() else {
            backPressedTime = now
            scope.launch { snackbarHostState.showSnackbar("Tekan sekali lagi untuk keluar") }
        }
    }

    val userMessage = uiState.userMessage
    LaunchedEffect(userMessage) {
        if (userMessage != null) {
            snackbarHostState.showSnackbar(userMessage)
            viewModel.dismissUserMessage()
        }
    }

    val selectImagesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.addImages(uris.distinctBy { it.toString() }, context)
    }
    val importArchiveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importArchive(uri, context)
    }
    fun launchArchivePicker() {
        importArchiveLauncher.launch(
            arrayOf(
                "application/zip", "application/x-zip-compressed", "application/x-cbz",
                "application/x-rar-compressed", "application/vnd.rar",
                "application/x-7z-compressed", "application/octet-stream", "*/*"
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MochiStitch", fontWeight = FontWeight.Bold) },
                actions = {
                    if (uiState.selectedImages.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearAll() }) {
                            Icon(Icons.Default.Clear, contentDescription = "Hapus semua", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    IconButton(onClick = { viewModel.navigateTo(Screen.SETTINGS) }) {
                        Icon(Icons.Default.Settings, contentDescription = "Pengaturan", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HeroStrip(
                imageCount = uiState.selectedImages.size,
                projectCount = uiState.projects.size,
                defaultWrapper = uiState.settings.wrapperFormat
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { selectImagesLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Gambar")
                }
                Button(
                    onClick = { launchArchivePicker() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Impor Arsip")
                }
            }

            val activeSource = uiState.activeSourceName
            if (activeSource != null && ArchiveHandler.isSupportedArchive(activeSource)) {
                val outName = FilenameFormatter.resolveArchiveOutputName(activeSource, uiState.settings.wrapperFormat)
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Sumber: $activeSource → output: $outName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Halaman Terpilih", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "(${uiState.selectedImages.size})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (uiState.selectedImages.isNotEmpty()) {
                    TextButton(onClick = { viewModel.saveCurrentAsProject(activeSource ?: "pilihan manual") }) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Jadikan Projek")
                    }
                }
            }

            if (uiState.selectedImages.isEmpty() && uiState.projects.isEmpty()) {
                EmptyStateBox(
                    onSelectClicked = { selectImagesLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onArchiveClicked = { launchArchivePicker() }
                )
            } else {
                if (uiState.selectedImages.isNotEmpty()) {
                    ImageReorderList(
                        items = uiState.selectedImages,
                        onMoveUp = { viewModel.moveUp(it) },
                        onMoveDown = { viewModel.moveDown(it) },
                        onRemove = { viewModel.remove(it) },
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { viewModel.generatePreview(context) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        enabled = !uiState.isProcessing,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            if (uiState.isProcessing) "Memproses..." else "Buat Pratinjau",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                if (uiState.projects.isNotEmpty()) {
                    BulkProjectSection(
                        projects = uiState.projects,
                        activeProjectId = uiState.activeProjectId,
                        globalWrapper = uiState.settings.wrapperFormat,
                        onLoad = { viewModel.loadProject(it) },
                        onWrapperChange = { id, w -> viewModel.updateProjectWrapper(id, w) },
                        onDelete = { viewModel.deleteProject(it) },
                        onProcessAll = { viewModel.processAllProjects(context) },
                        isProcessing = uiState.isProcessing
                    )
                }
            }
        }
    }

    ProcessingProgressDialog(uiState = uiState)
    ExportResultDialogs(uiState = uiState, viewModel = viewModel, context = context)
    BulkResultDialog(uiState = uiState, viewModel = viewModel)
}

@Composable
private fun HeroStrip(imageCount: Int, projectCount: Int, defaultWrapper: OutputWrapperFormat) {
    val wrapperLabel = when (defaultWrapper) {
        OutputWrapperFormat.ZIP -> "ZIP"
        OutputWrapperFormat.CBZ -> "CBZ"
        OutputWrapperFormat.LOOSE_FILES -> "Berkas"
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Penggabung Vertikal",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    "$imageCount gambar • $projectCount projek • Output $wrapperLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun EmptyStateBox(onSelectClicked: () -> Unit, onArchiveClicked: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Belum Ada Gambar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "Pilih halaman komik atau impor arsip — potongan selalu di batas halaman, tidak pernah di tengah konten.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Button(onClick = onSelectClicked, modifier = Modifier.fillMaxWidth(0.75f), shape = RoundedCornerShape(12.dp)) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Pilih Gambar")
        }
        OutlinedButton(onClick = onArchiveClicked, modifier = Modifier.fillMaxWidth(0.75f), shape = RoundedCornerShape(12.dp)) {
            Icon(Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Impor ZIP / RAR")
        }
    }
}

@Composable
private fun BulkProjectSection(
    projects: List<StitchProject>,
    activeProjectId: String?,
    globalWrapper: OutputWrapperFormat,
    onLoad: (String) -> Unit,
    onWrapperChange: (String, OutputWrapperFormat?) -> Unit,
    onDelete: (String) -> Unit,
    onProcessAll: () -> Unit,
    isProcessing: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Projek Bulk (${projects.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Button(onClick = onProcessAll, enabled = !isProcessing && projects.any { it.imageUris.isNotEmpty() }) {
                Text("Proses Semua")
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 220.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            projects.forEach { project ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (project.id == activeProjectId) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                project.sourceName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "${project.imageUris.size} hlm",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutputWrapperFormat.entries.forEach { wrapper ->
                                val label = when (wrapper) {
                                    OutputWrapperFormat.LOOSE_FILES -> "Berkas"
                                    OutputWrapperFormat.CBZ -> "CBZ"
                                    OutputWrapperFormat.ZIP -> "ZIP"
                                }
                                val selected = project.effectiveWrapper(globalWrapper) == wrapper
                                FilterChip(
                                    selected = selected,
                                    onClick = { onWrapperChange(project.id, if (selected) null else wrapper) },
                                    label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            if (project.id != activeProjectId) {
                                TextButton(onClick = { onLoad(project.id) }) { Text("Muat") }
                            }
                            IconButton(onClick = { onDelete(project.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BulkResultDialog(uiState: MainUiState, viewModel: MainViewModel) {
    if (uiState.bulkResults.isEmpty()) return
    val ok = uiState.bulkResults.count { it.errorMessage == null }
    AlertDialog(
        onDismissRequest = { viewModel.dismissBulkResults() },
        title = { Text("Bulk Selesai ($ok/${uiState.bulkResults.size})") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                uiState.bulkResults.forEach { result ->
                    Column {
                        Text(result.projectName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            result.errorMessage ?: "${result.outputCount} berkas • ${formatFileSize(result.bytesWritten)}\n${result.outputPath}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (result.errorMessage == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                        )
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { viewModel.dismissBulkResults() }) { Text("Selesai") }
        }
    )
}

@Composable
private fun ProcessingProgressDialog(uiState: MainUiState) {
    if (!uiState.isProcessing) return
    Dialog(onDismissRequest = {}) {
        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.padding(24.dp)) {
            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (uiState.processingStep.isNotEmpty()) uiState.processingStep else "Memproses...",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                LinearProgressIndicator(progress = { uiState.progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Text("${(uiState.progress * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ExportResultDialogs(uiState: MainUiState, viewModel: MainViewModel, context: android.content.Context) {
    val result = uiState.exportResult
    if (result != null && result.exportFolderPath != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissResult() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Ekspor Selesai!")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DetailRow("Jumlah Berkas", "${result.outputCount}")
                    DetailRow("Dimensi", "${result.width} × ${result.height} px")
                    DetailRow("Ukuran", formatFileSize(result.bytesWritten))
                    if (result.itemsNeedingManualReview > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "${result.itemsNeedingManualReview} bagian perlu tinjauan manual",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    HorizontalDivider()
                    Text("Disimpan di:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(result.exportFolderPath, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { viewModel.dismissResult() }) { Text("Selesai") }
                    Button(onClick = { viewModel.shareExportedFolder(context, result.exportFolderPath) }) { Text("Bagikan") }
                }
            }
        )
    }
    val error = uiState.errorMessage
    if (error != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissError() },
            title = { Text("Proses Gagal") },
            text = { Text(error) },
            confirmButton = { TextButton(onClick = { viewModel.dismissError() }) { Text("OK") } }
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
    val pre = "KMGTPE"[exp - 1]
    return String.format(Locale.US, "%.1f %cB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
}
