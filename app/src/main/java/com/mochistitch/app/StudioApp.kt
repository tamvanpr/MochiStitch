package com.mochistitch.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.mochistitch.core.settings.PackFormat
import com.mochistitch.core.ui.PageStrip
import com.mochistitch.core.ui.SettingsPanel
import com.mochistitch.core.ui.SlicePreview

/**
 * v5: empat tab bawah — Masukkan, Hasil, Antrean, Setelan.
 * Satu bilah atas + satu bilah bawah untuk seluruh aplikasi
 * (tanpa Scaffold bersarang), tombol kembali ke tab Masukkan.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioApp(viewModel: StudioViewModel, onExitApp: () -> Unit) {
    val ctx = LocalContext.current
    val state by viewModel.state.collectAsState()
    val settings = state.settings

    // Kembali sistem: dari tab lain ke Masukkan; di Masukkan tekan 2x
    // dalam 2 detik untuk keluar aplikasi (tanpa tombol X).
    var lastBackPress by remember { mutableStateOf(0L) }
    BackHandler(enabled = true) {
        if (state.screen != StudioScreen.INPUT) {
            viewModel.travel(StudioScreen.INPUT)
        } else {
            val now = System.currentTimeMillis()
            if (now - lastBackPress < 2000) {
                onExitApp()
            } else {
                lastBackPress = now
                android.widget.Toast.makeText(ctx, "Tekan kembali sekali lagi untuk keluar", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Izin tulis untuk Android 7-9 (API <= 28); Q+ pakai MediaStore.
    var pendingWrite by remember { mutableStateOf<(() -> Unit)?>(null) }
    val writePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val action = pendingWrite
        pendingWrite = null
        if (granted) {
            action?.invoke()
        } else {
            viewModel.notify("Izin penyimpanan ditolak — hasil tidak dapat disimpan.")
        }
    }
    val gatedWrite: (() -> Unit) -> Unit = { action ->
        val needsPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pendingWrite = action
            writePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            action()
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = state.screen == StudioScreen.INPUT,
                    onClick = { viewModel.travel(StudioScreen.INPUT) },
                    icon = { Icon(Icons.Default.Image, contentDescription = null) },
                    label = { Text("Masuk") }
                )
                NavigationBarItem(
                    selected = state.screen == StudioScreen.RESULT,
                    onClick = { viewModel.travel(StudioScreen.RESULT) },
                    icon = {
                        val n = state.slices.size
                        if (n > 0) {
                            BadgedBox(badge = { Badge { Text("$n") } }) {
                                Icon(Icons.Default.Collections, contentDescription = null)
                            }
                        } else {
                            Icon(Icons.Default.Collections, contentDescription = null)
                        }
                    },
                    label = { Text("Hasil") }
                )
                NavigationBarItem(
                    selected = state.screen == StudioScreen.QUEUE,
                    onClick = { viewModel.travel(StudioScreen.QUEUE) },
                    icon = {
                        val n = state.comics.size
                        if (n > 0) {
                            BadgedBox(badge = { Badge { Text("$n") } }) {
                                Icon(Icons.Default.FormatListBulleted, contentDescription = null)
                            }
                        } else {
                            Icon(Icons.Default.FormatListBulleted, contentDescription = null)
                        }
                    },
                    label = { Text("Antrean") }
                )
                NavigationBarItem(
                    selected = state.screen == StudioScreen.SETTINGS,
                    onClick = { viewModel.travel(StudioScreen.SETTINGS) },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Setelan") }
                )
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("MochiStitch", fontWeight = FontWeight.Bold)
                        Text(
                            when (state.screen) {
                                StudioScreen.INPUT -> "Masukkan halaman"
                                StudioScreen.RESULT -> "Hasil rakitan"
                                StudioScreen.QUEUE -> "Antrean batch"
                                StudioScreen.SETTINGS -> "Setelan"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.travel(StudioScreen.INPUT) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
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
            StudioScreen.SETTINGS -> SettingsPanel(
                settings = settings,
                onChange = viewModel::keepSettings,
                modifier = Modifier.padding(inner).fillMaxSize()
            )
            StudioScreen.RESULT -> if (state.slices.isEmpty()) {
                Column(modifier = Modifier.padding(inner).fillMaxSize().padding(16.dp)) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Belum ada hasil rakitan. Pilih halaman di tab Masukkan lalu tekan Rakit.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                }
            } else Column(modifier = Modifier.padding(inner).fillMaxSize()) {
                SlicePreview(
                    slices = state.slices,
                    showFlags = settings.showReviewFlags,
                    pack = settings.packFormat,
                    onPackChange = { viewModel.keepSettings(settings.copy(packFormat = it)) },
                    onPublish = { gatedWrite { viewModel.publish(ctx) } },
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
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
                onRunBatch = { gatedWrite { viewModel.runBatch(ctx) } },
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
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(72.dp)
                    )
                    Text(
                        "MochiStitch",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(state.phase.ifBlank { "Bekerja…" }, style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(
                        progress = { state.fraction.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.secondary
                    )
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
    var showUrlDialog by remember { mutableStateOf(false) }
    var urlText by remember { mutableStateOf("") }
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
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "Tambah halaman",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
                SourceRow(
                    icon = { Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    title = "Gambar",
                    desc = "Pilih dari galeri perangkat",
                    onTap = {
                        pickImages.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    }
                )
                SourceRow(
                    icon = { Icon(Icons.Default.Unarchive, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    title = "Arsip",
                    desc = "ZIP · CBZ · RAR · CBR · 7Z · CB7",
                    onTap = { pickArchive.launch(arrayOf("*/*")) }
                )
                SourceRow(
                    icon = { Icon(Icons.Default.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    title = "Unduh",
                    desc = "Tempel URL chapter/series (15 sumber RAW+EN)",
                    onTap = { urlText = ""; showUrlDialog = true }
                )
            }
        }
        if (state.pages.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Belum ada halaman. Tambah lewat Gambar, Arsip, atau Unduh di atas untuk mulai.",
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
                onClick = { viewModel.assemble(ctx) },
                enabled = state.pages.isNotEmpty() && !state.busy
            ) {
                Text("Rakit ${state.pages.size} halaman")
            }
        }
    }

    if (showUrlDialog) {
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUrlDialog = false
                        if (urlText.isNotBlank()) viewModel.fetchRaw(urlText, ctx)
                    }
                ) { Text("Lanjut") }
            },
            dismissButton = {
                TextButton(onClick = { showUrlDialog = false }) { Text("Batal") }
            },
            title = { Text("Unduh mentah") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Tempel URL chapter atau series — hasil otomatis masuk antrean.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "RAW: baozimh · wmanhua · jjabtoon · koudaimh · jjaptoon · goodtoon · manwa",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "EN: mangadex · mangapill · comick · mangageko · demonic · likemanga · mangabats · xcomic",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = urlText,
                        onValueChange = { urlText = it },
                        label = { Text("URL chapter/series") },
                        placeholder = { Text("https://…") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        )
    }

    if (state.rawChapters.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = viewModel::clearRawChapters,
            confirmButton = {
                TextButton(onClick = viewModel::clearRawChapters) { Text("Tutup") }
            },
            title = { Text("Pilih chapter${state.rawSourceLabel?.let { " · $it" } ?: ""} (${state.rawChapters.size})") },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(state.rawChapters, key = { it.id + it.url }) { ch ->
                        TextButton(
                            onClick = { viewModel.fetchChapterPick(ch, ctx) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                ch.title,
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }
        )
    }
}

