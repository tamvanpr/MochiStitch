package com.mochistitch.core.imaging

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.mochistitch.core.settings.AlignmentModeSetting
import com.mochistitch.core.settings.MochiStitchSettings
import com.mochistitch.core.settings.PaddingColorSetting
import com.mochistitch.core.settings.ReadingDirection
import com.mochistitch.core.settings.SplitMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

/**
 * StitchProcessor — orkestrasi lengkap: merge → split → output.
 *
 * Flow:
 * 1. Kelompokkan input sesuai splitMode (PAGES_PER_FILE atau MAX_PIXELS)
 * 2. Merge setiap kelompok menjadi bitmap
 * 3. Split bitmap jika perlu (MAX_PIXELS mode)
 * 4. Kembalikan daftar StitchResultItem
 */
data class StitchResultItem(
    val index: Int,
    val filename: String,
    val bitmap: Bitmap,
    val width: Int,
    val height: Int,
    val needsManualReview: Boolean = false
)

enum class ProcessingStage(val stepName: String) {
    ALIGNING("Aligning images"),
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
            val totalBatches = computeBatchCount(imageUris, settings)

            for ((batchIdx, batchUris) in imageUris.batches(settings).withIndex()) {
                val batchProgressBase = batchIdx.toFloat() / totalBatches
                val batchProgressRange = 1.0f / totalBatches

                // ── Step 1: Merge ──────────────────────────────────────────
                onProgress(ProcessingStage.MERGING, batchProgressBase + 0.1f * batchProgressRange)

                val mergedBitmap = mergeEngine.mergeToBitmap(batchUris, mergeConfig) { prog ->
                    val totalProg = batchProgressBase + (0.1f + prog * 0.5f) * batchProgressRange
                    onProgress(ProcessingStage.MERGING, totalProg)
                }.getOrNull()

                if (mergedBitmap == null) {
                    onProgress(ProcessingStage.MERGING, batchProgressBase + 0.6f * batchProgressRange)
                    // Merge gagal untuk batch ini, lewati
                    continue
                }

                // ── Step 2: Split (jika perlu) ────────────────────────────
                val slicedPieces: List<SlicedPiece>
                if (settings.splitMode == SplitMode.MAX_PIXELS) {
                    onProgress(ProcessingStage.SPLITTING, batchProgressBase + 0.6f * batchProgressRange)
                    slicedPieces = SplitEngine.sliceBitmapDetailed(
                        source = mergedBitmap,
                        settings = settings
                    )
                } else {
                    slicedPieces = listOf(SlicedPiece(mergedBitmap, needsManualReview = false))
                }

                onProgress(ProcessingStage.SPLITTING, batchProgressBase + 0.9f * batchProgressRange)

                // ── Step 3: Buat result item ──────────────────────────────
                for (piece in slicedPieces) {
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
                            bitmap = piece.bitmap,
                            width = piece.bitmap.width,
                            height = piece.bitmap.height,
                            needsManualReview = piece.needsManualReview
                        )
                    )
                    globalIndex++
                }

                // Recycle bitmap asli setelah discopy via slicing
                if (!mergedBitmap.isRecycled) {
                    mergedBitmap.recycle()
                }
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

    // ─── Private helpers ───────────────────────────────────────────────────────

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
     * Menghitung jumlah batch berdasarkan splitMode.
     */
    private fun computeBatchCount(imageUris: List<Uri>, settings: MochiStitchSettings): Int {
        return when (settings.splitMode) {
            SplitMode.PAGES_PER_FILE -> {
                val batchSize = settings.maxPagesPerFile.coerceAtLeast(1)
                (imageUris.size + batchSize - 1) / batchSize // ceiling division
            }
            else -> 1
        }
    }

    /**
     * Membagi URI menjadi batch-batch sesuai splitMode.
     */
    private fun List<Uri>.batches(settings: MochiStitchSettings): List<List<Uri>> {
        return when (settings.splitMode) {
            SplitMode.PAGES_PER_FILE -> {
                val batchSize = settings.maxPagesPerFile.coerceAtLeast(1)
                this.chunked(batchSize)
            }
            else -> listOf(this)
        }
    }
}
