package com.mochistitch.core.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mochistitch.core.settings.AlignmentModeSetting
import com.mochistitch.core.settings.DetectionSensitivity
import com.mochistitch.core.settings.MochiStitchSettings
import com.mochistitch.core.settings.OutputFormat
import com.mochistitch.core.settings.OutputWrapperFormat
import com.mochistitch.core.settings.PaddingColorSetting
import com.mochistitch.core.settings.ReadingDirection
import com.mochistitch.core.settings.SplitMode
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreenContent(
    settings: MochiStitchSettings,
    onSettingsChanged: (MochiStitchSettings) -> Unit,
    onBackClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBackClicked)

    var isAdvancedExpanded by remember { mutableStateOf(false) }
    val arrowRotationAngle by animateFloatAsState(
        targetValue = if (isAdvancedExpanded) 180f else 0f,
        label = "AdvancedSettingsArrowRotation"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App & Export Settings", style = MaterialTheme.typography.titleLarge) },
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
            // 1. Primary Output Image Format & Quality
            ElevatedCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Output Image Format",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    FlowRow(
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
                    } else if (settings.outputFormat == OutputFormat.WEBP) {
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

            // 2. Auto-Split Mode with friendly descriptive labels
            ElevatedCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Auto-Split Mode",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SplitMode.entries.forEach { mode ->
                            val label = when (mode) {
                                SplitMode.NO_LIMIT -> "No Splitting (Single Long Image)"
                                SplitMode.MAX_PIXELS -> "Split by File Size/Pixels"
                                SplitMode.PAGES_PER_FILE -> "Split by Page Count"
                            }
                            FilterChip(
                                selected = settings.splitMode == mode,
                                onClick = { onSettingsChanged(settings.copy(splitMode = mode)) },
                                label = { Text(label) }
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
                            label = { Text("Max Pixel Height/Length per Slice") },
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
                            label = { Text("Max Pages per Export File") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
            }

            // 3. Layout Defaults
            ElevatedCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Layout Defaults",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Default Reading Direction", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ReadingDirection.entries.forEach { dir ->
                            val label = when (dir) {
                                ReadingDirection.VERTICAL -> "Vertical (Webtoon)"
                                ReadingDirection.LTR -> "Horizontal (Left-to-Right)"
                                ReadingDirection.RTL -> "Horizontal (Right-to-Left / Manga)"
                            }
                            FilterChip(
                                selected = settings.readingDirection == dir,
                                onClick = { onSettingsChanged(settings.copy(readingDirection = dir)) },
                                label = { Text(label) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Default Alignment Mode", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AlignmentModeSetting.entries.forEach { mode ->
                            val label = when (mode) {
                                AlignmentModeSetting.RESIZE_PROPORTIONAL -> "Scale to Fit (Maintain Ratio)"
                                AlignmentModeSetting.CENTER_CROP -> "Center Crop"
                                AlignmentModeSetting.PADDING -> "Add Padding / Letterbox"
                            }
                            FilterChip(
                                selected = settings.alignmentMode == mode,
                                onClick = { onSettingsChanged(settings.copy(alignmentMode = mode)) },
                                label = { Text(label) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Default Padding Background Color", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PaddingColorSetting.entries.forEach { color ->
                            val label = when (color) {
                                PaddingColorSetting.WHITE -> "White"
                                PaddingColorSetting.BLACK -> "Black"
                                PaddingColorSetting.TRANSPARENT -> "Transparent"
                            }
                            FilterChip(
                                selected = settings.paddingColor == color,
                                onClick = { onSettingsChanged(settings.copy(paddingColor = color)) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }

            // 4. Advanced Settings Sub-Menu / Collapsible Section
            ElevatedCard(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isAdvancedExpanded = !isAdvancedExpanded }
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(0.dp))
                            Text(
                                text = " Advanced Settings",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isAdvancedExpanded) "Collapse Advanced Settings" else "Expand Advanced Settings",
                            modifier = Modifier.rotate(arrowRotationAngle)
                        )
                    }

                    AnimatedVisibility(visible = isAdvancedExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Sub-Section A: MochiSmart AI Contour Engine
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "MochiSmart AI / Smart Contour Engine",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Switch(
                                        checked = settings.mochiSmartEnabled,
                                        onCheckedChange = { onSettingsChanged(settings.copy(mochiSmartEnabled = it)) }
                                    )
                                }

                                if (settings.mochiSmartEnabled) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "Split Tolerance Range: ${settings.mochiSmartTolerance} px",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Slider(
                                        value = settings.mochiSmartTolerance.toFloat(),
                                        onValueChange = { onSettingsChanged(settings.copy(mochiSmartTolerance = it.roundToInt())) },
                                        valueRange = 50f..400f
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "Detection Sensitivity",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        DetectionSensitivity.entries.forEach { sensitivity ->
                                            val label = when (sensitivity) {
                                                DetectionSensitivity.LOW -> "Low (Fewer Splits)"
                                                DetectionSensitivity.MEDIUM -> "Balanced"
                                                DetectionSensitivity.HIGH -> "High (Sensitive)"
                                            }
                                            FilterChip(
                                                selected = settings.mochiSmartSensitivity == sensitivity,
                                                onClick = { onSettingsChanged(settings.copy(mochiSmartSensitivity = sensitivity)) },
                                                label = { Text(label) }
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Show Manual Review Markers",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Switch(
                                            checked = settings.showManualReviewMarkers,
                                            onCheckedChange = { onSettingsChanged(settings.copy(showManualReviewMarkers = it)) }
                                        )
                                    }
                                }
                            }

                            // Sub-Section B: Output Archive / Container Wrapper
                            Column {
                                Text(
                                    text = "Output Packaging / Wrapper",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutputWrapperFormat.entries.forEach { wrapper ->
                                        val label = when (wrapper) {
                                            OutputWrapperFormat.LOOSE_FILES -> "Individual Image Files"
                                            OutputWrapperFormat.CBZ -> "Comic Book Archive (.cbz)"
                                            OutputWrapperFormat.ZIP -> "ZIP Archive (.zip)"
                                        }
                                        FilterChip(
                                            selected = settings.wrapperFormat == wrapper,
                                            onClick = { onSettingsChanged(settings.copy(wrapperFormat = wrapper)) },
                                            label = { Text(label) }
                                        )
                                    }
                                }
                            }

                            // Sub-Section C: File Naming Template & Metadata
                            Column {
                                Text(
                                    text = "File Naming & Project Metadata",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(10.dp))
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
                                Spacer(modifier = Modifier.height(10.dp))
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
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}