/** Baris aksi vertikal: ikon + judul + deskripsi + chevron. */
@Composable
private fun SourceRow(
    icon: @Composable () -> Unit,
    title: String,
    desc: String,
    onTap: () -> Unit
) {
    Surface(
        onClick = onTap,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp)
        ) {
            icon()
            Spacer(modifier = Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueStep(
    viewModel: StudioViewModel,
    onRunBatch: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                    "Antrean kosong. Tambah lewat Gambar, Arsip, atau Unduh di tab Masuk — atau rakit sekali; tiap komik otomatis masuk antrean dan bisa diproses bersama di sini.",
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
                                IconButton(onClick = { viewModel.forgetComic(comic.id) }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Hapus",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = comic.packFor(state.settings.packFormat) == PackFormat.ZIP,
                                    onClick = { viewModel.setComicPack(comic.id, if (comic.packOverride == PackFormat.ZIP) null else PackFormat.ZIP) },
                                    label = { Text("ZIP") }
                                )
                                FilterChip(
                                    selected = comic.packFor(state.settings.packFormat) == PackFormat.CBZ,
                                    onClick = { viewModel.setComicPack(comic.id, if (comic.packOverride == PackFormat.CBZ) null else PackFormat.CBZ) },
                                    label = { Text("CBZ") }
                                )
                                FilterChip(
                                    selected = comic.packFor(state.settings.packFormat) == PackFormat.FILES,
                                    onClick = { viewModel.setComicPack(comic.id, if (comic.packOverride == PackFormat.FILES) null else PackFormat.FILES) },
                                    label = { Text("Lepas") }
                                )
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
                onClick = onRunBatch,
                enabled = !state.busy && state.comics.isNotEmpty()
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.size(8.dp))
                Text("Proses semua (${state.comics.size})")
            }
        }
    }
}
