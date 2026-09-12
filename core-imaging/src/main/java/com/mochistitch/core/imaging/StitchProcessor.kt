package com.mochistitch.core.imaging

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Environment
import com.mochistitch.core.mochismart.ContourDetector
import com.mochistitch.core.settings.AlignmentModeSetting
import com.mochistitch.core.settings.MochiStitchSettings
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
import kotlin.math.roundToInt

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

data class SliceIntervalSpec(
    val start: Int,
    val end: Int,
    val needsReview: Boolean = false,
    val reviewReason: String? = null
)

enum class ProcessingStage(val stepName: String) {
    GROUPING("Menata alur gambar"),
    MERGING("Menggabungkan canvas"),
    SPLITTING("Memotong bagian gambar"),
    EXPORTING("Mengekspor berkas")
}

class StitchProcessor(
    private val openInputStream: (Uri) -> InputStream?,
    private val cacheDir: File
) {
    constructor(context: Context) : this(
        openInputStream = { uri -> context.contentResolver.openInputStream(uri) },
        cacheDir = context.cacheDir
    )

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

            // Step 1: Hitung penempatan semua gambar pada virtual continuous canvas
            val mergeConfig = buildMergeConfig(settings)
            val sizes = imageUris.mapNotNull { uri ->
                val (w, h) = getImageDimensions(uri)
                if (w > 0 && h > 0) MergeEngine.ImageSize(uri, w, h) else null
            }

            if (sizes.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("Gagal membaca dimensi gambar."))
            }

            val maxInputWidth = sizes.maxOf { it.width }
            val maxInputHeight = sizes.maxOf { it.height }

            val placements = calculateItemPlacements(sizes, maxInputWidth, maxInputHeight, mergeConfig)
            val isVertical = mergeConfig.direction == MergeDirection.VERTICAL

            val totalCanvasWidth = if (isVertical) maxInputWidth else placements.maxOf { it.pageBox.right }
            val totalCanvasHeight = if (isVertical) placements.maxOf { it.pageBox.bottom } else maxInputHeight

            val totalLength = if (isVertical) totalCanvasHeight else totalCanvasWidth

            onProgress(ProcessingStage.GROUPING, 0.2f)

            // Step 2: Tentukan interval pemotongan (slice intervals) pada canvas gabungan
            val sliceIntervals = calculateSliceIntervals(
                totalLength = totalLength,
                isVertical = isVertical,
                totalCanvasWidth = totalCanvasWidth,
                totalCanvasHeight = totalCanvasHeight,
                placements = placements,
                settings = settings,
                mergeConfig = mergeConfig
            )

            onProgress(ProcessingStage.MERGING, 0.3f)

            // Step 3: Render setiap interval menjadi Bitmap, simpan ke File Cache, dan buat Preview Thumbnail
            val resultItems = mutableListOf<StitchResultItem>()
            val totalSlices = sliceIntervals.size

            for ((indexZeroBased, interval) in sliceIntervals.withIndex()) {
                val sliceIndex = indexZeroBased + 1 // 1-based index (1, 2, 3...)
                val (startPos, endPos, needsReview, reviewReason) = interval
                val sliceLen = endPos - startPos

                val sliceWidth = if (isVertical) totalCanvasWidth else sliceLen
                val sliceHeight = if (isVertical) sliceLen else totalCanvasHeight

                // Buat bitmap potongan
                val sliceBitmap = Bitmap.createBitmap(sliceWidth, sliceHeight, Bitmap.Config.RGB_565)
                val canvas = Canvas(sliceBitmap)
                canvas.drawColor(mergeConfig.paddingColor.colorInt)

                val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

                // Gambar item yang tumpang tindih dengan interval ini
                for (item in placements) {
                    val itemStart = if (isVertical) item.pageBox.top else item.pageBox.left
                    val itemEnd = if (isVertical) item.pageBox.bottom else item.pageBox.right

                    if (itemEnd > startPos && itemStart < endPos) {
                        val inputStream = openInputStream(item.uri) ?: continue

                        val sampleSize = calculateInSampleSize(
                            item.srcRect.width(),
                            item.srcRect.height(),
                            item.dstRect.width(),
                            item.dstRect.height()
                        )

                        val options = BitmapFactory.Options().apply {
                            inPreferredConfig = Bitmap.Config.RGB_565
                            inSampleSize = sampleSize
                        }
                        val srcBitmap = BitmapFactory.decodeStream(inputStream, null, options)
                        inputStream.close()

                        if (srcBitmap != null) {
                            val scaledSrcRect = Rect(
                                item.srcRect.left / sampleSize,
                                item.srcRect.top / sampleSize,
                                min(item.srcRect.right / sampleSize, srcBitmap.width),
                                min(item.srcRect.bottom / sampleSize, srcBitmap.height)
                            )

                            // Sesuaikan dstRect relatif terhadap slice Canvas
                            val dstRectInSlice = if (isVertical) {
                                Rect(
                                    item.dstRect.left,
                                    item.dstRect.top - startPos,
                                    item.dstRect.right,
                                    item.dstRect.bottom - startPos
                                )
                            } else {
                                Rect(
                                    item.dstRect.left - startPos,
                                    item.dstRect.top,
                                    item.dstRect.right - startPos,
                                    item.dstRect.bottom
                                )
                            }

                            canvas.drawBitmap(srcBitmap, scaledSrcRect, dstRectInSlice, paint)
                            srcBitmap.recycle()
                        }
                    }
                }

                // Simpan sliceBitmap ke file temporary di cacheDir
                val timeStamp = System.currentTimeMillis()
                val tempFile = File(cacheDir, "mochistitch_slice_${timeStamp}_${sliceIndex}.tmp")
                tempFile.outputStream().use { out ->
                    sliceBitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                }

                val previewBitmap = createPreviewThumbnail(sliceBitmap, maxDim = 800)
                val estimatedBytes = tempFile.length()

                // Recycle full-res bitmap langsung untuk membebaskan RAM
                sliceBitmap.recycle()

                val filename = FilenameFormatter.formatFilename(
                    template = settings.filenameTemplate,
                    project = settings.projectName,
                    chapter = settings.chapterName,
                    index = sliceIndex,
                    indexPaddingDigits = settings.indexPaddingDigits,
                    format = settings.outputFormat
                )

                resultItems.add(
                    StitchResultItem(
                        index = sliceIndex,
                        filename = filename,
                        previewBitmap = previewBitmap,
                        cacheFile = tempFile,
                        width = sliceWidth,
                        height = sliceHeight,
                        needsManualReview = needsReview,
                        reviewReason = reviewReason,
                        estimatedBytes = estimatedBytes
                    )
                )

                val prog = 0.3f + 0.65f * (sliceIndex.toFloat() / totalSlices.toFloat())
                onProgress(ProcessingStage.SPLITTING, prog)
            }

            onProgress(ProcessingStage.SPLITTING, 1.0f)
            Result.success(resultItems)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    fun recycleAll(items: List<StitchResultItem>) {
        items.forEach { item ->
            if (!item.previewBitmap.isRecycled) {
                item.previewBitmap.recycle()
            }
            try {
                if (item.cacheFile.exists()) {
                    item.cacheFile.delete()
                }
            } catch (t: Throwable) {
                // Ignore
            }
        }
    }

    private fun createPreviewThumbnail(source: Bitmap, maxDim: Int = 800): Bitmap {
        val w = source.width
        val h = source.height
        if (w <= maxDim && h <= maxDim) {
            return source.copy(source.config ?: Bitmap.Config.RGB_565, false)
        }
        val scale = min(maxDim.toFloat() / w, maxDim.toFloat() / h)
        val dstW = (w * scale).roundToInt().coerceAtLeast(1)
        val dstH = (h * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, dstW, dstH, true)
    }

    private fun calculateSliceIntervals(
        totalLength: Int,
        isVertical: Boolean,
        totalCanvasWidth: Int,
        totalCanvasHeight: Int,
        placements: List<MergeEngine.ItemPlacement>,
        settings: MochiStitchSettings,
        mergeConfig: MergeConfig
    ): List<SliceIntervalSpec> {
        val intervals = mutableListOf<SliceIntervalSpec>()
        val maxLen = settings.maxPixelLength

        if (settings.splitMode == SplitMode.NO_LIMIT || totalLength <= maxLen || maxLen <= 0) {
            intervals.add(SliceIntervalSpec(0, totalLength, false, null))
            return intervals
        }

        var currentPos = 0
        val minTailLength = min(600, maxLen / 4)

        while (currentPos < totalLength) {
            val remaining = totalLength - currentPos
            if (remaining <= maxLen) {
                intervals.add(SliceIntervalSpec(currentPos, totalLength, false, null))
                break
            }

            // Jika sisa setelah candidate sangat kecil, gabungkan langsung ke potongan terakhir
            if (remaining - maxLen < minTailLength) {
                intervals.add(SliceIntervalSpec(currentPos, totalLength, false, null))
                break
            }

            val candidate = currentPos + maxLen
            var safeSplit = candidate
            var needsReview = false
            var reviewReason: String? = null

            if (settings.mochiSmartEnabled) {
                val tolerance = settings.mochiSmartTolerance
                val searchMargin = max(tolerance * 3, 600)
                val bandStart = max(0, candidate - searchMargin)
                val bandEnd = min(totalLength, candidate + searchMargin)
                val bandLen = bandEnd - bandStart

                if (bandLen > 0) {
                    val bandWidth = if (isVertical) totalCanvasWidth else bandLen
                    val bandHeight = if (isVertical) bandLen else totalCanvasHeight

                    val bandBitmap = Bitmap.createBitmap(bandWidth, bandHeight, Bitmap.Config.RGB_565)
                    val canvas = Canvas(bandBitmap)
                    canvas.drawColor(mergeConfig.paddingColor.colorInt)
                    val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

                    for (item in placements) {
                        val itemStart = if (isVertical) item.pageBox.top else item.pageBox.left
                        val itemEnd = if (isVertical) item.pageBox.bottom else item.pageBox.right

                        if (itemEnd > bandStart && itemStart < bandEnd) {
                            val stream = openInputStream(item.uri) ?: continue
                            val sampleSize = calculateInSampleSize(
                                item.srcRect.width(), item.srcRect.height(),
                                item.dstRect.width(), item.dstRect.height()
                            )
                            val options = BitmapFactory.Options().apply {
                                inPreferredConfig = Bitmap.Config.RGB_565
                                inSampleSize = sampleSize
                            }
                            val srcBitmap = BitmapFactory.decodeStream(stream, null, options)
                            stream.close()

                            if (srcBitmap != null) {
                                val scaledSrcRect = Rect(
                                    item.srcRect.left / sampleSize,
                                    item.srcRect.top / sampleSize,
                                    min(item.srcRect.right / sampleSize, srcBitmap.width),
                                    min(item.srcRect.bottom / sampleSize, srcBitmap.height)
                                )
                                val dstRectInBand = if (isVertical) {
                                    Rect(item.dstRect.left, item.dstRect.top - bandStart, item.dstRect.right, item.dstRect.bottom - bandStart)
                                } else {
                                    Rect(item.dstRect.left - bandStart, item.dstRect.top, item.dstRect.right - bandStart, item.dstRect.bottom)
                                }
                                canvas.drawBitmap(srcBitmap, scaledSrcRect, dstRectInBand, paint)
                                srcBitmap.recycle()
                            }
                        }
                    }

                    val boxes = ContourDetector.detectBoundingBoxes(bandBitmap, settings.mochiSmartSensitivity)
                    val localCandidate = candidate - bandStart
                    val smartRes = ContourDetector.findSafeSplitPoint(
                        totalLength = bandLen,
                        candidate = localCandidate,
                        tolerance = tolerance,
                        isVertical = isVertical,
                        boundingBoxes = boxes,
                        bitmap = bandBitmap
                    )

                    safeSplit = bandStart + smartRes.splitPosition
                    needsReview = smartRes.needsManualReview
                    reviewReason = smartRes.reviewReason
                    bandBitmap.recycle()
                }
            }

            // Pastikan safeSplit tidak membuat potongan mini/mikro
            var effectiveSplit = safeSplit.coerceIn(currentPos + min(1000, maxLen / 2), totalLength - 1)
            if (totalLength - effectiveSplit < minTailLength) {
                effectiveSplit = totalLength
            }

            intervals.add(SliceIntervalSpec(currentPos, effectiveSplit, needsReview, reviewReason))
            currentPos = effectiveSplit
        }

        return intervals
    }

    private fun getImageDimensions(uri: Uri): Pair<Int, Int> {
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

    private fun calculateInSampleSize(reqSrcW: Int, reqSrcH: Int, reqDstW: Int, reqDstH: Int): Int {
        var sampleSize = 1
        if (reqSrcH > reqDstH * 2 || reqSrcW > reqDstW * 2) {
            val halfH = reqSrcH / 2
            val halfW = reqSrcW / 2
            while ((halfH / sampleSize) >= reqDstH && (halfW / sampleSize) >= reqDstW) {
                sampleSize *= 2
            }
        }
        return max(1, sampleSize)
    }

    private fun calculateItemPlacements(
        sizes: List<MergeEngine.ImageSize>,
        refWidth: Int,
        refHeight: Int,
        config: MergeConfig
    ): List<MergeEngine.ItemPlacement> {
        return when (config.direction) {
            MergeDirection.VERTICAL -> calculateVerticalPlacements(sizes, refWidth, refHeight, config)
            MergeDirection.HORIZONTAL_LTR -> calculateHorizontalPlacements(sizes, refWidth, refHeight, config, leftToRight = true)
            MergeDirection.HORIZONTAL_RTL -> calculateHorizontalPlacements(sizes, refWidth, refHeight, config, leftToRight = false)
        }
    }

    private fun calculateVerticalPlacements(
        sizes: List<MergeEngine.ImageSize>,
        refWidth: Int,
        refHeight: Int,
        config: MergeConfig
    ): List<MergeEngine.ItemPlacement> {
        val placements = mutableListOf<MergeEngine.ItemPlacement>()
        var currentY = 0

        for (item in sizes) {
            val placement = computePlacement(item.width, item.height, refWidth, refHeight, config.alignmentMode, isVertical = true)
            val pageBox = Rect(0, currentY, refWidth, currentY + placement.pageH)
            val adjustedDst = Rect(
                placement.dstRect.left,
                currentY + placement.dstRect.top,
                placement.dstRect.right,
                currentY + placement.dstRect.bottom
            )
            placements.add(MergeEngine.ItemPlacement(item.uri, placement.srcRect, adjustedDst, pageBox))
            currentY += placement.pageH
        }

        return placements
    }

    private fun calculateHorizontalPlacements(
        sizes: List<MergeEngine.ImageSize>,
        refWidth: Int,
        refHeight: Int,
        config: MergeConfig,
        leftToRight: Boolean
    ): List<MergeEngine.ItemPlacement> {
        val placements = mutableListOf<MergeEngine.ItemPlacement>()

        if (leftToRight) {
            var currentX = 0
            for (item in sizes) {
                val placement = computePlacement(item.width, item.height, refWidth, refHeight, config.alignmentMode, isVertical = false)
                val pageBox = Rect(currentX, 0, currentX + placement.pageW, refHeight)
                val adjustedDst = Rect(
                    currentX + placement.dstRect.left,
                    placement.dstRect.top,
                    currentX + placement.dstRect.right,
                    placement.dstRect.bottom
                )
                placements.add(MergeEngine.ItemPlacement(item.uri, placement.srcRect, adjustedDst, pageBox))
                currentX += placement.pageW
            }
        } else {
            val computed = sizes.map { item ->
                computePlacement(item.width, item.height, refWidth, refHeight, config.alignmentMode, isVertical = false)
            }
            val totalWidth = computed.sumOf { it.pageW }
            var currentX = totalWidth

            for ((index, item) in sizes.withIndex()) {
                val placement = computed[index]
                val itemStartX = currentX - placement.pageW
                val pageBox = Rect(itemStartX, 0, itemStartX + placement.pageW, refHeight)
                val adjustedDst = Rect(
                    itemStartX + placement.dstRect.left,
                    placement.dstRect.top,
                    itemStartX + placement.dstRect.right,
                    placement.dstRect.bottom
                )
                placements.add(MergeEngine.ItemPlacement(item.uri, placement.srcRect, adjustedDst, pageBox))
                currentX -= placement.pageW
            }
        }

        return placements
    }

    private fun computePlacement(
        itemWidth: Int,
        itemHeight: Int,
        refWidth: Int,
        refHeight: Int,
        alignmentMode: AlignmentMode,
        isVertical: Boolean
    ): PlacementSpec {
        return when (alignmentMode) {
            AlignmentMode.RESIZE_PROPORTIONAL -> {
                if (isVertical) {
                    val dstW = refWidth
                    val dstH = (itemHeight.toFloat() * refWidth / itemWidth).roundToInt().coerceAtLeast(1)
                    PlacementSpec(
                        pageW = refWidth, pageH = dstH,
                        srcRect = Rect(0, 0, itemWidth, itemHeight),
                        dstRect = Rect(0, 0, dstW, dstH)
                    )
                } else {
                    val dstH = refHeight
                    val dstW = (itemWidth.toFloat() * refHeight / itemHeight).roundToInt().coerceAtLeast(1)
                    PlacementSpec(
                        pageW = dstW, pageH = refHeight,
                        srcRect = Rect(0, 0, itemWidth, itemHeight),
                        dstRect = Rect(0, 0, dstW, dstH)
                    )
                }
            }

            AlignmentMode.PADDING -> {
                val scaleW = refWidth.toFloat() / itemWidth
                val scaleH = refHeight.toFloat() / itemHeight
                val scale = min(scaleW, scaleH)
                val dstW = (itemWidth * scale).roundToInt().coerceAtLeast(1)
                val dstH = (itemHeight * scale).roundToInt().coerceAtLeast(1)
                val offsetX = (refWidth - dstW) / 2
                val offsetY = (refHeight - dstH) / 2
                PlacementSpec(
                    pageW = refWidth, pageH = refHeight,
                    srcRect = Rect(0, 0, itemWidth, itemHeight),
                    dstRect = Rect(offsetX, offsetY, offsetX + dstW, offsetY + dstH)
                )
            }

            AlignmentMode.CENTER_CROP -> {
                val scaleW = refWidth.toFloat() / itemWidth
                val scaleH = refHeight.toFloat() / itemHeight
                val scale = max(scaleW, scaleH)
                val requiredSrcW = (refWidth / scale).roundToInt().coerceIn(1, itemWidth)
                val requiredSrcH = (refHeight / scale).roundToInt().coerceIn(1, itemHeight)
                val srcX = (itemWidth - requiredSrcW) / 2
                val srcY = (itemHeight - requiredSrcH) / 2
                PlacementSpec(
                    pageW = refWidth, pageH = refHeight,
                    srcRect = Rect(srcX, srcY, srcX + requiredSrcW, srcY + requiredSrcH),
                    dstRect = Rect(0, 0, refWidth, refHeight)
                )
            }
        }
    }

    private data class PlacementSpec(
        val pageW: Int,
        val pageH: Int,
        val srcRect: Rect,
        val dstRect: Rect
    )

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
}
