package com.mochistitch.app.studio

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mochistitch.app.StudioScreen
import com.mochistitch.app.StudioViewModel
import com.mochistitch.core.archive.ArchiveKit
import com.mochistitch.core.imaging.FileNamer
import com.mochistitch.core.ui.PageStrip

private val ARCHIVE_MIMES = arrayOf(
    "application/zip", "application/x-zip-compressed", "application/x-cbz",
    "application/x-rar-compressed", "application/vnd.rar",
    "application/x-7z-compressed", "application/octet-stream", "*/*"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Workbench(viewModel: StudioViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val snacks = remember { SnackbarHostState() }

    val pickPages = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) viewModel.takeImages(uris.distinctBy { it.toString() }, context)
    }
    val pickPack = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.takeArchive(uri, context)
    }

    val notice = state.notice
    LaunchedEffect(notice) {
        if (notice != null) {
            snacks.showSnackbar(notice)
            viewModel.clearNotice()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.activeOrigin ?: "Meja Rakit", fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(
                            "${state.pages.size} halaman • vertikal",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.travel(StudioScreen.LIBRARY) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    if (state.pages.isNotEmpty()) {
                        IconButton(onClick = { viewModel.wipePages() }) {
                            Icon(Icons.Default.ClearAll, contentDescription = "Bersihkan")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(snacks) },
        modifier = modifier
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { pickPages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Tambah")
                }
                OutlinedButton(onClick = { pickPack.launch(ARCHIVE_MIMES) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Unarchive, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Arsip")
                }
            }

            val origin = state.activeOrigin
            if (origin != null && ArchiveKit.canOpen(origin)) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Keluaran: ${FileNamer.packName(origin, state.settings.packFormat)} (sama dengan input)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Text("Susunan Halaman", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)

            PageStrip(
                pages = state.pages,
                onShiftUp = { viewModel.shiftEarlier(it) },
                onShiftDown = { viewModel.shiftLater(it) },
                onDrop = { viewModel.dropPage(it) },
                modifier = Modifier.weight(1f)
            )

            Button(
                onClick = { viewModel.assemble(context) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = state.pages.isNotEmpty() && !state.busy,
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(if (state.busy) "Merakit…" else "Rakit Strip Vertikal", fontWeight = FontWeight.Bold)
            }
        }
    }
}
