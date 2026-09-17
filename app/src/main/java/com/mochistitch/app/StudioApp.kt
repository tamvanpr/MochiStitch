package com.mochistitch.app

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mochistitch.app.batch.BatchBoard
import com.mochistitch.app.library.LibraryHome
import com.mochistitch.app.studio.Workbench
import com.mochistitch.core.ui.SettingsPanel
import com.mochistitch.core.ui.SlicePreview
import kotlinx.coroutines.launch

private data class Deck(val screen: StudioScreen, val label: String, val icon: ImageVector)

private val DECKS = listOf(
    Deck(StudioScreen.LIBRARY, "Pustaka", Icons.Default.Album),
    Deck(StudioScreen.BATCH, "Batch", Icons.Default.Layers),
    Deck(StudioScreen.SETTINGS, "Setelan", Icons.Default.SettingsSuggest)
)

@Composable
fun StudioApp(viewModel: StudioViewModel, onExitApp: () -> Unit = {}, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var lastBack by remember { mutableLongStateOf(0L) }

    BackHandler {
        when (state.screen) {
            StudioScreen.STUDIO -> viewModel.travel(StudioScreen.LIBRARY)
            StudioScreen.RESULT -> {
                viewModel.dropSlices()
                viewModel.travel(StudioScreen.STUDIO)
            }
            else -> {
                val now = System.currentTimeMillis()
                if (now - lastBack < 2000) onExitApp() else {
                    lastBack = now
                    scope.launch { Toast.makeText(context, "Sekali lagi untuk keluar", Toast.LENGTH_SHORT).show() }
                }
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
            when (state.screen) {
                StudioScreen.STUDIO -> Workbench(viewModel = viewModel, modifier = modifier)
                StudioScreen.RESULT -> SlicePreview(
                    slices = state.slices,
                    showFlags = state.settings.showReviewFlags,
                    onPublish = { viewModel.publish(context) },
                    onBack = {
                        viewModel.dropSlices()
                        viewModel.travel(StudioScreen.STUDIO)
                    },
                    modifier = modifier
                )
                else -> {
                    Scaffold(
                        bottomBar = {
                            NavigationBar {
                                DECKS.forEach { deck ->
                                    NavigationBarItem(
                                        selected = state.screen == deck.screen,
                                        onClick = { viewModel.travel(deck.screen) },
                                        icon = { Icon(deck.icon, contentDescription = deck.label) },
                                        label = { Text(deck.label) }
                                    )
                                }
                            }
                        },
                        modifier = modifier
                    ) { _ ->
                        when (state.screen) {
                            StudioScreen.LIBRARY -> LibraryHome(viewModel = viewModel)
                            StudioScreen.BATCH -> BatchBoard(viewModel = viewModel)
                            StudioScreen.SETTINGS -> SettingsPanel(
                                settings = state.settings,
                                onChange = { viewModel.keepSettings(it) },
                                onBack = { viewModel.travel(StudioScreen.LIBRARY) }
                            )
                            else -> LibraryHome(viewModel = viewModel)
                        }
                    }
                }
            }
            BusyVeil(busy = state.busy, phase = state.phase, fraction = state.fraction)
            PublishedSheet(state = state, viewModel = viewModel, context = context)
            FailurePop(state = state, viewModel = viewModel)
        }
    }
}

@Composable
private fun BusyVeil(busy: Boolean, phase: String, fraction: Float) {
    if (!busy) return
    androidx.compose.ui.window.Dialog(onDismissRequest = {}) {
        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.padding(24.dp)) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    phase.ifBlank { "Bekerja…" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                )
                LinearProgressIndicator(progress = { fraction.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Text("${(fraction * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun PublishedSheet(state: StudioState, viewModel: StudioViewModel, context: android.content.Context) {
    val published = state.published ?: return
    AlertDialog(
        onDismissRequest = { viewModel.clearPublished() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Terbit!")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(published.projectTitle, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Text("${published.packs} berkas", style = MaterialTheme.typography.bodySmall)
                Text(published.path ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { viewModel.clearPublished() }) { Text("Tutup") }
                published.path?.let { path ->
                    Button(onClick = { viewModel.shareFolder(context, path) }) { Text("Bagikan") }
                }
            }
        }
    )
}

@Composable
private fun FailurePop(state: StudioState, viewModel: StudioViewModel) {
    val failure = state.failure ?: return
    // BatchBoard menampilkan galatnya sendiri; jangan ganda.
    if (state.screen == StudioScreen.BATCH) return
    AlertDialog(
        onDismissRequest = { viewModel.clearFailure() },
        title = { Text("Gagal") },
        text = { Text(failure) },
        confirmButton = { TextButton(onClick = { viewModel.clearFailure() }) { Text("OK") } }
    )
}
