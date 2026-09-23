package com.mochistitch.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mochistitch.core.archive.ArchiveItem
import com.mochistitch.core.archive.ArchiveKit
import com.mochistitch.core.common.ComicProject
import com.mochistitch.core.imaging.BuildPhase
import com.mochistitch.core.imaging.BuiltStrip
import com.mochistitch.core.imaging.FileNamer
import com.mochistitch.core.imaging.StripBuilder
import com.mochistitch.core.settings.PackFormat
import com.mochistitch.core.settings.StitchSettings
import com.mochistitch.core.settings.StitchSettingsRepository
import com.mochistitch.core.ui.PageItem
import com.mochistitch.core.ui.SliceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class StudioScreen { INPUT, SETUP, RESULT, QUEUE }

data class PublishedFile(
    val projectTitle: String,
    val path: String?,
    val shareUri: Uri? = null,
    val packs: Int,
    val bytes: Long,
    val error: String? = null
)

data class OutInfo(val path: String?, val packs: Int, val bytes: Long, val shareUri: Uri? = null)

data class StudioState(
    val screen: StudioScreen = StudioScreen.INPUT,
    val pages: List<PageItem> = emptyList(),
    val slices: List<SliceInfo> = emptyList(),
    val settings: StitchSettings = StitchSettings(),
    val busy: Boolean = false,
    val phase: String = "",
    val fraction: Float = 0f,
    val published: PublishedFile? = null,
    val failure: String? = null,
    val notice: String? = null,
    val comics: List<ComicProject> = emptyList(),
    val activeComicId: String? = null,
    val activeOrigin: String? = null,
    val batchOutcomes: List<PublishedFile> = emptyList()
)

class StudioViewModel : ViewModel() {

    private val _state = MutableStateFlow(StudioState())
    val state: StateFlow<StudioState> = _state.asStateFlow()

    private var repo: StitchSettingsRepository? = null
    private var settingsSaveJob: Job? = null

