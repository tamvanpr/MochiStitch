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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.input.TextFieldValue
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Pengaturan",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClicked) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali"
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
            // ── 1. Format Hasil ──────────────────────────────────────────
            SectionCard(title = "Format Gambar Output") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutputFormat.entries.forEach { format ->
                        MochiChoiceChip(
                            selected = settings.outputFormat == format,
                            onClick = { onSettingsChanged(settings.copy(outputFormat = format)) },
                            label = format.name
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
                            text = "Kualitas JPG",
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
                        Text("WEBP Tanpa Kompresi (Lossless)", style = MaterialTheme.typography.bodyMedium)
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
                                text = "Kualitas WEBP",
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

            // ── 2. Mode Pemotongan ────────────────────────────────────────
            SectionCard(title = "Pengaturan Pemotongan") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SplitMode.entries.forEach { mode ->
                        val label = when (mode) {
                            SplitMode.NO_LIMIT -> "Tanpa Pemotongan"
                            SplitMode.MAX_PIXELS -> "Maksimal Piksel"
                            SplitMode.PAGES_PER_FILE -> "Halaman / Berkas"
                        }
                        MochiChoiceChip(
                            selected = settings.splitMode == mode,
                            onClick = { onSettingsChanged(settings.copy(splitMode = mode)) },
                            label = label
                        )
                    }
                }

                if (settings.splitMode == SplitMode.MAX_PIXELS) {
                    Spacer(modifier = Modifier.height(14.dp))
                    var pixelTfv by remember { mutableStateOf(TextFieldValue(text = settings.maxPixelLength.toString())) }
                    LaunchedEffect(settings.maxPixelLength) {
                        if (pixelTfv.text != settings.maxPixelLength.toString()) {
                            pixelTfv = TextFieldValue(text = settings.maxPixelLength.toString(), selection = pixelTfv.selection)
                        }
                    }
                    OutlinedTextField(
                        value = pixelTfv,
                        onValueChange = {
                            pixelTfv = it
                            val cleaned = it.text.replace("[^0-9]".toRegex(), "")
                            val value = cleaned.toIntOrNull()
                            if (value != null && value >= 1000) {
                                onSettingsChanged(settings.copy(maxPixelLength = value.coerceIn(1000, 20000)))
                            }
                        },
                        label = { Text("Tinggi Maksimal Piksel per Potongan") },
                        supportingText = { Text("Rentang: 1.000 – 20.000 px") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Saat Ini: ${settings.maxPixelLength} px",
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
                    var pagesTfv by remember { mutableStateOf(TextFieldValue(text = settings.maxPagesPerFile.toString())) }
                    LaunchedEffect(settings.maxPagesPerFile) {
                        if (pagesTfv.text != settings.maxPagesPerFile.toString()) {
                            pagesTfv = TextFieldValue(text = settings.maxPagesPerFile.toString(), selection = pagesTfv.selection)
                        }
                    }
                    OutlinedTextField(
                        value = pagesTfv,
                        onValueChange = {
                            pagesTfv = it
                            val cleaned = it.text.replace("[^0-9]".toRegex(), "")
                            val value = cleaned.toIntOrNull() ?: 0
                            val clamped = value.coerceIn(1, 100)
                            onSettingsChanged(settings.copy(maxPagesPerFile = clamped))
                        },
                        label = { Text("Maksimal Halaman per Berkas") },
                        supportingText = { Text("Rentang: 1 – 100 halaman") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Saat Ini: ${settings.maxPagesPerFile} halaman",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── 3. Tata Letak Utama ────────────────────────────────────────
            SectionCard(title = "Tata Letak & Arah Baca") {
                SettingSectionLabel("Arah Baca / Penggabungan")
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ReadingDirection.entries.forEach { dir ->
                        val label = when (dir) {
                            ReadingDirection.VERTICAL -> "Vertikal (Webtoon)"
                            ReadingDirection.LTR -> "Kiri ke Kanan (LTR)"
                            ReadingDirection.RTL -> "Kanan ke Kiri (RTL)"
                        }
                        MochiChoiceChip(
                            selected = settings.readingDirection == dir,
                            onClick = { onSettingsChanged(settings.copy(readingDirection = dir)) },
                            label = label
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                SettingSectionLabel("Mode Penyesuaian Dimensi")
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AlignmentModeSetting.entries.forEach { mode ->
                        val label = when (mode) {
                            AlignmentModeSetting.RESIZE_PROPORTIONAL -> "Sesuaikan Proporsi"
                            AlignmentModeSetting.CENTER_CROP -> "Potong Tengah"
                            AlignmentModeSetting.PADDING -> "Tambah Warna Latar"
                        }
                        MochiChoiceChip(
                            selected = settings.alignmentMode == mode,
                            onClick = { onSettingsChanged(settings.copy(alignmentMode = mode)) },
                            label = label
                        )
                    }
                }

                if (settings.alignmentMode == AlignmentModeSetting.PADDING) {
                    Spacer(modifier = Modifier.height(12.dp))
                    SettingSectionLabel("Warna Latar Margin")
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PaddingColorSetting.entries.forEach { color ->
                            val label = when (color) {
                                PaddingColorSetting.WHITE -> "Putih"
                                PaddingColorSetting.BLACK -> "Hitam"
                                PaddingColorSetting.TRANSPARENT -> "Transparan"
                            }
                            MochiChoiceChip(
                                selected = settings.paddingColor == color,
                                onClick = { onSettingsChanged(settings.copy(paddingColor = color)) },
                                label = label
                            )
                        }
                    }
                }
            }

            // ── 4. Pengaturan Lanjutan (collapsible) ────────────────────────
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


// ── Reusable choice chip component ─────────────────────────────────────────────
@Composable
private fun MochiChoiceChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        selected = selected,
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = if (selected) 2.dp else 0.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
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
                    text = "Pengaturan Tingkat Lanjut",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = if (isExpanded) "Tutup" else "Buka",
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
                            text = "Deteksi Pintar MochiSmart",
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
                            text = "Toleransi Jarak Potong: ${settings.mochiSmartTolerance} px",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Slider(
                            value = settings.mochiSmartTolerance.toFloat(),
                            onValueChange = { onSettingsChanged(settings.copy(mochiSmartTolerance = it.roundToInt())) },
                            valueRange = 50f..400f
                        )

                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Sensitivitas Deteksi Teks", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DetectionSensitivity.entries.forEach { sens ->
                                val label = when (sens) {
                                    DetectionSensitivity.LOW -> "Rendah"
                                    DetectionSensitivity.MEDIUM -> "Seimbang"
                                    DetectionSensitivity.HIGH -> "Tinggi"
                                }
                                MochiChoiceChip(
                                    selected = settings.mochiSmartSensitivity == sens,
                                    onClick = { onSettingsChanged(settings.copy(mochiSmartSensitivity = sens)) },
                                    label = label
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Tampilkan penanda peninjauan", style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = settings.showManualReviewMarkers,
                                onCheckedChange = { onSettingsChanged(settings.copy(showManualReviewMarkers = it)) }
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // ── Pengemasan ───────────────────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Format Pengemasan Ekspor",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutputWrapperFormat.entries.forEach { wrapper ->
                            val label = when (wrapper) {
                                OutputWrapperFormat.LOOSE_FILES -> "Berkas Gambar Terpisah"
                                OutputWrapperFormat.CBZ -> "Arsip CBZ"
                                OutputWrapperFormat.ZIP -> "Arsip ZIP"
                            }
                            MochiChoiceChip(
                                selected = settings.wrapperFormat == wrapper,
                                onClick = { onSettingsChanged(settings.copy(wrapperFormat = wrapper)) },
                                label = label
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // ── Penamaan ──────────────────────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Penamaan Berkas Hasil",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    var projectTfv by remember { mutableStateOf(TextFieldValue(text = settings.projectName)) }
                    LaunchedEffect(settings.projectName) {
                        if (projectTfv.text != settings.projectName) {
                            projectTfv = TextFieldValue(text = settings.projectName, selection = projectTfv.selection)
                        }
                    }
                    OutlinedTextField(
                        value = projectTfv,
                        onValueChange = {
                            projectTfv = it
                            onSettingsChanged(settings.copy(projectName = it.text))
                        },
                        label = { Text("Nama Proyek / Judul Komik") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )

                    var chapterTfv by remember { mutableStateOf(TextFieldValue(text = settings.chapterName)) }
                    LaunchedEffect(settings.chapterName) {
                        if (chapterTfv.text != settings.chapterName) {
                            chapterTfv = TextFieldValue(text = settings.chapterName, selection = chapterTfv.selection)
                        }
                    }
                    OutlinedTextField(
                        value = chapterTfv,
                        onValueChange = {
                            chapterTfv = it
                            onSettingsChanged(settings.copy(chapterName = it.text))
                        },
                        label = { Text("Bab / Volume") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )

                    var templateTfv by remember { mutableStateOf(TextFieldValue(text = settings.filenameTemplate)) }
                    LaunchedEffect(settings.filenameTemplate) {
                        if (templateTfv.text != settings.filenameTemplate) {
                            templateTfv = TextFieldValue(text = settings.filenameTemplate, selection = templateTfv.selection)
                        }
                    }
                    OutlinedTextField(
                        value = templateTfv,
                        onValueChange = {
                            templateTfv = it
                            onSettingsChanged(settings.copy(filenameTemplate = it.text))
                        },
                        label = { Text("Template Nama Berkas ({project} {chapter} {index})") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                    Text(
                        text = "Jumlah digit indeks: ${settings.indexPaddingDigits} digit (contoh: ${"1".padStart(settings.indexPaddingDigits, '0')})",
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
