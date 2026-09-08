package com.mochistitch.app

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.mochistitch.core.imaging.AlignmentMode
import com.mochistitch.core.imaging.MergeDirection
import com.mochistitch.core.imaging.PaddingColor
import com.mochistitch.core.settings.AlignmentModeSetting
import com.mochistitch.core.settings.PaddingColorSetting
import com.mochistitch.core.settings.OutputWrapperFormat
import com.mochistitch.core.settings.ReadingDirection
import com.mochistitch.core.ui.ImageReorderList
import com.mochistitch.core.ui.MergeSettingsCard
import com.mochistitch.core.ui.PreviewScreenContent
import com.mochistitch.core.ui.SettingsScreenContent
import kotlinx.coroutines.launch
import java.io.File
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

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(viewModel.getExportMimeType())
    ) { uri ->
        if (uri != null) {
            viewModel.exportResult(uri, context)
        }
    }

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
                if (uiState.settings.wrapperFormat == OutputWrapperFormat.LOOSE_FILES) {
                    viewModel.exportLooseFiles(context)
                } else {
                    val filename = viewModel.getExportDefaultFilename()
                    createDocumentLauncher.launch(filename)
                }
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
                snackbarHostState.showSnackbar("Press again to exit")
            }
        }
    }

    val selectImagesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addImages(uris, context)
        }
    }

    val currentDirection = when (uiState.settings.readingDirection) {
        ReadingDirection.VERTICAL -> MergeDirection.VERTICAL
        ReadingDirection.LTR -> MergeDirection.HORIZONTAL_LTR
        ReadingDirection.RTL -> MergeDirection.HORIZONTAL_RTL
    }

    val currentAlignment = when (uiState.settings.alignmentMode) {
        AlignmentModeSetting.RESIZE_PROPORTIONAL -> AlignmentMode.RESIZE_PROPORTIONAL
        AlignmentModeSetting.CENTER_CROP -> AlignmentMode.CENTER_CROP
        AlignmentModeSetting.PADDING -> AlignmentMode.PADDING
    }

    val currentPaddingColor = when (uiState.settings.paddingColor) {
        PaddingColorSetting.WHITE -> PaddingColor.WHITE
        PaddingColorSetting.BLACK -> PaddingColor.BLACK
        PaddingColorSetting.TRANSPARENT -> PaddingColor.TRANSPARENT
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
                                contentDescription = "Clear all",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    IconButton(onClick = { viewModel.navigateTo(Screen.SETTINGS) }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
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
        floatingActionButton = {
            if (uiState.currentScreen == Screen.MAIN && uiState.selectedImages.isEmpty()) {
                FloatingActionButton(
                    onClick = { selectImagesLauncher.launch(arrayOf("image/*")) },
                    modifier = Modifier.padding(end = 16.dp, bottom = 16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add images",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Stitch Settings Card ─────────────────────────────────────
            MergeSettingsCard(
                direction = currentDirection,
                onDirectionChange = { viewModel.updateDirection(it) },
                alignmentMode = currentAlignment,
                onAlignmentModeChange = { viewModel.updateAlignmentMode(it) },
                paddingColor = currentPaddingColor,
                onPaddingColorChange = { viewModel.updatePaddingColor(it) }
            )

            // ── Section header + page count ─────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Selected Pages",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = "${uiState.selectedImages.size} page${if (uiState.selectedImages.size != 1) "s" else ""}",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // ── Image list or empty state ───────────────────────────────
            if (uiState.selectedImages.isEmpty()) {
                EmptyStateBox(
                    onSelectClicked = { selectImagesLauncher.launch(arrayOf("image/*")) }
                )
            } else {
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
                        .height(52.dp),
                    enabled = uiState.selectedImages.isNotEmpty() && !uiState.isProcessing,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (uiState.isProcessing) "Processing..." else "Generate Preview",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            }
        }
    }

    ProcessingProgressDialog(uiState = uiState)
    ExportResultDialogs(uiState = uiState, viewModel = viewModel, context = context)
}

// ── Empty state box ──────────────────────────────────────────────────────────
@Composable
private fun EmptyStateBox(onSelectClicked: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.5f),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        RoundedCornerShape(16.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "No images yet",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = "Add comic pages to stitch them into a long strip",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Button(
                onClick = onSelectClicked,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Select Images")
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
                        text = if (uiState.processingStep.isNotEmpty()) uiState.processingStep else "Processing...",
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
    // Archive export dialog
    if (uiState.exportResult != null && uiState.resultOutputUri != null) {
        val result = uiState.exportResult
        val uri = uiState.resultOutputUri

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
                    Text(text = "Export Complete!")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AsyncImage(
                        model = uri,
                        contentDescription = "Export Result Preview",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )

                    HorizontalDivider()

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        DetailRow("Files", "${result.outputCount}")
                        DetailRow("Dimensions", "${result.width} × ${result.height} px")
                        DetailRow("Size", formatFileSize(result.bytesWritten))
                    }

                    if (result.itemsNeedingManualReview > 0) {
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
                                text = "${result.itemsNeedingManualReview} piece(s) need manual review",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissResult() }) {
                    Text("Done")
                }
            }
        )
    }

    // Loose files export dialog
    if (uiState.exportResult != null && uiState.exportResult!!.exportFolderPath != null) {
        val result = uiState.exportResult!!

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
                    Text(text = "Export Complete!")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        DetailRow("Files", "${result.outputCount}")
                        DetailRow("Dimensions", "${result.width} × ${result.height} px")
                        DetailRow("Size", formatFileSize(result.bytesWritten))
                        if (result.itemsNeedingManualReview > 0) {
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
                                    text = "${result.itemsNeedingManualReview} piece(s) need manual review",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }

                    HorizontalDivider()

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Saved to:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = result.exportFolderPath!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { viewModel.dismissResult() }) {
                        Text("Done")
                    }
                    Button(
                        onClick = { viewModel.shareExportedFolder(context, result.exportFolderPath!!) }
                    ) {
                        Text("Share")
                    }
                }
            }
        )
    }

    if (uiState.errorMessage != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissError() },
            title = { Text("Export Failed") },
            text = { Text(uiState.errorMessage!!) },
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