    fun boot(context: Context) {
        if (repo != null) return
        val r = StitchSettingsRepository(context)
        repo = r
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(settings = r.flow.first()) }
            r.flow.collect { s -> _state.update { it.copy(settings = s) } }
        }
    }

    fun travel(to: StudioScreen) {
        _state.update { it.copy(screen = to) }
    }

    fun keepSettings(s: StitchSettings) {
        // State langsung, simpan tunda 600ms: tanpa ini gema DataStore
        // menimpa ketikan cepat (hapus teks mental-mental).
        _state.update { it.copy(settings = s) }
        settingsSaveJob?.cancel()
        settingsSaveJob = viewModelScope.launch {
            delay(600)
            try {
                repo?.save(_state.value.settings)
            } catch (t: Throwable) {
                // Abaikan kegagalan simpan latar.
            }
        }
    }

    fun notify(msg: String) {
        _state.update { it.copy(notice = msg) }
    }

    fun clearNotice() {
        _state.update { it.copy(notice = null) }
    }

    fun clearFailure() {
        _state.update { it.copy(failure = null) }
    }

    fun clearPublished() {
        _state.update { it.copy(published = null) }
    }

    fun clearBatchOutcomes() {
        _state.update { it.copy(batchOutcomes = emptyList()) }
    }

    // ── Antrean ─────────────────────────────────────────────────────

    fun openComic(id: String) {
        val comic = _state.value.comics.find { it.id == id } ?: return
        dropSlices()
        val items = comic.pageUris.mapIndexed { i, uri ->
            PageItem(uri = uri, title = comic.pageNames.getOrElse(i) { "Halaman ${i + 1}" })
        }
        _state.update {
            it.copy(pages = items, activeComicId = comic.id, activeOrigin = comic.origin, screen = StudioScreen.INPUT)
        }
    }

    fun forgetComic(id: String) {
        _state.update { s ->
            val rest = s.comics.filterNot { it.id == id }
            val cleared = s.activeComicId == id
            s.copy(
                comics = rest,
                activeComicId = if (cleared) null else s.activeComicId,
                activeOrigin = if (cleared) null else s.activeOrigin
            )
        }
    }

    fun setComicPack(id: String, pack: PackFormat?) {
        _state.update { s ->
            s.copy(comics = s.comics.map { if (it.id == id) it.copy(packOverride = pack) else it })
        }
    }

    private fun shelve(origin: String, items: List<PageItem>) {
        val comic = ComicProject(
            origin = origin.ifBlank { "Komik ${System.currentTimeMillis()}" },
            pageUris = items.map { it.uri },
            pageNames = items.map { it.title }
        )
        _state.update { s ->
            s.copy(comics = s.comics + comic, activeComicId = comic.id, activeOrigin = comic.origin)
        }
    }

    /** Samakan halaman meja kerja ke komik aktif agar antrean tidak basi. */
    private fun syncActiveComic() {
        _state.update { s ->
            val id = s.activeComicId ?: return@update s
            s.copy(
                comics = s.comics.map { comic ->
                    if (comic.id == id) {
                        comic.copy(
                            pageUris = s.pages.map { it.uri },
                            pageNames = s.pages.map { it.title }
                        )
                    } else {
                        comic
                    }
                }
            )
        }
    }

    // ── Impor ────────────────────────────────────────────────────────

    fun takeImages(uris: List<Uri>, context: Context) {
        boot(context)
        viewModelScope.launch(Dispatchers.IO) {
            val known = _state.value.pages.map { it.uri.toString() }.toSet()
            val fresh = uris.distinctBy { it.toString() }.filterNot { known.contains(it.toString()) }
            val dupes = uris.size - fresh.size
            if (fresh.isEmpty()) {
                if (dupes > 0) _state.update { it.copy(notice = "Duplikat dilewati ($dupes)") }
                return@launch
            }
            val items = fresh.map { uri ->
                try {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: SecurityException) {
                    // Photo picker tidak memberi izin persisten — abaikan.
                }
                PageItem(uri = uri, title = readName(context, uri) ?: uri.lastPathSegment ?: "Gambar")
            }
            _state.update { s ->
                s.copy(
                    pages = s.pages + items,
                    notice = if (dupes > 0) "Duplikat dilewati ($dupes)" else null
                )
            }
        }
    }

    fun takeArchive(uri: Uri, context: Context) {
        boot(context)
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(busy = true, phase = "Membongkar arsip", fraction = 0f, failure = null) }
            try {
                val name = readName(context, uri) ?: uri.lastPathSegment?.substringAfterLast('/') ?: "arsip"
                if (!ArchiveKit.canOpen(name)) {
                    _state.update { it.copy(busy = false, failure = "Arsip tidak didukung: $name") }
                    return@launch
                }
                try {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: SecurityException) {
                    // Abaikan.
                }
                val stream = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("Tidak dapat membuka: $name")
                // Streaming ke disk, satu folder unik per impor: arsip besar
                // tidak membebani RAM dan komik lama di antrean tidak rusak.
                val stem = ArchiveKit.sanitizeName(ArchiveKit.baseNameOf(name).ifBlank { "arsip" })
                val unique = "${stem}_${System.currentTimeMillis()}"
                val dir = File(File(context.cacheDir, "studio_import"), unique).apply { mkdirs() }
                val unpacked = stream.use { ArchiveKit.unpackTo(it, name, dir) }
                if (unpacked.isEmpty()) {
                    try { dir.deleteRecursively() } catch (t: Throwable) { }
                    _state.update { it.copy(busy = false, failure = "Arsip kosong: $name") }
                    return@launch
                }
                val items = unpacked.map { page ->
                    PageItem(uri = Uri.fromFile(page.file), title = page.name)
                }
                shelve(name, items)
                _state.update { s ->
                    s.copy(busy = false, pages = items, screen = StudioScreen.INPUT, notice = "$name: ${items.size} halaman.")
                }
            } catch (e: Throwable) {
                _state.update { it.copy(busy = false, failure = e.message ?: "Gagal impor arsip.") }
            }
        }
    }

    // ── Meja kerja ──────────────────────────────────────────────────

    fun shiftEarlier(index: Int) {
        if (index <= 0) return
        _state.update { s ->
            val list = s.pages.toMutableList()
            val item = list.removeAt(index)
            list.add(index - 1, item)
            s.copy(pages = list)
        }
    }

    fun shiftLater(index: Int) {
        _state.update { s ->
            val list = s.pages.toMutableList()
            if (index < 0 || index >= list.size - 1) return@update s
            val item = list.removeAt(index)
            list.add(index + 1, item)
            s.copy(pages = list)
        }
    }

    fun dropPage(index: Int) {
        _state.update { s ->
            val list = s.pages.toMutableList()
            if (index in list.indices) list.removeAt(index)
            s.copy(pages = list)
        }
    }

    fun wipePages() {
        dropSlices()
        _state.update { it.copy(pages = emptyList(), activeOrigin = null, activeComicId = null) }
    }

    fun dropSlices() {
        _state.value.slices.forEach { slice ->
            slice.cachePath?.let { path ->
                try { File(path).delete() } catch (t: Throwable) { }
            }
        }
        _state.update { it.copy(slices = emptyList()) }
    }

    fun assemble(context: Context) {
        boot(context)
        if (_state.value.pages.isEmpty()) {
            _state.update { it.copy(failure = "Studio kosong — impor halaman dulu.") }
            return
        }
        // Komik aktif diperbarui; belum ada -> jadikan komik baru agar
        // masuk antrean batch dengan daftar halaman terkini.
        if (_state.value.activeComicId == null) {
            shelve(_state.value.activeOrigin ?: "Rakitan", _state.value.pages)
        } else {
            syncActiveComic()
        }
        dropSlices()
        _state.update { it.copy(busy = true, phase = "Menata halaman", fraction = 0f, failure = null, published = null) }
        viewModelScope.launch {
            try {
                val settings = _state.value.settings
                val done = StripBuilder(context).build(
                    uris = _state.value.pages.map { it.uri },
                    settings = settings,
                    onProgress = { phase, p -> _state.update { it.copy(phase = phase.label, fraction = p) } }
                ).getOrThrow()
                val slices = done.map { strip ->
                    SliceInfo(
                        order = strip.order,
                        fileName = strip.fileName,
                        bitmap = strip.preview,
                        cachePath = strip.file.absolutePath,
                        width = strip.width,
                        height = strip.height,
                        flagged = strip.flagged,
                        flagReason = strip.flagReason,
                        bytes = strip.bytes
                    )
                }
                _state.update { it.copy(busy = false, slices = slices, screen = StudioScreen.RESULT) }
            } catch (e: Throwable) {
                val oom = e is OutOfMemoryError || (e.message?.contains("OutOfMemory", ignoreCase = true) == true)
                _state.update { it.copy(busy = false, failure = if (oom) "Memori tidak cukup." else (e.message ?: "Gagal merakit.")) }
            }
        }
    }

    // ── Terbit ───────────────────────────────────────────────────────

    fun defaultFileName(): String {
        val s = _state.value.settings
        val series = s.seriesTitle.ifBlank { "MochiStitch" }
        val chapter = s.chapterLabel.ifBlank { "1" }
        return when (s.packFormat) {
            PackFormat.CBZ -> "${series}_ch${chapter}.cbz"
            PackFormat.ZIP -> "${series}_ch${chapter}.zip"
            PackFormat.FILES -> FileNamer.numbered(s.namePattern, series, chapter, 1, s.numberWidth, s.imageFormat)
        }
    }

    fun exportMime(): String = when (_state.value.settings.packFormat) {
        PackFormat.CBZ -> "application/x-cbz"
        PackFormat.ZIP -> "application/zip"
        PackFormat.FILES -> FileNamer.mimeOf(_state.value.settings.imageFormat)
    }

    fun publish(context: Context) {
        val slices = _state.value.slices
        if (slices.isEmpty()) {
            _state.update { it.copy(failure = "Belum ada hasil rakitan.") }
            return
        }
        val settings = _state.value.settings
        _state.update { it.copy(busy = true, phase = "Menerbitkan", fraction = 0f, failure = null) }
        viewModelScope.launch {
            try {
                val info = writeOut(
                    context = context,
                    files = slices.map { it.fileName to it.cachePath?.let { path -> File(path) } },
                    origin = _state.value.activeOrigin,
                    pack = settings.packFormat,
                    onProgress = { p -> _state.update { it.copy(fraction = p) } }
                )
                _state.update {
                    it.copy(
                        busy = false,
                        published = PublishedFile(
                            projectTitle = _state.value.activeOrigin ?: defaultFileName(),
                            path = info.path,
                            shareUri = info.shareUri,
                            packs = info.packs,
                            bytes = info.bytes
                        )
                    )
                }
            } catch (e: Throwable) {
                _state.update { it.copy(busy = false, failure = e.message ?: "Gagal menerbitkan.") }
            }
        }
    }

    fun runBatch(context: Context) {
        boot(context)
        val comics = _state.value.comics.filter { it.pageUris.isNotEmpty() }
        if (comics.isEmpty()) {
            _state.update { it.copy(failure = "Antrean batch kosong.") }
            return
        }
        _state.update { it.copy(busy = true, phase = "Batch berjalan", fraction = 0f, failure = null, batchOutcomes = emptyList()) }
        viewModelScope.launch {
            val outcomes = mutableListOf<PublishedFile>()
            val base = _state.value.settings
            comics.forEachIndexed { pi, comic ->
                _state.update { s ->
                    s.copy(phase = "${comic.origin} (${pi + 1}/${comics.size})", fraction = pi.toFloat() / comics.size.toFloat())
                }
                val pack = comic.packFor(base.packFormat)
                var built: List<BuiltStrip> = emptyList()
                try {
                    built = StripBuilder(context).build(
                        uris = comic.pageUris,
                        settings = base,
                        onProgress = { _, p -> _state.update { it.copy(fraction = (pi + p) / comics.size.toFloat()) } }
                    ).getOrThrow()
                    val info = writeOut(
                        context = context,
                        files = built.map { it.fileName to it.file },
                        origin = comic.origin,
                        pack = pack
                    )
                    outcomes.add(PublishedFile(comic.origin, info.path, info.shareUri, info.packs, info.bytes))
                } catch (e: Throwable) {
                    outcomes.add(PublishedFile(comic.origin, null, null, 0, 0L, e.message ?: "Gagal."))
                } finally {
                    // Berkas sementara selalu dibuang, termasuk saat gagal.
                    built.forEach { strip ->
                        try { strip.file.delete() } catch (t: Throwable) { }
                    }
                }
            }
            _state.update { it.copy(busy = false, fraction = 1f, batchOutcomes = outcomes) }
        }
    }

    private suspend fun writeOut(
        context: Context,
        files: List<Pair<String, File?>>,
        origin: String?,
        pack: PackFormat,
        onProgress: suspend (Float) -> Unit = {}
    ): OutInfo = withContext(Dispatchers.IO) {
        val useMedia = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())

        if (pack == PackFormat.CBZ || pack == PackFormat.ZIP) {
            val name = when {
                origin != null && ArchiveKit.canOpen(origin) -> FileNamer.packName(origin, pack)
                origin != null && origin.isNotBlank() -> FileNamer.packName(origin, pack, stamp)
                else -> FileNamer.packName(defaultFileName(), pack, stamp)
            }
            val mime = if (pack == PackFormat.CBZ) "application/x-cbz" else "application/zip"
            // Rakit dulu ke cache, lalu terbitkan via MediaStore (wajib di Android 10+).
            onProgress(0.3f)
            val tmp = File.createTempFile("mochi_out", ".tmp", context.cacheDir)
            try {
                var written = 0L
                tmp.outputStream().use { out ->
                    val entries = files.map { (entryName, src) ->
                        ArchiveItem(entryName) { o ->
                            if (src != null && src.exists()) src.inputStream().use { it.copyTo(o) }
                        }
                    }
                    written = ArchiveKit.pack(entries, out)
                }
                onProgress(0.8f)
                val size = if (tmp.exists()) tmp.length() else written
                if (useMedia) {
                    val uri = mediaPublish(context, tmp, name, mime, null)
                    try { tmp.delete() } catch (t: Throwable) { }
                    onProgress(1f)
                    // Satu arsip = satu berkas keluaran.
                    OutInfo("Download/MochiStitch/$name", 1, size, uri)
                } else {
                    val dest = uniqueDestination(File(picturesRoot(), "MochiStitch"), name)
                    dest.parentFile?.mkdirs()
                    tmp.copyTo(dest, overwrite = true)
                    tmp.delete()
                    onProgress(1f)
                    OutInfo(dest.absolutePath, 1, size, shareUriFor(context, dest))
                }
            } catch (e: Throwable) {
                try { tmp.delete() } catch (t: Throwable) { }
                throw e
            }
        } else {
            val folderName = "lepas_$stamp"
            var bytes = 0L
            var firstUri: Uri? = null
            var firstPath: String? = null
            var firstFile: File? = null
            files.forEachIndexed { i, (entryName, src) ->
                onProgress((i + 1).toFloat() / files.size.toFloat())
                if (src != null && src.exists()) {
                    if (useMedia) {
                        val uri = mediaPublish(context, src, entryName, FileNamer.mimeOf(_state.value.settings.imageFormat), folderName)
                        if (firstUri == null) firstUri = uri
                        if (firstPath == null) firstPath = "Pictures/MochiStitch/$folderName/$entryName"
                        bytes += src.length()
                    } else {
                        val folder = File(picturesRoot(), "MochiStitch/$folderName").apply { mkdirs() }
                        val dest = File(folder, entryName)
                        src.copyTo(dest, overwrite = true)
                        bytes += dest.length()
                        if (firstPath == null) {
                            firstPath = dest.absolutePath
                            firstFile = dest
                        }
                    }
                }
            }
            val share = if (!useMedia) firstFile?.let { shareUriFor(context, it) } else firstUri
            OutInfo(firstPath ?: "Pictures/MochiStitch/$folderName", files.size, bytes, share)
        }
    }

    private fun picturesRoot(): File =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)

    /** Hindari menimpa berkas yang sudah ada di penyimpanan lama (API < 29). */
    private fun uniqueDestination(dir: File, name: String): File {
        var dest = File(dir, name)
        if (!dest.exists()) return dest
        val stem = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.', "")
        var n = 1
        while (dest.exists()) {
            dest = if (ext.isEmpty()) File(dir, "${stem}_${n++}") else File(dir, "${stem}_${n++}.$ext")
        }
        return dest
    }

    private fun shareUriFor(context: Context, file: File): Uri? = try {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    } catch (t: Throwable) {
        null
    }

    /** Terbitkan satu file ke galeri via MediaStore (Android 10+). */
    private fun mediaPublish(context: Context, src: File, displayName: String, mime: String, subfolder: String?): Uri? {
        val resolver = context.contentResolver
        // MediaStore.Files hanya mengizinkan Download/Documents — arsip ke Download.
        val baseDir = if (mime.startsWith("image/")) "Pictures" else "Download"
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mime)
            val rel = if (subfolder.isNullOrBlank()) "$baseDir/MochiStitch" else "$baseDir/MochiStitch/$subfolder"
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, rel)
        }
        val collection = if (mime.startsWith("image/")) {
            android.provider.MediaStore.Images.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            android.provider.MediaStore.Files.getContentUri("external")
        }
        val uri = resolver.insert(collection, values) ?: return null
        resolver.openOutputStream(uri)?.use { out ->
            src.inputStream().use { it.copyTo(out) }
        }
        return uri
    }

    fun sharePublished(context: Context) {
        val uri = _state.value.published?.shareUri
        if (uri == null) {
            _state.update { it.copy(failure = "Tidak ada tautan berbagi untuk hasil ini.") }
            return
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = exportMime()
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Bagikan").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun onCleared() {
        super.onCleared()
        dropSlices()
    }

    private fun readName(context: Context, uri: Uri): String? {
        if (uri.scheme != "content") return null
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx != -1) c.getString(idx) else null
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }
}
