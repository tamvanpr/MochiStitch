package com.mochistitch.app.batch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import com.mochistitch.app.StudioViewModel
import com.mochistitch.core.common.ComicProject
import com.mochistitch.core.settings.PackFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchBoard(viewModel: StudioViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Papan Batch", fontWeight = FontWeight.Bold) },
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
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "Tiap komik dikemas dengan formatnya sendiri. Arsip keluar dengan nama yang sama dengan arsip masuk.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (state.comics.isEmpty()) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Antrean masih kosong", fontWeight = FontWeight.Bold)
                        Text(
                            "Batch = memproses banyak komik sekaligus sekali jalan. Komik masuk sini otomatis setiap kamu merakit di Meja Rakit atau membongkar arsip.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.comics, key = { it.id }) { comic ->
                    BatchCard(
                        comic = comic,
                        global = state.settings.packFormat,
                        onPack = { viewModel.setComicPack(comic.id, it) },
                        onDelete = { viewModel.forgetComic(comic.id) }
                    )
                }
            }
            Button(
                onClick = { viewModel.runBatch(context) },
                enabled = !state.busy && state.comics.any { it.pageUris.isNotEmpty() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.size(8.dp))
                Text("Jalankan Batch (${state.comics.size})", fontWeight = FontWeight.Bold)
            }
        }
    }

    if (state.busy) {
        Dialog(onDismissRequest = {}) {
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.padding(24.dp)) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(state.phase.ifBlank { "Bekerja…" }, fontWeight = FontWeight.SemiBold)
                    LinearProgressIndicator(progress = { state.fraction.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    Text("${(state.fraction * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    if (state.batchOutcomes.isNotEmpty()) {
        val ok = state.batchOutcomes.count { it.error == null }
        AlertDialog(
            onDismissRequest = { viewModel.clearBatchOutcomes() },
            title = { Text("Batch Tuntas ($ok/${state.batchOutcomes.size})") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.batchOutcomes.forEach { r ->
                        Column {
                            Text(r.projectTitle, fontWeight = FontWeight.SemiBold)
                            Text(
                                r.error ?: "${r.packs} berkas • ${prettyBytes(r.bytes)}\n${r.path}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (r.error == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                            )
                            HorizontalDivider()
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.clearBatchOutcomes() }) { Text("Tutup") }
            }
        )
    }

    val failure = state.failure
    if (failure != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearFailure() },
            title = { Text("Gagal") },
            text = { Text(failure) },
            confirmButton = { TextButton(onClick = { viewModel.clearFailure() }) { Text("OK") } }
        )
    }
}

@Composable
private fun BatchCard(
    comic: ComicProject,
    global: PackFormat,
    onPack: (PackFormat?) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(comic.origin, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text("${comic.pageUris.size} halaman", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = MaterialTheme.colorScheme.error)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                PackFormat.entries.forEach { pack ->
                    val label = when (pack) {
                        PackFormat.FILES -> "Lepas"
                        PackFormat.CBZ -> "CBZ"
                        PackFormat.ZIP -> "ZIP"
                    }
                    val active = comic.packFor(global) == pack
                    FilterChip(
                        selected = active,
                        onClick = { onPack(if (active) null else pack) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }
    }
}

private fun prettyBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
    return String.format(Locale.US, "%.1f %cB", bytes / Math.pow(1024.0, exp.toDouble()), "KMGTPE"[exp - 1])
}
