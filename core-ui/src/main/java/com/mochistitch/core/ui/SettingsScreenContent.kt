package com.mochistitch.core.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
            // ── 1. Output ──────────────────────────────────────────────
            SectionCard(title = "Output / Ekspor") {
                SettingSectionLabel("Format Berkas Gambar")
                Spacer(modifier = Modifier.height(4.dp))
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

            // ── 2. Pemotongan (Split) ──────────────────────────────────
            SectionCard(title = "Pemotongan (Split)") {
                SettingSectionLabel("Mode Pemotongan")
                Spacer(modifier = Modifier.height(4.dp))
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
                        supportingText = { Text("Mencegah potongan terlalu panjang. Rentang: 1.000 – 20.000 px") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
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
                }
            }

            // ── 3. Mochi Smart (Deteksi Otomatis) ─────────────────────
            SectionCard(title = "Mochi Smart (Deteksi Otomatis)") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Aktifkan Mochi Smart AI",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Deteksi otomatis balon ucapan, teks monolog, dan SFX agar tidak terpotong di tengah.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.mochiSmartEnabled,
                        onCheckedChange = { onSettingsChanged(settings.copy(mochiSmartEnabled = it)) }
                    )
                }

                if (settings.mochiSmartEnabled) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    SettingSectionLabel("Sensitivitas Deteksi Contour/Balon")
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

                    // Tolerance slider + tooltip explanation
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Toleransi Pergeseran Potongan",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "${settings.mochiSmartTolerance} px",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = settings.mochiSmartTolerance.toFloat(),
                            onValueChange = { onSettingsChanged(settings.copy(mochiSmartTolerance = it.roundToInt())) },
                            valueRange = 50f..500f
                        )
                        TooltipBox(
                            text = "Rentang piksel maksimum di mana pemotong boleh menggeser titik potong dari target demi menemukan celah aman bebas balon kata."
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Allow exceed toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Izinkan Melebihi Batas Potong",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Jika tidak ada celah aman dalam toleransi, perpanjang potongan melewati batas demi menghindari memotong balon kata.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.allowExceedOnNoSafeGap,
                            onCheckedChange = { onSettingsChanged(settings.copy(allowExceedOnNoSafeGap = it)) }
                        )
                    }

                    // Prefer shorter toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Utamakan Potongan Lebih Pendek",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Jika ada dua celah aman berjarak sama, pilih yang menghasilkan potongan lebih pendek.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.preferShorterOverLonger,
                            onCheckedChange = { onSettingsChanged(settings.copy(preferShorterOverLonger = it)) }
                        )
                    }
                }
            }

            // ── 4. Lanjutan ───────────────────────────────────────────
            AdvancedSettingsCard(
                isExpanded = isAdvancedExpanded,
                onToggle = { isAdvancedExpanded = !isAdvancedExpanded },
                settings = settings,
                onSettingsChanged = onSettingsChanged
            )
        }
    }
}

@Composable
private fun TooltipBox(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

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
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            content()
        }
    }
}

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

@Composable
private fun SettingSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

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
                    text = "Pengaturan Lanjutan & Gutter",
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
                // Auto Gutter Detection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Deteksi Gutter Pixel-Whitespace", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("Cari baris pixel paling bersih (variansi rendah) di area celah aman.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = settings.autoGutterDetectionEnabled,
                        onCheckedChange = { onSettingsChanged(settings.copy(autoGutterDetectionEnabled = it)) }
                    )
                }

                if (settings.autoGutterDetectionEnabled) {
                    Column {
                        Text("Sensitivitas Deteksi Pixel: ${(settings.pixelComparisonSensitivity * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = settings.pixelComparisonSensitivity,
                            onValueChange = { onSettingsChanged(settings.copy(pixelComparisonSensitivity = it)) },
                            valueRange = 0.1f..1.0f
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Layout & Reading
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingSectionLabel("Arah Baca / Penggabungan")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReadingDirection.entries.forEach { dir ->
                            val label = when (dir) {
                                ReadingDirection.VERTICAL -> "Vertikal (Webtoon)"
                                ReadingDirection.LTR -> "Kiri ke Kanan"
                                ReadingDirection.RTL -> "Kanan ke Kiri"
                            }
                            MochiChoiceChip(
                                selected = settings.readingDirection == dir,
                                onClick = { onSettingsChanged(settings.copy(readingDirection = dir)) },
                                label = label
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    SettingSectionLabel("Mode Penyesuaian Lebar")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AlignmentModeSetting.entries.forEach { mode ->
                            val label = when (mode) {
                                AlignmentModeSetting.RESIZE_PROPORTIONAL -> "Sesuaikan Proporsi"
                                AlignmentModeSetting.CENTER_CROP -> "Potong Tengah"
                                AlignmentModeSetting.PADDING -> "Tambah Margin"
                            }
                            MochiChoiceChip(
                                selected = settings.alignmentMode == mode,
                                onClick = { onSettingsChanged(settings.copy(alignmentMode = mode)) },
                                label = label
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Export packaging
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingSectionLabel("Format Pengemasan Ekspor")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutputWrapperFormat.entries.forEach { wrapper ->
                            val label = when (wrapper) {
                                OutputWrapperFormat.LOOSE_FILES -> "Berkas Terpisah"
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
            }
        }
    }
}
