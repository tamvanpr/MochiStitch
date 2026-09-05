package com.mochistitch.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.mochistitch.core.imaging.AlignmentMode
import com.mochistitch.core.imaging.MergeDirection
import com.mochistitch.core.imaging.PaddingColor
import com.mochistitch.core.settings.AlignmentModeSetting
import com.mochistitch.core.settings.PaddingColorSetting
import com.mochistitch.core.settings.ReadingDirection
import com.mochistitch.core.ui.ImageReorderList
import com.mochistitch.core.ui.MergeSettingsCard
import com.mochistitch.core.ui.PreviewScreenContent
import com.mochistitch.core.ui.SettingsScreenContent
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.initSettings(context)
    }

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
                val filename = viewModel.getExportDefaultFilename()
                createDocumentLauncher.launch(filename)
            },
            onBackClicked = { viewModel.navigateTo(Screen.MAIN) },
            modifier = modifier
        )

        ExportResultDialogs(uiState = uiState, viewModel = viewModel)
        ProcessingProgressDialog(uiState = uiState)
        return
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
                                contentDescription = "Clear All"
                            )
                        }
                    }
                    IconButton(onClick = { viewModel.navigateTo(Screen.SETTINGS) }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
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
                .padding(16.dp)
        ) {
            MergeSettingsCard(
                direction = currentDirection,
                onDirectionChange = { viewModel.updateDirection(it) },
                alignmentMode = currentAlignment,
                onAlignmentModeChange = { viewModel.updateAlignmentMode(it) },
                paddingColor = currentPaddingColor,
                onPaddingColorChange = { viewModel.updatePaddingColor(it) }
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Selected Pages (${uiState.selectedImages.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                OutlinedButton(
                    onClick = {
                        selectImagesLauncher.launch(arrayOf("image/*"))
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Add Images")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (uiState.selectedImages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No images selected to stitch",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                selectImagesLauncher.launch(arrayOf("image/*"))
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "Select Images from Device")
                        }
                    }
                }
            } else {
                ImageReorderList(
                    items = uiState.selectedImages,
                    onMoveUp = { viewModel.moveUp(it) },
                    onMoveDown = { viewModel.moveDown(it) },
                    onRemove = { viewModel.remove(it) },
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        viewModel.generatePreview(context)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    enabled = uiState.selectedImages.isNotEmpty() && !uiState.isProcessing
                ) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Stitch & Export Preview",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    ProcessingProgressDialog(uiState = uiState)
    ExportResultDialogs(uiState = uiState, viewModel = viewModel)
}

@Composable
private fun ProcessingProgressDialog(uiState: MainUiState) {
    if (uiState.isProcessing) {
        Dialog(onDismissRequest = {}) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (uiState.processingStep.isNotEmpty()) uiState.processingStep else "Processing...",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { uiState.progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "${(uiState.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun ExportResultDialogs(
    uiState: MainUiState,
    viewModel: MainViewModel
) {
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
                Column {
                    AsyncImage(
                        model = uri,
                        contentDescription = "Export Result Preview",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Output Files: ${result.outputCount}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Dimensions: ${result.width} x ${result.height} px",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Total Size: ${formatFileSize(result.bytesWritten)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (result.itemsNeedingManualReview > 0) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Needs Manual Review Warning",
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${result.itemsNeedingManualReview} piece(s) need manual review (bubble/text overlap)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            }
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

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
    val pre = "KMGTPE"[exp - 1]
    return String.format(Locale.US, "%.1f %cB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
}
