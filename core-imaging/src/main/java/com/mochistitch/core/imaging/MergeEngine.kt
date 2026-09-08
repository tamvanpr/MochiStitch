package com.mochistitch.core.imaging

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * MergeEngine — menggabungkan beberapa halaman komik menjadi satu canvas.
 *
 * Mendukung:
 * - Vertical (webtoon), Horizontal LTR, Horizontal RTL
 * - Alignment: RESIZE_PROPORTIONAL, CENTER_CROP, PADDING
 * - Output bitmap berukuran asli (tanpa scaling artifak MAX_CANVAS_DIM)
 */
class MergeEngine(
    private val openInputStream: (Uri) -> InputStream?
) {
    constructor(context: Context) : this({ uri -> context.contentResolver.openInputStream(uri) })

    data class ImageSize(val uri: Uri, val width: Int, val height: Int)

    /**
     * Menggabungkan daftar URI gambar menjadi satu Bitmap.
     * Bitmap hasil berukuran asli sesuai dimensi input — tidak ada scaling artifak.
     */
    suspend fun mergeToBitmap(
        imageUris: List<Uri>,
        config: MergeConfig = MergeConfig(),
        onProgress: (Float) -> Unit = {}
    ): Result<Bitmap> = withContext(Dispatchers.IO) {
        if (imageUris.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("No input images provided."))
        }

        try {
            onProgress(0.05f)

            // Step 1: Ambil dimensi setiap input
            val sizes = imageUris.map { uri ->
                val (w, h) = getImageDimensions(uri)
                if (w <= 0 || h <= 0) {
                    return@withContext Result.failure(
                        IllegalStateException("Failed to decode image dimensions for URI: $uri")
                    )
                }
                ImageSize(uri, w, h)
            }

            val maxInputWidth = sizes.maxOf { it.width }
            val maxInputHeight = sizes.maxOf { it.height }

            // Step 2: Hitung penempatan setiap item
            val items = calculateItemPlacements(sizes, maxInputWidth, maxInputHeight, config)

            // Step 3: Hitung dimensi canvas akhir
            val canvasWidth = if (config.direction == MergeDirection.VERTICAL) {
                maxInputWidth
            } else {
                // Horizontal: jumlahkan semua pageBox.right (posisi absolut di canvas)
                items.maxOfOrNull { it.pageBox.right } ?: maxInputWidth
            }

            val canvasHeight = if (config.direction == MergeDirection.VERTICAL) {
                items.maxOfOrNull { it.pageBox.bottom } ?: maxInputHeight
            } else {
                maxInputHeight
            }

            // Fallback: pastikan dimensi minimal1
            val finalWidth = max(1, canvasWidth)
            val finalHeight = max(1, canvasHeight)

            // Step 4: Buat canvas dan gambar semua item
            val canvasBitmap = Bitmap.createBitmap(finalWidth, finalHeight, Bitmap.Config.RGB_565)
            val canvas = Canvas(canvasBitmap)
            canvas.drawColor(config.paddingColor.colorInt)

            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

            val totalCount = items.size
            for ((index, item) in items.withIndex()) {
                val inputStream = openInputStream(item.uri)
                    ?: return@withContext Result.failure(
                        IllegalStateException("Could not open stream for URI: ${item.uri}")
                    )

                // Decode dengan sample size yang sesuai
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

                if (srcBitmap == null) {
                    canvasBitmap.recycle()
                    return@withContext Result.failure(
                        IllegalStateException("Could not decode bitmap for URI: ${item.uri}")
                    )
                }

                // Src rect disesuaikan dengan sample size
                val scaledSrcRect = Rect(
                    item.srcRect.left / sampleSize,
                    item.srcRect.top / sampleSize,
                    min(item.srcRect.right / sampleSize, srcBitmap.width),
                    min(item.srcRect.bottom / sampleSize, srcBitmap.height)
                )

                // Draw background padding if needed
                if (config.alignmentMode == AlignmentMode.PADDING && config.paddingColor != PaddingColor.TRANSPARENT) {
                    val pageBgPaint = Paint().apply {
                        color = config.paddingColor.colorInt
                        style = Paint.Style.FILL
                    }
                    canvas.drawRect(item.pageBox.left.toFloat(), item.pageBox.top.toFloat(), item.pageBox.right.toFloat(), item.pageBox.bottom.toFloat(), pageBgPaint)
                }

                canvas.drawBitmap(srcBitmap, scaledSrcRect, item.dstRect, paint)
                srcBitmap.recycle()

                val progress = 0.1f + 0.8f * ((index + 1).toFloat() / totalCount.toFloat())
                onProgress(progress)
            }

            onProgress(1.0f)
            Result.success(canvasBitmap)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    /**
     * Merge langsung ke OutputStream (JPEG/PNG/WebP).
     */
    suspend fun merge(
        imageUris: List<Uri>,
        outputStream: OutputStream,
        config: MergeConfig = MergeConfig(),
        onProgress: (Float) -> Unit = {}
    ): MergeResult = withContext(Dispatchers.IO) {
        mergeToBitmap(imageUris, config, onProgress).fold(
            onSuccess = { bitmap ->
                try {
                    val countingStream = ByteCountingOutputStream(outputStream)
                    bitmap.compress(config.compressFormat, config.quality, countingStream)
                    countingStream.flush()
                    val bytesWritten = countingStream.bytesWritten
                    bitmap.recycle()

                    MergeResult.Success(
                        width = bitmap.width,
                        height = bitmap.height,
                        bytesWritten = bytesWritten
                    )
                } catch (e: Throwable) {
                    bitmap.recycle()
                    MergeResult.Error(e.message ?: "Failed to compress bitmap", e)
                }
            },
            onFailure = { throwable ->
                MergeResult.Error(throwable.message ?: "Unknown merge error", throwable)
            }
        )
    }

    // ─── Private helpers ───────────────────────────────────────────────────────

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

    /**
     * Menghitung posisi penempatan untuk semua item berdasarkan arah dan alignment.
     */
    private fun calculateItemPlacements(
        sizes: List<ImageSize>,
        refWidth: Int,
        refHeight: Int,
        config: MergeConfig
    ): List<ItemPlacement> {
        return when (config.direction) {
            MergeDirection.VERTICAL -> calculateVerticalPlacements(sizes, refWidth, refHeight, config)
            MergeDirection.HORIZONTAL_LTR -> calculateHorizontalPlacements(sizes, refWidth, refHeight, config, leftToRight = true)
            MergeDirection.HORIZONTAL_RTL -> calculateHorizontalPlacements(sizes, refWidth, refHeight, config, leftToRight = false)
        }
    }

    private fun calculateVerticalPlacements(
        sizes: List<ImageSize>,
        refWidth: Int,
        refHeight: Int,
        config: MergeConfig
    ): List<ItemPlacement> {
        val placements = mutableListOf<ItemPlacement>()
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
            placements.add(ItemPlacement(item.uri, placement.srcRect, adjustedDst, pageBox))
            currentY += placement.pageH
        }

        return placements
    }

    private fun calculateHorizontalPlacements(
        sizes: List<ImageSize>,
        refWidth: Int,
        refHeight: Int,
        config: MergeConfig,
        leftToRight: Boolean
    ): List<ItemPlacement> {
        val placements = mutableListOf<ItemPlacement>()

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
                placements.add(ItemPlacement(item.uri, placement.srcRect, adjustedDst, pageBox))
                currentX += placement.pageW
            }
        } else {
            // RTL: hitung semua placement dulu, lalu susun dari kanan ke kiri
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
                placements.add(ItemPlacement(item.uri, placement.srcRect, adjustedDst, pageBox))
                currentX -= placement.pageW
            }
        }

        return placements
    }

    /**
     * Menghitung srcRect, dstRect, dan dimensi page untuk satu item.
     */
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
                    // Scale width ke refWidth, hitung height proporsional
                    val dstW = refWidth
                    val dstH = (itemHeight.toFloat() * refWidth / itemWidth).roundToInt().coerceAtLeast(1)
                    PlacementSpec(
                        pageW = refWidth, pageH = dstH,
                        srcRect = Rect(0, 0, itemWidth, itemHeight),
                        dstRect = Rect(0, 0, dstW, dstH)
                    )
                } else {
                    // Scale height ke refHeight, hitung width proporsional
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
                // Scale agar muat di refWidth x refHeight, posisi di tengah
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
                // Crop tengah, scale agar memenuhi refWidth x refHeight
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

    // ─── Data classes ──────────────────────────────────────────────────────────

    data class ItemPlacement(
        val uri: Uri,
        val srcRect: Rect,
        val dstRect: Rect,
        val pageBox: Rect
    )

    private data class PlacementSpec(
        val pageW: Int,
        val pageH: Int,
        val srcRect: Rect,
        val dstRect: Rect
    )

    private class ByteCountingOutputStream(private val delegate: OutputStream) : OutputStream() {
        var bytesWritten: Long = 0
            private set

        override fun write(b: Int) { delegate.write(b); bytesWritten++ }
        override fun write(b: ByteArray) { delegate.write(b); bytesWritten += b.size }
        override fun write(b: ByteArray, off: Int, len: Int) { delegate.write(b, off, len); bytesWritten += len }
        override fun flush() { delegate.flush() }
        override fun close() { delegate.close() }
    }
}
