package com.mochistitch.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mochistitch.core.imaging.FileNamer
import com.mochistitch.core.settings.FitMode
import com.mochistitch.core.settings.ImageFormat
import com.mochistitch.core.settings.MatteColor
import com.mochistitch.core.settings.PackFormat
import com.mochistitch.core.settings.SplitRule
import com.mochistitch.core.settings.StitchSettings
import com.mochistitch.core.settings.Strictness
import com.mochistitch.core.settings.ThemeMode
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsPanel(
    settings: StitchSettings,
    onChange: (StitchSettings) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Setelan Studio", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Group("Kemasan & Gambar") {
                SubLabel("Kemasan default: ZIP")
                ChipRow {
                    PackFormat.entries.forEach { pack ->
                        OptionChip(
                            active = settings.packFormat == pack,
                            onTap = { onChange(settings.copy(packFormat = pack)) },
                            text = when (pack) {
                                PackFormat.ZIP -> "ZIP"
                                PackFormat.CBZ -> "CBZ"
                                PackFormat.FILES -> "Lepas"
                            }
                        )
                    }
                }
                SubLabel("Gambar potongan")
                ChipRow {
                    ImageFormat.entries.forEach { fmt ->
                        OptionChip(
                            active = settings.imageFormat == fmt,
                            onTap = { onChange(settings.copy(imageFormat = fmt)) },
                            text = fmt.name
                        )
                    }
                }
                if (settings.imageFormat == ImageFormat.JPG) {
                    Text("Mutu JPG ${settings.jpgQuality}%", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = settings.jpgQuality.toFloat(),
                        onValueChange = { onChange(settings.copy(jpgQuality = it.roundToInt())) },
                        valueRange = 10f..100f
                    )
                }
                if (settings.imageFormat == ImageFormat.WEBP) {
                    Text("Mutu WEBP ${settings.webpQuality}%", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = settings.webpQuality.toFloat(),
                        onValueChange = { onChange(settings.copy(webpQuality = it.roundToInt())) },
                        valueRange = 10f..100f
                    )
                }
            }

            Group("Nama Berkas") {
                OutlinedTextField(
                    value = settings.seriesTitle,
                    onValueChange = { onChange(settings.copy(seriesTitle = it)) },
                    label = { Text("Judul seri") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = settings.chapterLabel,
                    onValueChange = { onChange(settings.copy(chapterLabel = it)) },
                    label = { Text("Chapter") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = settings.namePattern,
                    onValueChange = { onChange(settings.copy(namePattern = it)) },
                    label = { Text("Pola nama") },
                    supportingText = { Text("{series} {chapter} {n}") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                val sample = FileNamer.numbered(
                    settings.namePattern, settings.seriesTitle, settings.chapterLabel,
                    1, settings.numberWidth, settings.imageFormat
                ) + ".${settings.imageFormat.name.lowercase()}"
                Text("Contoh: $sample", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                Note("Arsip dari arsip: nama output SAMA dengan nama input.")
            }

            Group("Bagi Strip") {
                SubLabel("Aturan bagi (selalu di batas halaman)")
                ChipRow {
                    SplitRule.entries.forEach { rule ->
                        OptionChip(
                            active = settings.splitRule == rule,
                            onTap = { onChange(settings.copy(splitRule = rule)) },
                            text = when (rule) {
                                SplitRule.WHOLE -> "Utuh"
                                SplitRule.MAX_HEIGHT -> "Tinggi maks"
                                SplitRule.PAGES_PER_PACK -> "Per halaman"
                            }
                        )
                    }
                }
                if (settings.splitRule == SplitRule.MAX_HEIGHT) {
                    Text("Tinggi maks ${settings.maxStripHeight}px", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = settings.maxStripHeight.toFloat(),
                        onValueChange = { onChange(settings.copy(maxStripHeight = it.roundToInt())) },
                        valueRange = 2000f..30000f
                    )
                }
                if (settings.splitRule == SplitRule.PAGES_PER_PACK) {
                    // State teks lokal agar kolom bisa dikosongkan total tanpa mental.
                    var pagesText by remember(settings.pagesPerPack) {
                        mutableStateOf(settings.pagesPerPack.toString())
                    }
                    OutlinedTextField(
                        value = pagesText,
                        onValueChange = { v ->
                            pagesText = v.filter { it.isDigit() }.take(3)
                            pagesText.toIntOrNull()?.let {
                                onChange(settings.copy(pagesPerPack = it.coerceIn(1, 200)))
                            }
                        },
                        label = { Text("Halaman per berkas") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Group("Potong Pintar") {
                ToggleRow(
                    title = "Potong pintar aktif",
                    desc = "Halaman raksasa dibelah bertumpang tindih, konten tidak hilang.",
                    checked = settings.smartCut,
                    onFlip = { onChange(settings.copy(smartCut = it)) }
                )
                if (settings.smartCut) {
                    SubLabel("Ketegasan")
                    ChipRow {
                        Strictness.entries.forEach { level ->
                            OptionChip(
                                active = settings.strictness == level,
                                onTap = { onChange(settings.copy(strictness = level)) },
                                text = if (level == Strictness.STRICT) "Ketat" else "Longgar"
                            )
                        }
                    }
                    ToggleRow(
                        title = "Penanda tinjau manual",
                        desc = "Tandai potongan yang meragukan di pratinjau.",
                        checked = settings.showReviewFlags,
                        onFlip = { onChange(settings.copy(showReviewFlags = it)) }
                    )
                }
            }

            Group("Tampilan Strip") {
                SubLabel("Penyesuaian lebar")
                ChipRow {
                    FitMode.entries.forEach { mode ->
                        OptionChip(
                            active = settings.fitMode == mode,
                            onTap = { onChange(settings.copy(fitMode = mode)) },
                            text = when (mode) {
                                FitMode.FIT_WIDTH -> "Paskan"
                                FitMode.CROP_CENTER -> "Potong tengah"
                                FitMode.LETTERBOX -> "Bingkai"
                            }
                        )
                    }
                }
                SubLabel("Warna bingkai")
                ChipRow {
                    MatteColor.entries.forEach { matte ->
                        OptionChip(
                            active = settings.matteColor == matte,
                            onTap = { onChange(settings.copy(matteColor = matte)) },
                            text = when (matte) {
                                MatteColor.WHITE -> "Putih"
                                MatteColor.BLACK -> "Hitam"
                                MatteColor.CLEAR -> "Benar"
                            }
                        )
                    }
                }
            }

            Group("Tema Aplikasi") {
                ChipRow {
                    ThemeMode.entries.forEach { mode ->
                        OptionChip(
                            active = settings.themeMode == mode,
                            onTap = { onChange(settings.copy(themeMode = mode)) },
                            text = when (mode) {
                                ThemeMode.SYSTEM -> "Sistem"
                                ThemeMode.LIGHT -> "Terang"
                                ThemeMode.DARK -> "Gelap"
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Group(title: String, body: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            body()
        }
    }
}

@Composable
private fun SubLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(body: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { body() }
}

@Composable
private fun OptionChip(active: Boolean, onTap: () -> Unit, text: String) {
    Surface(
        selected = active,
        onClick = onTap,
        shape = RoundedCornerShape(10.dp),
        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    ) {
        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            if (active) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium)
        }
    }
}

@Composable
private fun ToggleRow(title: String, desc: String, checked: Boolean, onFlip: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onFlip)
    }
}

@Composable
private fun Note(text: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
