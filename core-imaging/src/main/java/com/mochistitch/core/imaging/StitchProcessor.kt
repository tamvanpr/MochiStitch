package com.mochistitch.core.imaging

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Bitmap.Config
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import com.mochistitch.core.settings.AlignmentModeSetting
import com.mochistitch.core.settings.MochiStitchSettings
import com.mochistitch.core.settings.OutputWrapperFormat
import com.mochistitch.core.settings.PaddingColorSetting
import com.mochistitch.core.settings.ReadingDirection
import com.mochistitch.core.settings.SplitMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * ChunkStitchProcessor — versi baru dengan pendekatan chunk-based merging.
 *
 * Flow baru:
 * 1. Kelompokkan gambar berdasarkan tinggi kumulatif (~maxPixelLength per chunk)
 * 2. Merge setiap chunk menjadi bitmap terpisah
 * 3. Gunakan MochiSmart untuk split yang aman (hindari balon/dialog)
 * 4. Kembalikan daftar StitchResultItem
 *
 * Keunggulan:
 * - Memori lebih hemat (tidak perlu simpan semua gambar sekaligus)
 * - Lebih cepat (chunk kecil proses lebih cepat)
 * - MochiSmart tetap aktif untuk menghindari split di area teks/balon
 */
data class ImageDim(val uri: Uri, val width: Int, val height: Int)

data class StitchResultItem(
    val index: Int,
    val filename: String,
    val bitmap: Bitmap,
    val width: Int,
    val height: Int,
    val needsManualReview: Boolean = false,
    val estimatedBytes: Long = 0L
)

enum class ProcessingStage(val stepName: String) {
    GROUPING("Grouping images"),
    MERGING("Merging canvas"),
    SPLITTING("Splitting pieces"),
    EXPORTING("Exporting file")
}

