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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
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
import androidx.compose.runtime.LaunchedEffect
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
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
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
            // ── 1. Output Format ──────────────────────────────────────────
            SectionCard(title = "Output Format") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Quality: ${settings.jpgQuality}%",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "${settings.jpgQuality}%",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
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
                        Text("WEBP Lossless", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = settings.webpLossless,
                            onCheckedChange = { onSettingsChanged(settings.copy(webpLossless = it)) }
                        )
                    }
                    if (!settings.webpLossless) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Quality: ${settings.webpQuality}%",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "${settings.webpQuality}%",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Slider(
                            value = settings.webpQuality.toFloat(),
                            onValueChange = { onSettingsChanged(settings.copy(webpQuality = it.roundToInt())) },
                            valueRange = 1f..100f
                        )
                    }
                }
            }

            // ── 2. Auto-Split Mode ────────────────────────────────────────
            SectionCard(title = "Split Settings") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SplitMode.entries.forEach { mode ->
                        val label = when (mode) {
                            SplitMode.NO_LIMIT -> "No Split"
                            SplitMode.MAX_PIXELS -> "Max Pixels"
                            SplitMode.PAGES_PER_FILE -> "Pages/File"
                        }
                        FilterChip(
                            selected = settings.splitMode == mode,
                            onClick = { onSettingsChanged(settings.copy(splitMode = mode)) },
                            label = { Text(label) }
                        )
                    }
                }

                if (settings.splitMode == SplitMode.MAX_PIXELS) {
                    Spacer(modifier = Modifier.height(14.dp))
                    // ── FIX GLITCH: local text state, only commit valid values ──
                    var pixelText by remember { mutableStateOf(settings.maxPixelLength.toString()) }
                    // Sync text when settings change externally (e.g. from slider)
                    // but only if text doesn't look like the user is mid-typing
                    LaunchedEffect(settings.maxPixelLength) {
                        pixelText = settings.maxPixelLength.toString()
                    }
                    OutlinedTextField(
                        value = pixelText,
                        onValueChange = {
                            pixelText = it
                            val cleaned = it.replace("[^0-9]".toRegex(), "")
                            val value = cleaned.toIntOrNull()
                            if (value != null && value >= 1000) {
                                onSettingsChanged(settings.copy(maxPixelLength = value.coerceIn(1000, 20000)))
                            }
                        },
                        label = { Text("Max Pixel Height per Slice") },
                        supportingText = { Text("Min 1,000 · Max 20,000 px") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Current: ${settings.maxPixelLength} px",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = settings.maxPixelLength.toFloat(),
                        onValueChange = { onSettingsChanged(settings.copy(maxPixelLength = it.roundToInt())) },
                        valueRange = 1000f..20000f,
                        steps = 19,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (settings.splitMode == SplitMode.PAGES_PER_FILE) {
                    Spacer(modifier = Modifier.height(14.dp))
                    // ── FIX GLITCH: local text state, only commit valid values ──
                    var pagesText by remember { mutableStateOf(settings.maxPagesPerFile.toString()) }
                    LaunchedEffect(settings.maxPagesPerFile) {
                        pagesText = settings.maxPagesPerFile.toString()
                    }
                    OutlinedTextField(
                        value = pagesText,
                        onValueChange = {
                            pagesText = it
                            val cleaned = it.replace("[^0-9]".toRegex(), "")
                            val value = cleaned.toIntOrNull() ?: 0
                            val clamped = value.coerceIn(1, 100)
                            onSettingsChanged(settings.copy(maxPagesPerFile = clamped))
                        },
                        label = { Text("Max Pages per File") },
                        supportingText = { Text("Range: 1 – 100") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Current: ${settings.maxPagesPerFile} pages",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── 3. Layout Defaults ────────────────────────────────────────
            SectionCard(title = "Layout Defaults") {
                SettingSectionLabel("Reading Direction")
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReadingDirection.entries.forEach { dir ->
                        val label = when (dir) {
                            ReadingDirection.VERTICAL -> "Vertical"
                            ReadingDirection.LTR -> "LTR"
                            ReadingDirection.RTL -> "RTL"
                        }
                        FilterChip(
                            selected = settings.readingDirection == dir,
                            onClick = { onSettingsChanged(settings.copy(readingDirection = dir)) },
                            label = { Text(label) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                SettingSectionLabel("Alignment Mode")
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AlignmentModeSetting.entries.forEach { mode ->
                        val label = when (mode) {
                            AlignmentModeSetting.RESIZE_PROPORTIONAL -> "Scale to Fit"
                            AlignmentModeSetting.CENTER_CROP -> "Center Crop"
                            AlignmentModeSetting.PADDING -> "Padding"
                        }
                        FilterChip(
                            selected = settings.alignmentMode == mode,
                            onClick = { onSettingsChanged(settings.copy(alignmentMode = mode)) },
                            label = { Text(label) }
                        )
                    }
                }

                if (settings.alignmentMode == AlignmentModeSetting.PADDING) {
                    Spacer(modifier = Modifier.height(12.dp))
                    SettingSectionLabel("Padding Color")
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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

            // ── 4. Advanced Settings (collapsible) ────────────────────────
            AdvancedSettingsCard(
                isExpanded = isAdvancedExpanded,
                onToggle = { isAdvancedExpanded = !isAdvancedExpanded },
                settings = settings,
                onSettingsChanged = onSettingsChanged
            )
        }
    }
}

// ── Reusable section card ─────────────────────────────────────────────────────
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary
            )
            content()
        }
    }
}

// ── Small section label inside cards ──────────────────────────────────────────
@Composable
private fun SettingSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

// ── Advanced settings collapsible card ────────────────────────────────────────
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdvancedSettingsCard(
    isExpanded: Boolean,
    onToggle: () -> Unit,
    settings: MochiStitchSettings,
    onSettingsChanged: (MochiStitchSettings) -> Unit
) {
    ElevatedCard(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Header (always visible)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Advanced",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                modifier = Modifier.rotate(if (isExpanded) 180f else 0f)
            )
        }

        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── MochiSmart ──────────────────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "MochiSmart Engine",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Switch(
                            checked = settings.mochiSmartEnabled,
                            onCheckedChange = { onSettingsChanged(settings.copy(mochiSmartEnabled = it)) }
                        )
                    }

                    if (settings.mochiSmartEnabled) {
                        Text(
                            text = "Tolerance: ${settings.mochiSmartTolerance}px",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Slider(
                            value = settings.mochiSmartTolerance.toFloat(),
                            onValueChange = { onSettingsChanged(settings.copy(mochiSmartTolerance = it.roundToInt())) },
                            valueRange = 50f..400f
                        )

                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Sensitivity", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DetectionSensitivity.entries.forEach { sens ->
                                val label = when (sens) {
                                    DetectionSensitivity.LOW -> "Low"
                                    DetectionSensitivity.MEDIUM -> "Balanced"
                                    DetectionSensitivity.HIGH -> "High"
                                }
                                FilterChip(
                                    selected = settings.mochiSmartSensitivity == sens,
                                    onClick = { onSettingsChanged(settings.copy(mochiSmartSensitivity = sens)) },
                                    label = { Text(label) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Show review markers", style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = settings.showManualReviewMarkers,
                                onCheckedChange = { onSettingsChanged(settings.copy(showManualReviewMarkers = it)) }
                            )
                        }
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant)

                // ── Packaging ───────────────────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Output Format",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutputWrapperFormat.entries.forEach { wrapper ->
                            val label = when (wrapper) {
                                OutputWrapperFormat.LOOSE_FILES -> "Images"
                                OutputWrapperFormat.CBZ -> "CBZ"
                                OutputWrapperFormat.ZIP -> "ZIP"
                            }
                            FilterChip(
                                selected = settings.wrapperFormat == wrapper,
                                onClick = { onSettingsChanged(settings.copy(wrapperFormat = wrapper)) },
                                label = { Text(label) }
                            )
                        }
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant)

                // ── Naming ──────────────────────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Naming",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    OutlinedTextField(
                        value = settings.projectName,
                        onValueChange = { onSettingsChanged(settings.copy(projectName = it)) },
                        label = { Text("Project Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                    OutlinedTextField(
                        value = settings.chapterName,
                        onValueChange = { onSettingsChanged(settings.copy(chapterName = it)) },
                        label = { Text("Chapter / Volume") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                    OutlinedTextField(
                        value = settings.filenameTemplate,
                        onValueChange = { onSettingsChanged(settings.copy(filenameTemplate = it)) },
                        label = { Text("Filename Template ({project} {chapter} {index})") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                    Text(
                        text = "Index padding: ${settings.indexPaddingDigits} digit${if (settings.indexPaddingDigits != 1) "s" else ""} (e.g. ${"1".padStart(settings.indexPaddingDigits, '0')})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = settings.indexPaddingDigits.toFloat(),
                        onValueChange = { onSettingsChanged(settings.copy(indexPaddingDigits = it.roundToInt())) },
                        valueRange = 1f..5f,
                        steps = 3
                    )
                }
            }
        }
    }
}
