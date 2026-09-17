package com.mochistitch.app

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import com.mochistitch.core.settings.OutputWrapperFormat
import com.mochistitch.core.ui.ImageReorderList
import com.mochistitch.core.ui.PreviewScreenContent
import com.mochistitch.core.ui.SettingsScreenContent
import com.mochistitch.core.ui.StitchProject
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onExitApp: () -> Unit = {},
    modifier: Modifier = Modifier
) {
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
            onExportClicked = {
                viewModel.exportResult(context)
            },
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

    // Double-back exit
    BackHandler {
        val currentTime = System.currentTimeMillis()
        if (currentTime - backPressedTime < 2000) {
            onExitApp()
        } else {
            backPressedTime = currentTime
            scope.launch {
                snackbarHostState.showSnackbar("Tekan sekali lagi untuk keluar")
            }
        }
    }

    val userMessage = uiState.userMessage
    androidx.compose.runtime.LaunchedEffect(userMessage) {
        if (userMessage != null) {
            snackbarHostState.showSnackbar(userMessage)
            viewModel.dismissUserMessage()
        }
    }

    val selectImagesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addImages(uris.distinctBy { it.toString() }, context)
        }
    }

    val importArchiveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importArchive(uri, context)
        }
    }

    fun launchArchivePicker() {
        importArchiveLauncher.launch(
            arrayOf(
                "application/zip",
                "application/x-zip-compressed",
                "application/x-cbz",
                "application/x-rar-compressed",
                "application/vnd.rar",
                "application/x-7z-compressed",
                "application/octet-stream",
                "*/*"
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "MochiStitch",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                actions = {
                    if (uiState.selectedImages.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearAll() }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Hapus Semua",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    IconButton(onClick = { viewModel.navigateTo(Screen.SETTINGS) }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Pengaturan",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
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
            // ── Hero ringkas: mode vertikal + output default ZIP ──────
            HeroStrip(
                imageCount = uiState.selectedImages.size,
                projectCount = uiState.projects.size,
                defaultWrapper = uiState.settings.wrapperFormat
            )

            // ── Tombol impor: gambar satuan + arsip (ZIP/CBZ/RAR/CBR) ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { selectImagesLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Gambar")
                }
                Button(
                    onClick = { launchArchivePicker() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Impor Arsip")
                }
            }

            // ── Penanda sumber arsip: nama output = nama input ─────────
            val activeSource = uiState.activeSourceName
            if (activeSource != null && com.mochistitch.core.archive.ArchiveHandler.isSupportedArchive(activeSource)) {
                val outName = com.mochistitch.core.imaging.FilenameFormatter.resolveArchiveOutputName(
                    activeSource, uiState.settings.wrapperFormat
                )
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

            // ── Section header + page count ─────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Halaman Terpilih",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "(${uiState.selectedImages.size})",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (uiState.selectedImages.isNotEmpty()) {
                    TextButton(
                        onClick = { viewModel.saveCurrentAsProject(activeSource ?: "pilihan manual") }
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Jadikan Projek")
                    }
                }
            }

            // ── Image list or empty state ───────────────────────────────
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
                        enabled = uiState.selectedImages.isNotEmpty() && !uiState.isProcessing,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (uiState.isProcessing) "Memproses..." else "Buat Pratinjau",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }

                // ── Daftar projek bulk: output bisa ditentukan sendiri ──
                if (uiState.projects.isNotEmpty()) {
                    BulkProjectSection(
                        projects = uiState.projects,
                        activeProjectId = uiState.activeProjectId,
                        globalWrapper = uiState.settings.wrapperFormat,
                        onLoad = { viewModel.loadProject(it) },
                        onWrapperChange = { id, wrapper -> viewModel.updateProjectWrapper(id, wrapper) },
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

// ── Hero strip ─────────────────────────────────────────────────────────────
@Composable
private fun HeroStrip(
    imageCount: Int,
    projectCount: Int,
    defaultWrapper: OutputWrapperFormat
) {
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
                    text = "Penggabung Vertikal",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "$imageCount gambar • $projectCount projek • Output $wrapperLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

// ── Daftar projek bulk ─────────────────────────────────────────────────────
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
            Text(
                text = "Projek Bulk (${projects.size})",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
            )
            Button(
                onClick = onProcessAll,
                enabled = !isProcessing && projects.any { it.images.isNotEmpty() }
            ) {
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
                                text = project.sourceName,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${project.images.size} hlm",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutputWrapperFormat.entries.forEach { wrapper ->
                                val label = when (wrapper) {
                                    OutputWrapperFormat.LOOSE_FILES -> "Berkas"
                                    OutputWrapperFormat.CBZ -> "CBZ"
                                    OutputWrapperFormat.ZIP -> "ZIP"
                                }
                                val selected = project.effectiveWrapper(globalWrapper) == wrapper
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        // Ketuk opsi aktif lagi = kembali ikut global.
                                        onWrapperChange(project.id, if (selected) null else wrapper)
                                    },
                                    label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            if (project.id != activeProjectId) {
                                TextButton(onClick = { onLoad(project.id) }) { Text("Muat") }
                            }
                            IconButton(onClick = { onDelete(project.id) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Hapus projek",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Dialog hasil bulk ──────────────────────────────────────────────────────
@Composable
private fun BulkResultDialog(
    uiState: MainUiState,
    viewModel: MainViewModel
) {
    if (uiState.bulkResults.isEmpty()) return
    val successCount = uiState.bulkResults.count { it.errorMessage == null }
    AlertDialog(
        onDismissRequest = { viewModel.dismissBulkResults() },
        title = { Text("Bulk Selesai ($successCount/${uiState.bulkResults.size})") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                uiState.bulkResults.forEach { result ->
                    Column {
                        Text(
                            text = result.projectName,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = result.errorMessage ?: "${result.outputCount} berkas • ${formatFileSize(result.bytesWritten)}\n${result.outputPath}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (result.errorMessage == null) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.error
                            }
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

// ── Empty state box ──────────────────────────────────────────────────────────
@Composable
private fun EmptyStateBox(
    onSelectClicked: () -> Unit,
    onArchiveClicked: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.6f),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Belum Ada Gambar",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = "Tambahkan halaman komik untuk digabungkan menjadi alur utuh",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Button(
                onClick = onSelectClicked,
                modifier = Modifier.fillMaxWidth(0.75f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Pilih Gambar")
            }

            OutlinedButton(
                onClick = onArchiveClicked,
                modifier = Modifier.fillMaxWidth(0.75f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Archive,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Impor ZIP / RAR")
            }
        }
    }
}

// ── Progress dialog ──────────────────────────────────────────────────────────
@Composable
private fun ProcessingProgressDialog(uiState: MainUiState) {
    if (uiState.isProcessing) {
        Dialog(onDismissRequest = {}) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.padding(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (uiState.processingStep.isNotEmpty()) uiState.processingStep else "Memproses...",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    LinearProgressIndicator(
                        progress = { uiState.progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "${(uiState.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ── Export result dialog ─────────────────────────────────────────────────────
@Composable
private fun ExportResultDialogs(
    uiState: MainUiState,
    viewModel: MainViewModel,
    context: android.content.Context
) {
    val exportResult = uiState.exportResult

    // Archive export dialog
    if (exportResult != null && uiState.resultOutputUri != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissResult() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Ekspor Selesai!")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        DetailRow("Jumlah Berkas", "${exportResult.outputCount}")
                        DetailRow("Dimensi", "${exportResult.width} × ${exportResult.height} px")
                        DetailRow("Ukuran Berkas", formatFileSize(exportResult.bytesWritten))
                    }

                    if (exportResult.itemsNeedingManualReview > 0) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${exportResult.itemsNeedingManualReview} bagian perlu peninjauan manual",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissResult() }) {
                    Text("Selesai")
                }
            }
        )
    }

    // Loose files export dialog
    val folderPath = exportResult?.exportFolderPath
    if (exportResult != null && folderPath != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissResult() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Ekspor Selesai!")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        DetailRow("Jumlah Berkas", "${exportResult.outputCount}")
                        DetailRow("Dimensi", "${exportResult.width} × ${exportResult.height} px")
                        DetailRow("Ukuran Berkas", formatFileSize(exportResult.bytesWritten))
                        if (exportResult.itemsNeedingManualReview > 0) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(8.dp))
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${exportResult.itemsNeedingManualReview} bagian perlu peninjauan manual",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }

                    HorizontalDivider()

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Disimpan di:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = folderPath,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { viewModel.dismissResult() }) {
                        Text("Selesai")
                    }
                    Button(
                        onClick = { viewModel.shareExportedFolder(context, folderPath) }
                    ) {
                        Text("Bagikan")
                    }
                }
            }
        )
    }

    val errorMessage = uiState.errorMessage
    if (errorMessage != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissError() },
            title = { Text("Proses Gagal") },
            text = { Text(errorMessage) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissError() }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
        )
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
    val pre = "KMGTPE"[exp - 1]
    return String.format(Locale.US, "%.1f %cB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
}