class StitchProcessor(
    private val openInputStream: (Uri) -> InputStream?
) {
    constructor(context: Context) : this({ uri -> context.contentResolver.openInputStream(uri) })

    private val mergeEngine = MergeEngine(openInputStream)

    suspend fun process(
        imageUris: List<Uri>,
        settings: MochiStitchSettings,
        onProgress: (ProcessingStage, Float) -> Unit = { _, _ -> }
    ): Result<List<StitchResultItem>> = withContext(Dispatchers.IO) {
        if (imageUris.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("No input images provided."))
        }

        try {
            val mergeConfig = buildMergeConfig(settings)
            val resultItems = mutableListOf<StitchResultItem>()
            var globalIndex = 1

            // ── Step 1: Kelompokkan gambar berdasarkan tinggi ──────────────
            onProgress(ProcessingStage.GROUPING, 0.1f)
            val groups = chunkImagesByHeight(imageUris, settings, mergeConfig)
            val totalGroups = groups.size

            // ── Step 2: Merge setiap kelompok ──────────────────────────────
            for ((groupIdx, group) in groups.withIndex()) {
                val groupProgressBase = groupIdx.toFloat() / totalGroups
                val groupProgressRange = 1.0f / totalGroups

                onProgress(ProcessingStage.MERGING, groupProgressBase + 0.1f * groupProgressRange)

                // Merge satu kelompok
                var mergedBitmap = mergeEngine.mergeToBitmap(group, mergeConfig) { prog ->
                    val totalProg = groupProgressBase + (0.1f + prog * 0.5f) * groupProgressRange
                    onProgress(ProcessingStage.MERGING, totalProg)
                }.getOrNull()

                if (mergedBitmap == null) {
                    continue
                }

                // NOT doing downsample - let user's maxPixelLength setting control output size

                // ── Step 3: Split dengan MochiSmart (jika diperlukan) ────────
                onProgress(ProcessingStage.SPLITTING, groupProgressBase + 0.6f * groupProgressRange)
                
                val slicedPieces = SplitEngine.sliceBitmapDetailed(
                    source = mergedBitmap,
                    settings = settings
                )

                // ── Step 4: Buat result item ──────────────────────────────────
                for (piece in slicedPieces) {
                    // Buat copy independen agar bitmap tidak terpengaruh recycle source
                    val independentBitmap = piece.bitmap.copy(requireNotNull(piece.bitmap.config), true)
                    piece.bitmap.recycle()

                    val filename = FilenameFormatter.formatFilename(
                        template = settings.filenameTemplate,
                        project = settings.projectName,
                        chapter = settings.chapterName,
                        index = globalIndex,
                        indexPaddingDigits = settings.indexPaddingDigits,
                        format = settings.outputFormat
                    )
                    resultItems.add(
                        StitchResultItem(
                            index = globalIndex,
                            filename = filename,
                            bitmap = independentBitmap,
                            width = independentBitmap.width,
                            height = independentBitmap.height,
                            needsManualReview = piece.needsManualReview,
                            estimatedBytes = (independentBitmap.width.toLong() * independentBitmap.height * 4L)
                        )
                    )
                    globalIndex++
                }

                // Recycle bitmap merge setelah di-split
                if (!mergedBitmap.isRecycled) {
                    mergedBitmap.recycle()
                }

                onProgress(ProcessingStage.SPLITTING, groupProgressBase + 0.9f * groupProgressRange)
            }

            onProgress(ProcessingStage.SPLITTING, 1.0f)
            Result.success(resultItems)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    fun recycleAll(items: List<StitchResultItem>) {
        items.forEach { item ->
            if (!item.bitmap.isRecycled) {
                item.bitmap.recycle()
            }
        }
    }

    /**
     * Mendapatkan folder output untuk hasil stitch.
     * - Untuk ZIP/CBZ: simpan langsung di Pictures/MochiStitch/
     * - Untuk loose files: buat folder baru berdasarkan tanggal
     */
    fun getOutputFolder(context: Context, wrapperFormat: com.mochistitch.core.settings.OutputWrapperFormat): File {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val mochistitchDir = File(downloadsDir, "MochiStitch")
        
        if (!mochistitchDir.exists()) {
            mochistitchDir.mkdirs()
        }

        return if (wrapperFormat == com.mochistitch.core.settings.OutputWrapperFormat.LOOSE_FILES) {
            // Buat folder baru berdasarkan tanggal untuk loose files
            val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val timestamp = dateFormat.format(Date())
            val sessionDir = File(mochistitchDir, "session_$timestamp")
            if (!sessionDir.exists()) {
                sessionDir.mkdirs()
            }
            sessionDir
        } else {
            // Untuk ZIP/CBZ, simpan langsung di folder MochiStitch
            mochistitchDir
        }
    }

    // ─── Private helpers ───────────────────────────────────────────────────────

    /**
     * Mengelompokkan gambar berdasarkan tinggi kumulatif.
     * Setiap chunk memiliki total tinggi maksimal maxPixelLength.
     */
    private fun chunkImagesByHeight(
        imageUris: List<Uri>,
        settings: MochiStitchSettings,
        config: MergeConfig
    ): List<List<Uri>> {
        val groups = mutableListOf<List<Uri>>()
        val currentGroup = mutableListOf<Uri>()
        var currentHeight = 0
        val maxChunkHeight = settings.maxPixelLength

        // Ambil dimensi semua gambar
        val dims = imageUris.map { uri ->
            val (w, h) = getImageDimensions(uri, config)
            ImageDim(uri, w, h)
        }

        for (dim in dims) {
            // Untuk vertical: gunakan height, horizontal: gunakan width
            val itemHeight = if (config.direction == MergeDirection.VERTICAL) dim.height else dim.width
            
            // Jika satu gambar sudah lebih besar dari maxChunkHeight, buat group sendiri
            if (itemHeight > maxChunkHeight && currentGroup.isNotEmpty()) {
                groups.add(currentGroup.toList())  // FIX: buat copy agar tidak saling影响
                currentGroup.clear()
                currentHeight = 0
            }

            // Tambah ke group saat ini
            currentGroup.add(dim.uri)
            currentHeight += itemHeight

            // Jika mencapai limit, tutup group
            if (currentHeight >= maxChunkHeight && currentGroup.isNotEmpty()) {
                groups.add(currentGroup.toList())  // FIX: buat copy agar tidak saling影响
                currentGroup.clear()
                currentHeight = 0
            }
        }

        // Tambahkan group terakhir jika ada
        if (currentGroup.isNotEmpty()) {
            groups.add(currentGroup.toList())  // FIX: buat copy agar tidak saling影响
        }

        return groups
    }

    private fun getImageDimensions(uri: Uri, config: MergeConfig): Pair<Int, Int> {
        return try {
            val stream = openInputStream(uri) ?: return Pair(0, 0)
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(stream, null, options)
            stream.close()
            Pair(options.outWidth, options.outHeight)
        } catch (e: Exception) {
            Pair(0, 0)
        }
    }

    private fun buildMergeConfig(settings: MochiStitchSettings): MergeConfig {
        return MergeConfig(
            direction = when (settings.readingDirection) {
                ReadingDirection.VERTICAL -> MergeDirection.VERTICAL
                ReadingDirection.LTR -> MergeDirection.HORIZONTAL_LTR
                ReadingDirection.RTL -> MergeDirection.HORIZONTAL_RTL
            },
            alignmentMode = when (settings.alignmentMode) {
                AlignmentModeSetting.RESIZE_PROPORTIONAL -> AlignmentMode.RESIZE_PROPORTIONAL
                AlignmentModeSetting.CENTER_CROP -> AlignmentMode.CENTER_CROP
                AlignmentModeSetting.PADDING -> AlignmentMode.PADDING
            },
            paddingColor = when (settings.paddingColor) {
                PaddingColorSetting.WHITE -> PaddingColor.WHITE
                PaddingColorSetting.BLACK -> PaddingColor.BLACK
                PaddingColorSetting.TRANSPARENT -> PaddingColor.TRANSPARENT
            }
        )
    }

    /**
     * Mendapatkan folder output untuk hasil stitch.
     * - Untuk ZIP/CBZ: simpan langsung di Pictures/MochiStitch/
     * - Untuk loose files: buat folder baru berdasarkan tanggal
     */
    fun getOutputFolder(context: Context, wrapperFormat: com.mochistitch.core.settings.OutputWrapperFormat): File {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val mochistitchDir = File(downloadsDir, "MochiStitch")

        if (!mochistitchDir.exists()) {
            mochistitchDir.mkdirs()
        }

        return if (wrapperFormat == com.mochistitch.core.settings.OutputWrapperFormat.LOOSE_FILES) {
            // Buat folder baru berdasarkan tanggal untuk loose files
            val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val timestamp = dateFormat.format(Date())
            val sessionDir = File(mochistitchDir, "session_$timestamp")
            if (!sessionDir.exists()) {
                sessionDir.mkdirs()
            }
            sessionDir
        } else {
            // Untuk ZIP/CBZ, simpan langsung di folder MochiStitch
            mochistitchDir
        }
    }
}
