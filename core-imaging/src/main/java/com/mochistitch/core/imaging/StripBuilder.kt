package com.mochistitch.core.imaging

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.mochistitch.core.settings.StitchSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

/** Satu berkas strip hasil rakitan. */
data class BuiltStrip(
    val order: Int,
    val fileName: String,
    val preview: Bitmap,
    val file: File,
    val width: Int,
    val height: Int,
    val flagged: Boolean = false,
    val flagReason: String? = null,
    val bytes: Long = 0L
)

enum class BuildPhase(val label: String) {
    MEASURING("Mengukur halaman"),
    ASSEMBLING("Merakit strip vertikal"),
    WRITING("Menulis berkas")
}

/**
 * Orkestrasi v4: ukur -> kelompokkan di batas halaman -> render tiap
 * kelompok -> tulis cache. Potongan HANYA di batas halaman; satu halaman
 * raksasa yang melebihi batas dibiarkan utuh dan ditandai.
 */
class StripBuilder(
    private val openStream: (Uri) -> InputStream?,
    private val scratchDir: File
) {
    constructor(context: Context) : this(
        openStream = { uri -> context.contentResolver.openInputStream(uri) },
        scratchDir = context.cacheDir
    )

    private val renderer = StripRenderer(openStream)

    suspend fun build(
        uris: List<Uri>,
        settings: StitchSettings,
        onProgress: (BuildPhase, Float) -> Unit = { _, _ -> }
    ): Result<List<BuiltStrip>> = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Tidak ada gambar."))
        try {
            onProgress(BuildPhase.MEASURING, 0.05f)
            val config = StripConfig.fromSettings(settings)
            val measured = uris.mapNotNull { uri ->
                val (w, h) = renderer.measure(uri)
                if (w > 0 && h > 0) StripRenderer.Measured(uri, w, h) else null
            }
            if (measured.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("Gagal membaca dimensi gambar."))
            }
            val stripWidth = measured.maxOf { it.width }
            val sheets = measured.mapIndexed { i, m ->
                val h = (m.height.toFloat() * stripWidth / m.width).toInt().coerceAtLeast(1)
                PageGrouper.Sheet(order = i, renderedHeight = h)
            }
            val bundles = PageGrouper.group(sheets, settings.splitRule, settings.maxStripHeight, settings.pagesPerPack)
            if (bundles.isEmpty()) return@withContext Result.failure(IllegalStateException("Tidak ada yang bisa dirakit."))

            onProgress(BuildPhase.MEASURING, 0.2f)
            val strips = mutableListOf<BuiltStrip>()
            var number = 1
            val series = settings.seriesTitle.ifBlank { "MochiStitch" }
            val chapter = settings.chapterLabel.ifBlank { "1" }

            for ((bi, bundle) in bundles.withIndex()) {
                onProgress(BuildPhase.ASSEMBLING, 0.2f + 0.6f * (bi.toFloat() / bundles.size.toFloat()))
                val bundleUris = bundle.sheets.map { measured[it.order].uri }
                val whole = renderer.renderStrip(bundleUris, config).getOrThrow()

                if (bundle.tallSingle) {
                    // Halaman raksasa: DIBIARKAN UTUH + ditandai. Tidak ada
                    // algoritma apa pun yang boleh memotong di dalam halaman.
                    strips.add(
                        store(
                            bitmap = whole, number = number++, series = series, chapter = chapter,
                            settings = settings, flagged = true,
                            flagReason = "Melebihi batas ${settings.maxStripHeight}px — dibiarkan utuh, tangani manual"
                        )
                    )
                } else {
                    strips.add(
                        store(
                            bitmap = whole, number = number++, series = series, chapter = chapter,
                            settings = settings, flagged = false, flagReason = null
                        )
                    )
                }
            }
            onProgress(BuildPhase.ASSEMBLING, 1.0f)
            Result.success(strips)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    private fun store(
        bitmap: Bitmap,
        number: Int,
        series: String,
        chapter: String,
        settings: StitchSettings,
        flagged: Boolean,
        flagReason: String?
    ): BuiltStrip {
        val ext = FileNamer.extensionOf(settings.imageFormat)
        val stem = FileNamer.numbered(settings.namePattern, series, chapter, number, settings.numberWidth, settings.imageFormat)
        val dir = File(scratchDir, "mochi_strips").apply { mkdirs() }
        val tmp = File(dir, "$stem.$ext-${System.nanoTime()}.tmp")
        tmp.outputStream().use { out ->
            bitmap.compress(StripConfig.fromSettings(settings).compressFormat, StripConfig.fromSettings(settings).quality, out)
        }
        val final = File(dir, "$stem.$ext")
        if (final.exists()) final.delete()
        tmp.renameTo(final)
        return BuiltStrip(
            order = number,
            fileName = "$stem.$ext",
            preview = bitmap,
            file = final,
            width = bitmap.width,
            height = bitmap.height,
            flagged = flagged,
            flagReason = flagReason,
            bytes = final.length()
        )
    }
}
