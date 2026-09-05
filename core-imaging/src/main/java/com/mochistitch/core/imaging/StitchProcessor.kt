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

data class StitchResultItem(
    val index: Int,
    val filename: String,
    val bitmap: Bitmap,
    val width: Int,
    val height: Int,
    val needsManualReview: Boolean = false
)

class StitchProcessor(
    private val openInputStream: (Uri) -> InputStream?
) {
    constructor(context: Context) : this({ uri -> context.contentResolver.openInputStream(uri) })

    private val mergeEngine = MergeEngine(openInputStream)

    suspend fun process(
        imageUris: List<Uri>,
        settings: MochiStitchSettings,
        onProgress: (Float) -> Unit = {}
    ): Result<List<StitchResultItem>> = withContext(Dispatchers.IO) {
        if (imageUris.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("No input images provided."))
        }

        try {
            val mergeConfig = MergeConfig(
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

            val batches: List<List<Uri>> = when (settings.splitMode) {
                SplitMode.PAGES_PER_FILE -> {
                    val batchSize = settings.maxPagesPerFile.coerceAtLeast(1)
                    imageUris.chunked(batchSize)
                }
                else -> listOf(imageUris)
            }

            val resultItems = mutableListOf<StitchResultItem>()
            var totalItemIndex = 1
            val totalBatches = batches.size

            for ((batchIdx, batchUris) in batches.withIndex()) {
                val batchProgressStart = batchIdx.toFloat() / totalBatches.toFloat()
                val batchProgressRange = 1.0f / totalBatches.toFloat()

                val mergedBitmap = mergeEngine.mergeToBitmap(batchUris, mergeConfig) { prog ->
                    onProgress(batchProgressStart + prog * batchProgressRange)
                }.getOrThrow()

                val slices: List<SlicedPiece> = if (settings.splitMode == SplitMode.MAX_PIXELS) {
                    SplitEngine.sliceBitmapDetailed(
                        source = mergedBitmap,
                        settings = settings
                    )
                } else {
                    listOf(SlicedPiece(mergedBitmap, needsManualReview = false))
                }

                for (piece in slices) {
                    val filename = FilenameFormatter.formatFilename(
                        template = settings.filenameTemplate,
                        project = settings.projectName,
                        chapter = settings.chapterName,
                        index = totalItemIndex,
                        indexPaddingDigits = settings.indexPaddingDigits,
                        format = settings.outputFormat
                    )
                    resultItems.add(
                        StitchResultItem(
                            index = totalItemIndex,
                            filename = filename,
                            bitmap = piece.bitmap,
                            width = piece.bitmap.width,
                            height = piece.bitmap.height,
                            needsManualReview = piece.needsManualReview
                        )
                    )
                    totalItemIndex++
                }
            }

            onProgress(1.0f)
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
}
