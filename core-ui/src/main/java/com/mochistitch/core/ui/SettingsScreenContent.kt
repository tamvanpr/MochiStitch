package com.mochistitch.core.ui

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mochistitch.core.imaging.FilenameFormatter
import com.mochistitch.core.settings.MochiStitchSettings
import com.mochistitch.core.settings.OutputFormat
import com.mochistitch.core.settings.OutputWrapperFormat
import com.mochistitch.core.settings.SplitMode
import com.mochistitch.core.settings.Strictness
import com.mochistitch.core.settings.ThemeMode
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
                title = { Text("Pengaturan", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClicked) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
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
            SectionCard(title = "Output / Ekspor") {
                Label("Format Gambar")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutputFormat.entries.forEach { fmt ->
                        ChoiceChip(
                            selected = settings.outputFormat == fmt,
                            onClick = { onSettingsChanged(settings.copy(outputFormat = fmt)) },
                            label = fmt.name
                        )
                    }
                }
                if (settings.outputFormat == OutputFormat.JPG) {
                    Text("Kualitas JPG: ${settings.jpgQuality}%", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = settings.jpgQuality.toFloat(),
                        onValueChange = { onSettingsChanged(settings.copy(jpgQuality = it.roundToInt())) },
                        valueRange = 10f..100f
                    )
                }
                if (settings.outputFormat == OutputFormat.WEBP) {
                    Text("Kualitas WEBP: ${settings.webpQuality}%", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = settings.webpQuality.toFloat(),
                        onValueChange = { onSettingsChanged(settings.copy(webpQuality = it.roundToInt())) },
                        valueRange = 10f..100f
                    )
                }
                Label("Pengemasan (default: ZIP)")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutputWrapperFormat.entries.forEach { wrapper ->
                        val label = when (wrapper) {
                            OutputWrapperFormat.ZIP -> "Arsip ZIP"
                            OutputWrapperFormat.CBZ -> "Arsip CBZ"
                            OutputWrapperFormat.LOOSE_FILES -> "Berkas Terpisah"
                        }
                        ChoiceChip(
                            selected = settings.wrapperFormat == wrapper,
                            onClick = { onSettingsChanged(settings.copy(wrapperFormat = wrapper)) },
                            label = label
                        )
                    }
                }
            }

            SectionCard(title = "Penamaan File") {
                OutlinedTextField(
                    value = settings.projectName,
                    onValueChange = { onSettingsChanged(settings.copy(projectName = it)) },
                    label = { Text("Nama Proyek") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
                OutlinedTextField(
                    value = settings.chapterName,
                    onValueChange = { onSettingsChanged(settings.copy(chapterName = it)) },
                    label = { Text("Chapter") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
                OutlinedTextField(
                    value = settings.filenameTemplate,
                    onValueChange = { onSettingsChanged(settings.copy(filenameTemplate = it)) },
                    label = { Text("Template") },
                    supportingText = { Text("Gunakan {project}, {chapter}, {index}") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
                val preview = FilenameFormatter.formatFilename(
                    settings.filenameTemplate, settings.projectName, settings.chapterName,
                    1, settings.indexPaddingDigits, settings.outputFormat
                ) + ".${settings.outputFormat.name.lowercase()}"
                Text("Pratinjau: $preview", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                InfoBox("Input arsip ZIP/CBZ/RAR/CBR → output arsip memakai nama file yang SAMA (tanpa timestamp).")
            }

            SectionCard(title = "Pemotongan Strip") {
                Label("Mode Pisah")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SplitMode.entries.forEach { mode ->
                        val label = when (mode) {
                            SplitMode.NO_LIMIT -> "Tanpa Batas"
                            SplitMode.MAX_PIXELS -> "Batas Panjang"
                            SplitMode.PAGES_PER_FILE -> "Halaman per Berkas"
                        }
                        ChoiceChip(
                            selected = settings.splitMode == mode,
                            onClick = { onSettingsChanged(settings.copy(splitMode = mode)) },
                            label = label
                        )
                    }
                }
                if (settings.splitMode == SplitMode.MAX_PIXELS) {
                    Text("Panjang maks: ${settings.maxPixelLength}px", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = settings.maxPixelLength.toFloat(),
                        onValueChange = { onSettingsChanged(settings.copy(maxPixelLength = it.roundToInt())) },
                        valueRange = 2000f..30000f
                    )
                }
                if (settings.splitMode == SplitMode.PAGES_PER_FILE) {
                    OutlinedTextField(
                        value = settings.maxPagesPerFile.toString(),
                        onValueChange = { v ->
                            v.toIntOrNull()?.let { onSettingsChanged(settings.copy(maxPagesPerFile = it.coerceIn(1, 200))) }
                        },
                        label = { Text("Halaman per berkas") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
                InfoBox("Potongan SELALU tepat di batas halaman asli — tidak pernah di tengah konten. Satu-satunya pengecualian: satu halaman yang lebih panjang dari batas, dibelah di celah kertas oleh Mochi Smart.")
            }

            SectionCard(title = "Mochi Smart") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Aktifkan Mochi Smart", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Belah halaman raksasa hanya di baris kertas kosong.",
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
                    Label("Ketegasan")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Strictness.entries.forEach { level ->
                            ChoiceChip(
                                selected = settings.strictness == level,
                                onClick = { onSettingsChanged(settings.copy(strictness = level)) },
                                label = if (level == Strictness.STRICT) "Ketat" else "Normal"
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Tandai perlu tinjauan manual", modifier = Modifier.weight(1f))
                        Switch(
                            checked = settings.showManualReviewMarkers,
                            onCheckedChange = { onSettingsChanged(settings.copy(showManualReviewMarkers = it)) }
                        )
                    }
                }
            }

            SectionCard(title = "Tema") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        val label = when (mode) {
                            ThemeMode.SYSTEM -> "Ikuti Sistem"
                            ThemeMode.LIGHT -> "Terang"
                            ThemeMode.DARK -> "Gelap"
                        }
                        ChoiceChip(
                            selected = settings.themeMode == mode,
                            onClick = { onSettingsChanged(settings.copy(themeMode = mode)) },
                            label = label
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    androidx.compose.material3.Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ChoiceChip(selected: Boolean, onClick: () -> Unit, label: String) {
    Surface(
        selected = selected,
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selected) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
        }
    }
}

@Composable
private fun InfoBox(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
