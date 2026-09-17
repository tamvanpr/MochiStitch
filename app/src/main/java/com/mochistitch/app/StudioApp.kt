package com.mochistitch.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.mochistitch.core.settings.PackFormat
import com.mochistitch.core.ui.PageStrip
import com.mochistitch.core.ui.SettingsPanel
import com.mochistitch.core.ui.SlicePreview

/**
 * v4: wizard 3 langkah — Masukkan -> Atur -> Hasil — plus layar Antrean.
 * Tanpa bottom-nav, tanpa layar setelan terpisah: semua setelan inline
 * di langkah Atur, semua aksi dalam satu alur maju yang jelas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioApp(viewModel: StudioViewModel, onExitApp: () -> Unit) {
    val ctx = LocalContext.current
    val state by viewModel.state.collectAsState()
    val settings = state.settings

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("MochiStitch", fontWeight = FontWeight.Bold)
                        Text(
                            when (state.screen) {
                                StudioScreen.INPUT -> "Langkah 1 dari 3 · Masukkan halaman"
                                StudioScreen.SETUP -> "Langkah 2 dari 3 · Atur & rakit"
                                StudioScreen.RESULT -> "Langkah 3 dari 3 · Hasil"
                                StudioScreen.QUEUE -> "Antrean batch"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onExitApp) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.travel(StudioScreen.QUEUE) },
                        enabled = state.screen != StudioScreen.QUEUE
                    ) {
                        Text("Antrean (${state.comics.size})")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { inner ->
        when (state.screen) {
            StudioScreen.INPUT -> InputStep(
                viewModel = viewModel,
                modifier = Modifier.padding(inner).fillMaxSize()
            )
            StudioScreen.SETUP -> Column(modifier = Modifier.padding(inner).fillMaxSize()) {
                SettingsPanel(
                    settings = settings,
                    onChange = viewModel::keepSettings,
                    onBack = { viewModel.travel(StudioScreen.INPUT) },
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { viewModel.assemble(ctx) },
                    enabled = !state.busy && state.pages.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Text("Rakit ${state.pages.size} halaman")
                }
            }
            StudioScreen.RESULT -> Column(modifier = Modifier.padding(inner).fillMaxSize()) {
                SlicePreview(
                    slices = state.slices,
                    showFlags = settings.showReviewFlags,
                    pack = settings.packFormat,
                    onPackChange = { viewModel.keepSettings(settings.copy(packFormat = it)) },
                    onPublish = { viewModel.publish(ctx) },
                    onBack = { viewModel.travel(StudioScreen.SETUP) },
                    modifier = Modifier.weight(1f)
                )
                if (state.published?.shareUri != null) {
                    OutlinedButton(
                        onClick = { viewModel.sharePublished(ctx) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Bagikan hasil")
                    }
                }
            }
            StudioScreen.QUEUE -> QueueStep(
                viewModel = viewModel,
                modifier = Modifier.padding(inner).fillMaxSize()
            )
        }
    }

    if (state.busy) {
        Dialog(onDismissRequest = {}) {
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator()
                    Text(state.phase.ifBlank { "Bekerja…" }, style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(progress = { state.fraction.coerceIn(0f, 1f) })
                }
            }
        }
    }
    state.failure?.let { msg ->
        AlertDialog(
            onDismissRequest = viewModel::clearFailure,
            confirmButton = { TextButton(onClick = viewModel::clearFailure) { Text("Tutup") } },
            title = { Text("Gagal") },
            text = { Text(msg) }
        )
    }
    state.notice?.let { msg ->
        AlertDialog(
            onDismissRequest = viewModel::clearNotice,
            confirmButton = { TextButton(onClick = viewModel::clearNotice) { Text("OK") } },
            text = { Text(msg) }
        )
    }
    state.published?.let { pub ->
        AlertDialog(
            onDismissRequest = viewModel::clearPublished,
            confirmButton = { TextButton(onClick = viewModel::clearPublished) { Text("Tutup") } },
            dismissButton = {
                if (pub.shareUri != null) TextButton(onClick = { viewModel.sharePublished(ctx) }) { Text("Bagikan") }
            },
            title = { Text("Tersimpan") },
            text = { Text("${pub.projectTitle}\n${pub.path ?: ""}\n${pub.packs} berkas · ${pub.bytes / 1024} KB") }
        )
    }
}

@Composable
private fun InputStep(viewModel: StudioViewModel, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val state by viewModel.state.collectAsState()
    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) viewModel.takeImages(uris, ctx)
    }
    val pickArchive = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.takeArchive(uri, ctx)
    }
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    pickImages.launch(
                        ActivityResultContracts.PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly
                        )
                    )
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.size(8.dp))
                Text("Gambar")
            }
            Button(
                onClick = { pickArchive.launch(arrayOf("*/*")) },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Unarchive, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.size(8.dp))
                Text("Arsip")
            }
        }
        if (state.pages.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Belum ada halaman. Pilih gambar atau arsip (ZIP/CBZ/RAR/CBR/7Z) untuk mulai.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(20.dp)
                )
            }
            Spacer(modifier = Modifier.weight(1f))
        } else {
            Text("${state.pages.size} halaman", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            PageStrip(
                pages = state.pages,
                onShiftUp = viewModel::shiftEarlier,
                onShiftDown = viewModel::shiftLater,
                onDrop = viewModel::dropPage,
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = viewModel::wipePages, enabled = state.pages.isNotEmpty()) {
                Text("Bersihkan")
            }
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = { viewModel.travel(StudioScreen.SETUP) },
                enabled = state.pages.isNotEmpty()
            ) {
                Text("Lanjut")
                Spacer(modifier = Modifier.size(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun QueueStep(viewModel: StudioViewModel, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val state by viewModel.state.collectAsState()
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (state.comics.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Antrean kosong. Impor arsip atau rakit sekali — tiap komik otomatis masuk antrean dan bisa diproses bersama di sini.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(20.dp)
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                items(state.comics, key = { it.id }) { comic ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(comic.origin, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text(
                                        "${comic.pageUris.size} halaman · ${comic.packFor(state.settings.packFormat)}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                IconButton(onClick = { viewModel.openComic(comic.id) }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Buka")
                                }
                                IconButton(onClick = { viewModel.forgetComic(comic.id) }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Hapus",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PackFormat.entries.forEach { pack ->
                                    FilterChip(
                                        selected = comic.packFor(state.settings.packFormat) == pack,
                                        onClick = {
                                            viewModel.setComicPack(
                                                comic.id,
                                                if (comic.packOverride == pack) null else pack
                                            )
                                        },
                                        label = { Text(pack.name) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        state.batchOutcomes.forEach { out ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (out.error == null) "OK · ${out.projectTitle} · ${out.packs} berkas"
                    else "Gagal · ${out.projectTitle}: ${out.error}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            if (state.batchOutcomes.isNotEmpty()) {
                TextButton(onClick = viewModel::clearBatchOutcomes) { Text("Bersihkan hasil") }
            }
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = { viewModel.runBatch(ctx) },
                enabled = !state.busy && state.comics.isNotEmpty()
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.size(8.dp))
                Text("Proses semua (${state.comics.size})")
            }
        }
    }
}
