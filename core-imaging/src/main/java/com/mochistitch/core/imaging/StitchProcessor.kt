package com.mochistitch.core.imaging

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.mochistitch.core.mochismart.GutterScanner
import com.mochistitch.core.settings.AlignmentModeSetting
import com.mochistitch.core.settings.MochiStitchSettings
import com.mochistitch.core.settings.PaddingColorSetting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

data class StitchResultItem(
    val index: Int,
    val filename: String,
    val previewBitmap: Bitmap,
    val cacheFile: File,
    val width: Int,
    val height: Int,
    val needsManualReview: Boolean = false,
    val reviewReason: String? = null,
    val estimatedBytes: Long = 0L
)

enum class ProcessingStage(val stepName: String) {
    GROUPING("Menata halaman per berkas"),
    RENDERING("Merender strip vertikal"),
    EXPORTING("Mengekspor berkas")
}

/**
 * Orkestrasi v2: kelompokkan halaman per file output TEPAT di batas halaman
 * ([PageAwareSplitter]), render tiap kelompok menjadi strip vertikal, lalu
 * simpan ke cache. Tidak ada deteksi konten di jalur normal — balon tidak
 * mungkin terpotong karena halaman tidak pernah dibelah.
 */
class StitchProcessor(
    private val openInputStream: (Uri) -> InputStream?,
    private val cacheDir: File
) {
    constructor(context: Context) : this(
        openInputStream = { uri -> context.contentResolver.openInputStream(uri) },
        cacheDir = context.cacheDir
    )

    private val mergeEngine = MergeEngine(openInputStream)

    suspend fun process(
        imageUris: List<Uri>,
        settings: MochiStitchSettings,
        onProgress: (ProcessingStage, Float) -> Unit = { _, _ -> }
    ): Result<List<StitchResultItem>> = withContext(Dispatchers.IO) {
        if (imageUris.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Tidak ada gambar yang dipilih."))
        }
        try {
            onProgress(ProcessingStage.GROUPING, 0.05f)

            val mergeConfig = buildMergeConfig(settings)
            val sizes = imageUris.mapNotNull { uri ->
                val (w, h) = mergeEngine.getImageDimensions(uri)
                if (w > 0 && h > 0) MergeEngine.ImageSize(uri, w, h) else null
            }
            if (sizes.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("Gagal membaca dimensi gambar."))
            }
            val refWidth = sizes.maxOf { it.width }
            val placements = mergeEngine.calculatePlacements(sizes, refWidth, mergeConfig)
            val pageRefs = placements.mapIndexed { index, item ->
                PageAwareSplitter.PageRef(index = index, height = item.pageBox.height())
            }

            val groups = PageAwareSplitter.split(
                pages = pageRefs,
                splitMode = settings.splitMode,
                maxPixelLength = settings.maxPixelLength,
                maxPagesPerFile = settings.maxPagesPerFile
            )
            if (groups.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("Tidak ada halaman yang bisa diproses."))
            }

            onProgress(ProcessingStage.GROUPING, 0.2f)

            val items = mutableListOf<StitchResultItem>()
            var outputIndex = 1
            val project = settings.projectName.ifBlank { "MochiStitch" }
            val chapter = settings.chapterName.ifBlank { "1" }

            for ((groupPosition, group) in groups.withIndex()) {
                onProgress(
                    ProcessingStage.RENDERING,
                    0.2f + 0.7f * (groupPosition.toFloat() / groups.size.toFloat())
                )
                val groupUris = group.pages.map { placements[it.index].uri }
                val groupBitmap = mergeEngine.mergeToBitmap(groupUris, mergeConfig).getOrThrow()

                if (!group.overflow) {
                    items.add(
                        saveItem(
                            bitmap = groupBitmap,
                            outputIndex = outputIndex++,
                            project = project,
                            chapter = chapter,
                            settings = settings,
                            needsReview = false,
                            reviewReason = null
                        )
                    )
                } else if (!settings.mochiSmartEnabled) {
                    // Smart mati: belah buta per batas + wajib review.
                    var y = 0
                    while (y < groupBitmap.height) {
                        val h = minOf(settings.maxPixelLength.coerceAtLeast(1), groupBitmap.height - y)
                        val slice = Bitmap.createBitmap(groupBitmap, 0, y, groupBitmap.width, h)
                        items.add(
                            saveItem(
                                bitmap = slice,
                                outputIndex = outputIndex++,
                                project = project,
                                chapter = chapter,
                                settings = settings,
                                needsReview = true,
                                reviewReason = "Mochi Smart mati — potongan buta, periksa hasil"
                            )
                        )
                        y += h
                    }
                    groupBitmap.recycle()
                } else {
                    // Satu halaman raksasa: belah HANYA di baris kertas murni.
                    val grid = GutterScanner.BitmapPixelGrid(groupBitmap)
                    val tolerance = if (settings.strictness == com.mochistitch.core.settings.Strictness.STRICT) {
                        settings.paperTolerance
                    } else {
                        settings.paperTolerance * 2f
                    }
                    val intervals = GutterScanner.splitOnPaper(
                        grid = grid,
                        maxLength = settings.maxPixelLength,
                        paperTolerance = tolerance
                    )
                    for (interval in intervals) {
                        val h = (interval.end - interval.start).coerceAtLeast(1)
                        val slice = Bitmap.createBitmap(
                            groupBitmap, 0,
                            interval.start.coerceIn(0, groupBitmap.height - 1),
                            groupBitmap.width, h.coerceAtMost(groupBitmap.height - interval.start)
                        )
                        items.add(
                            saveItem(
                                bitmap = slice,
                                outputIndex = outputIndex++,
                                project = project,
                                chapter = chapter,
                                settings = settings,
                                needsReview = interval.needsReview,
                                reviewReason = if (interval.needsReview) {
                                    "Halaman penuh tanpa celah kertas — periksa hasil"
                                } else {
                                    null
                                }
                            )
                        )
                    }
                    groupBitmap.recycle()
                }
            }

            onProgress(ProcessingStage.RENDERING, 1.0f)
            Result.success(items)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    private fun saveItem(
        bitmap: Bitmap,
        outputIndex: Int,
        project: String,
        chapter: String,
        settings: MochiStitchSettings,
        needsReview: Boolean,
        reviewReason: String?
    ): StitchResultItem {
        val extension = ImageCompressor.getFileExtension(settings.outputFormat)
        val filename = FilenameFormatter.formatFilename(
            template = settings.filenameTemplate,
            project = project,
            chapter = chapter,
            index = outputIndex,
            indexPaddingDigits = settings.indexPaddingDigits,
            format = settings.outputFormat
        )
        val outDir = File(cacheDir, "mochi_slices").apply { mkdirs() }
        val outFile = File(outDir, "$filename.$extension-${System.nanoTime()}.tmp")
        val finalFile = File(outDir, "$filename.$extension")
        outFile.outputStream().use { out ->
            bitmap.compress(
                ImageCompressor.compressFormat(settings),
                ImageCompressor.quality(settings),
                out
            )
        }
        if (finalFile.exists()) finalFile.delete()
        outFile.renameTo(finalFile)
        val item = StitchResultItem(
            index = outputIndex,
            filename = "$filename.$extension",
            previewBitmap = bitmap,
            cacheFile = finalFile,
            width = bitmap.width,
            height = bitmap.height,
            needsManualReview = needsReview,
            reviewReason = reviewReason,
            estimatedBytes = finalFile.length()
        )
        return item
    }

    private fun buildMergeConfig(settings: MochiStitchSettings): MergeConfig {
        return MergeConfig(
            alignmentMode = when (settings.alignmentMode) {
                AlignmentModeSetting.RESIZE_PROPORTIONAL -> AlignmentMode.RESIZE_PROPORTIONAL
                AlignmentModeSetting.CENTER_CROP -> AlignmentMode.CENTER_CROP
                AlignmentModeSetting.PADDING -> AlignmentMode.PADDING
            },
            paddingColor = when (settings.paddingColor) {
                PaddingColorSetting.WHITE -> PaddingColor.WHITE
                PaddingColorSetting.BLACK -> PaddingColor.BLACK
                PaddingColorSetting.TRANSPARENT -> PaddingColor.TRANSPARENT
            },
            compressFormat = ImageCompressor.compressFormat(settings),
            quality = ImageCompressor.quality(settings)
        )
    }
}
