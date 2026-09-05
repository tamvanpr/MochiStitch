package com.mochistitch.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mochistitch.core.settings.AlignmentModeSetting
import com.mochistitch.core.settings.MochiStitchSettings
import com.mochistitch.core.settings.OutputFormat
import com.mochistitch.core.settings.OutputWrapperFormat
import com.mochistitch.core.settings.PaddingColorSetting
import com.mochistitch.core.settings.ReadingDirection
import com.mochistitch.core.settings.SplitMode
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenContent(
    settings: MochiStitchSettings,
    onSettingsChanged: (MochiStitchSettings) -> Unit,
    onBackClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBackClicked) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Output Image Format & Quality
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Output Image Format",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutputFormat.entries.forEach { format ->
                            FilterChip(
                                selected = settings.outputFormat == format,
                                onClick = { onSettingsChanged(settings.copy(outputFormat = format)) },
                                label = { Text(format.name) }
                            )
                        }
                    }

                    if (settings.outputFormat == OutputFormat.JPG) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "JPG Quality: ${settings.jpgQuality}%",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Slider(
                            value = settings.jpgQuality.toFloat(),
                            onValueChange = { onSettingsChanged(settings.copy(jpgQuality = it.roundToInt())) },
                            valueRange = 1f..100f
                        )
                    }

                    if (settings.outputFormat == OutputFormat.WEBP) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("WEBP Lossless Mode")
                            Switch(
                                checked = settings.webpLossless,
                                onCheckedChange = { onSettingsChanged(settings.copy(webpLossless = it)) }
                            )
                        }
                        if (!settings.webpLossless) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "WEBP Quality: ${settings.webpQuality}%",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Slider(
                                value = settings.webpQuality.toFloat(),
                                onValueChange = { onSettingsChanged(settings.copy(webpQuality = it.roundToInt())) },
                                valueRange = 1f..100f
                            )
                        }
                    }
                }
            }

            // 2. Output Wrapper Format
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Output Wrapper / Container",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutputWrapperFormat.entries.forEach { wrapper ->
                            FilterChip(
                                selected = settings.wrapperFormat == wrapper,
                                onClick = { onSettingsChanged(settings.copy(wrapperFormat = wrapper)) },
                                label = { Text(wrapper.name) }
                            )
                        }
                    }
                }
            }

            // 3. File Naming Template
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "File Naming & Project Info",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = settings.projectName,
                        onValueChange = { onSettingsChanged(settings.copy(projectName = it)) },
                        label = { Text("Project Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = settings.chapterName,
                        onValueChange = { onSettingsChanged(settings.copy(chapterName = it)) },
                        label = { Text("Chapter / Volume") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = settings.filenameTemplate,
                        onValueChange = { onSettingsChanged(settings.copy(filenameTemplate = it)) },
                        label = { Text("Filename Template ({project}, {chapter}, {index})") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Index Padding Digits: ${settings.indexPaddingDigits} (e.g. ${"1".padStart(settings.indexPaddingDigits, '0')})",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = settings.indexPaddingDigits.toFloat(),
                        onValueChange = { onSettingsChanged(settings.copy(indexPaddingDigits = it.roundToInt())) },
                        valueRange = 1f..5f,
                        steps = 3
                    )
                }
            }

            // 4. Auto-Split Mode
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Auto-Split Mode",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SplitMode.entries.forEach { mode ->
                            FilterChip(
                                selected = settings.splitMode == mode,
                                onClick = { onSettingsChanged(settings.copy(splitMode = mode)) },
                                label = { Text(mode.name) }
                            )
                        }
                    }

                    if (settings.splitMode == SplitMode.MAX_PIXELS) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = settings.maxPixelLength.toString(),
                            onValueChange = {
                                val value = it.toIntOrNull() ?: 0
                                onSettingsChanged(settings.copy(maxPixelLength = value))
                            },
                            label = { Text("Max Pixel Length") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }

                    if (settings.splitMode == SplitMode.PAGES_PER_FILE) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = settings.maxPagesPerFile.toString(),
                            onValueChange = {
                                val value = it.toIntOrNull() ?: 0
                                onSettingsChanged(settings.copy(maxPagesPerFile = value))
                            },
                            label = { Text("Max Pages Per File") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
            }

            // 5. Reading Direction & Alignment Defaults
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Layout Defaults",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Reading Direction", style = MaterialTheme.typography.bodyMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ReadingDirection.entries.forEach { dir ->
                            FilterChip(
                                selected = settings.readingDirection == dir,
                                onClick = { onSettingsChanged(settings.copy(readingDirection = dir)) },
                                label = { Text(dir.name) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Alignment Mode", style = MaterialTheme.typography.bodyMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AlignmentModeSetting.entries.forEach { mode ->
                            FilterChip(
                                selected = settings.alignmentMode == mode,
                                onClick = { onSettingsChanged(settings.copy(alignmentMode = mode)) },
                                label = { Text(mode.name) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Padding Color", style = MaterialTheme.typography.bodyMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PaddingColorSetting.entries.forEach { color ->
                            FilterChip(
                                selected = settings.paddingColor == color,
                                onClick = { onSettingsChanged(settings.copy(paddingColor = color)) },
                                label = { Text(color.name) }
                            )
                        }
                    }
                }
            }
        }
    }
}
