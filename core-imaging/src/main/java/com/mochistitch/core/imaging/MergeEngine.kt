package com.mochistitch.core.imaging

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.roundToInt

class MergeEngine(
    private val openInputStream: (Uri) -> InputStream?
) {
    constructor(context: Context) : this({ uri -> context.contentResolver.openInputStream(uri) })

    companion object {
        private const val TAG = "MochiStitch.MergeEngine"
        private const val MAX_CANVAS_DIM = 8192
    }

    data class ImageSize(val uri: Uri, val width: Int, val height: Int)

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
            val sizes = imageUris.map { uri ->
                val (w, h) = getImageDimensions(uri)
                if (w <= 0 || h <= 0) {
                    return@withContext Result.failure(IllegalStateException("Failed to decode image dimensions for URI: $uri"))
                }
                logMochiStitch("Input Bitmap: ${w}x${h}")
                logDebug("Input load stage - URI: $uri, width: $w, height: $h")
                ImageSize(uri, w, h)
            }

            val maxInputWidth = sizes.maxOf { it.width }
            val maxInputHeight = sizes.maxOf { it.height }

            val items = calculateItemPlacements(sizes, maxInputWidth, maxInputHeight, config)

            var canvasWidth = if (config.direction == MergeDirection.VERTICAL) {
                maxInputWidth
            } else {
                items.maxOf { item -> item.dstRect.right }
            }

            var canvasHeight = if (config.direction == MergeDirection.VERTICAL) {
                items.maxOf { item -> item.dstRect.bottom }
            } else {
                maxInputHeight
            }

            // Log EXACTLY calculated max width before drawing
            logMochiStitch("Calculated max width: $maxInputWidth")
            logMochiStitch("Target Canvas: ${canvasWidth}x${canvasHeight}")

            var scaleFactor = 1.0f
            if (canvasWidth > MAX_CANVAS_DIM || canvasHeight > MAX_CANVAS_DIM) {
                val scaleW = MAX_CANVAS_DIM.toFloat() / canvasWidth.toFloat()
                val scaleH = MAX_CANVAS_DIM.toFloat() / canvasHeight.toFloat()
                scaleFactor = minOf(scaleW, scaleH)
                canvasWidth = (canvasWidth * scaleFactor).roundToInt().coerceAtLeast(1)
                canvasHeight = (canvasHeight * scaleFactor).roundToInt().coerceAtLeast(1)
            }

            logDebug("Dimension calculation stage - maxInputWidth: $maxInputWidth, maxInputHeight: $maxInputHeight, canvasWidth: $canvasWidth, canvasHeight: $canvasHeight, direction: ${config.direction}, alignment: ${config.alignmentMode}")

            val canvasBitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(canvasBitmap)
            canvas.drawColor(config.paddingColor.colorInt)

            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

            val totalCount = items.size
            for ((index, item) in items.withIndex()) {
                val inputStream = openInputStream(item.uri)
                    ?: return@withContext Result.failure(IllegalStateException("Could not open stream for URI: ${item.uri}"))

                val sampleSize = calculateInSampleSize(
                    item.srcRect.width(),
                    item.srcRect.height(),
                    item.dstRect.width(),
                    item.dstRect.height()
                )

                val options = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inSampleSize = sampleSize
                }
                val srcBitmap = BitmapFactory.decodeStream(inputStream, null, options)
                inputStream.close()

                if (srcBitmap == null) {
                    canvasBitmap.recycle()
                    return@withContext Result.failure(IllegalStateException("Could not decode bitmap for URI: ${item.uri}"))
                }

                val scaledSrcRect = Rect(
                    item.srcRect.left / sampleSize,
                    item.srcRect.top / sampleSize,
                    (item.srcRect.right / sampleSize).coerceAtMost(srcBitmap.width),
                    (item.srcRect.bottom / sampleSize).coerceAtMost(srcBitmap.height)
                )

                val dstRectF = if (scaleFactor != 1.0f) {
                    RectF(
                        item.dstRect.left * scaleFactor,
                        item.dstRect.top * scaleFactor,
                        item.dstRect.right * scaleFactor,
                        item.dstRect.bottom * scaleFactor
                    )
                } else {
                    RectF(item.dstRect)
                }

                logDebug("Canvas draw stage - item $index: URI: ${item.uri}, srcRect: $scaledSrcRect, dstRectF: $dstRectF")

                if (config.alignmentMode == AlignmentMode.PADDING && config.paddingColor != PaddingColor.TRANSPARENT) {
                    val pageBgPaint = Paint().apply {
                        color = config.paddingColor.colorInt
                        style = Paint.Style.FILL
                    }
                    val pageBoxF = if (scaleFactor != 1.0f) {
                        RectF(
                            item.pageBox.left * scaleFactor,
                            item.pageBox.top * scaleFactor,
                            item.pageBox.right * scaleFactor,
                            item.pageBox.bottom * scaleFactor
                        )
                    } else {
                        RectF(item.pageBox)
                    }
                    canvas.drawRect(pageBoxF, pageBgPaint)
                }

                canvas.drawBitmap(srcBitmap, scaledSrcRect, dstRectF, paint)
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

    private fun calculateInSampleSize(reqSrcW: Int, reqSrcH: Int, reqDstW: Int, reqDstH: Int): Int {
        var inSampleSize = 1
        if (reqSrcH > reqDstH * 2 || reqSrcW > reqDstW * 2) {
            val halfHeight: Int = reqSrcH / 2
            val halfWidth: Int = reqSrcW / 2
            while ((halfHeight / inSampleSize) >= reqDstH && (halfWidth / inSampleSize) >= reqDstW) {
                inSampleSize *= 2
            }
        }
        return max(1, inSampleSize)
    }

    suspend fun merge(
        imageUris: List<Uri>,
        outputStream: OutputStream,
        config: MergeConfig = MergeConfig(),
        onProgress: (Float) -> Unit = {}
    ): MergeResult = withContext(Dispatchers.IO) {
        mergeToBitmap(imageUris, config, onProgress).fold(
            onSuccess = { canvasBitmap ->
                try {
                    val byteCountingStream = ByteCountingOutputStream(outputStream)
                    canvasBitmap.compress(config.compressFormat, config.quality, byteCountingStream)
                    byteCountingStream.flush()
                    val bytesWritten = byteCountingStream.bytesWritten
                    val width = canvasBitmap.width
                    val height = canvasBitmap.height
                    canvasBitmap.recycle()

                    MergeResult.Success(
                        width = width,
                        height = height,
                        bytesWritten = bytesWritten
                    )
                } catch (e: Throwable) {
                    canvasBitmap.recycle()
                    MergeResult.Error(e.message ?: "Failed to compress bitmap", e)
                }
            },
            onFailure = { throwable ->
                MergeResult.Error(throwable.message ?: "Unknown error during merge", throwable)
            }
        )
    }

    private fun getImageDimensions(uri: Uri): Pair<Int, Int> {
        return try {
            val stream = openInputStream(uri) ?: return Pair(0, 0)
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(stream, null, options)
            stream.close()
            Pair(options.outWidth, options.outHeight)
        } catch (e: Exception) {
            Pair(0, 0)
        }
    }

    data class ItemPlacement(
        val uri: Uri,
        val srcRect: Rect,
        val dstRect: Rect,
        val pageBox: Rect
    )

    private fun calculateItemPlacements(
        sizes: List<ImageSize>,
        refWidth: Int,
        refHeight: Int,
        config: MergeConfig
    ): List<ItemPlacement> {
        val placements = mutableListOf<ItemPlacement>()

        when (config.direction) {
            MergeDirection.VERTICAL -> {
                var currentY = 0
                for (item in sizes) {
                    val (pageW, pageH, srcRect, dstRect) = computePlacementForSingleImage(
                        itemWidth = item.width,
                        itemHeight = item.height,
                        refWidth = refWidth,
                        refHeight = refHeight,
                        alignmentMode = config.alignmentMode,
                        isVertical = true
                    )
                    val pageBox = Rect(0, currentY, refWidth, currentY + pageH)
                    val adjustedDstRect = Rect(
                        dstRect.left,
                        currentY + dstRect.top,
                        dstRect.right,
                        currentY + dstRect.bottom
                    )
                    placements.add(ItemPlacement(item.uri, srcRect, adjustedDstRect, pageBox))
                    currentY += pageH
                }
            }

            MergeDirection.HORIZONTAL_LTR -> {
                var currentX = 0
                for (item in sizes) {
                    val (pageW, pageH, srcRect, dstRect) = computePlacementForSingleImage(
                        itemWidth = item.width,
                        itemHeight = item.height,
                        refWidth = refWidth,
                        refHeight = refHeight,
                        alignmentMode = config.alignmentMode,
                        isVertical = false
                    )
                    val pageBox = Rect(currentX, 0, currentX + pageW, refHeight)
                    val adjustedDstRect = Rect(
                        currentX + dstRect.left,
                        dstRect.top,
                        currentX + dstRect.right,
                        dstRect.bottom
                    )
                    placements.add(ItemPlacement(item.uri, srcRect, adjustedDstRect, pageBox))
                    currentX += pageW
                }
            }

            MergeDirection.HORIZONTAL_RTL -> {
                val computedSpecs = sizes.map { item ->
                    computePlacementForSingleImage(
                        itemWidth = item.width,
                        itemHeight = item.height,
                        refWidth = refWidth,
                        refHeight = refHeight,
                        alignmentMode = config.alignmentMode,
                        isVertical = false
                    )
                }
                val totalWidth = computedSpecs.sumOf { it.first }
                var currentX = totalWidth

                for ((index, item) in sizes.withIndex()) {
                    val (pageW, pageH, srcRect, dstRect) = computedSpecs[index]
                    val itemStartX = currentX - pageW
                    val pageBox = Rect(itemStartX, 0, itemStartX + pageW, refHeight)
                    val adjustedDstRect = Rect(
                        itemStartX + dstRect.left,
                        dstRect.top,
                        itemStartX + dstRect.right,
                        dstRect.bottom
                    )
                    placements.add(ItemPlacement(item.uri, srcRect, adjustedDstRect, pageBox))
                    currentX -= pageW
                }
            }
        }

        return placements
    }

    private fun computePlacementForSingleImage(
        itemWidth: Int,
        itemHeight: Int,
        refWidth: Int,
        refHeight: Int,
        alignmentMode: AlignmentMode,
        isVertical: Boolean
    ): Quad<Int, Int, Rect, Rect> {
        return when (alignmentMode) {
            AlignmentMode.RESIZE_PROPORTIONAL -> {
                if (isVertical) {
                    val dstW = refWidth
                    val dstH = (itemHeight.toFloat() * refWidth.toFloat() / itemWidth.toFloat()).roundToInt().coerceAtLeast(1)
                    Quad(dstW, dstH, Rect(0, 0, itemWidth, itemHeight), Rect(0, 0, dstW, dstH))
                } else {
                    val dstH = refHeight
                    val dstW = (itemWidth.toFloat() * refHeight.toFloat() / itemHeight.toFloat()).roundToInt().coerceAtLeast(1)
                    Quad(dstW, dstH, Rect(0, 0, itemWidth, itemHeight), Rect(0, 0, dstW, dstH))
                }
            }

            AlignmentMode.PADDING -> {
                val pageW = refWidth
                val pageH = refHeight
                val scaleW = refWidth.toFloat() / itemWidth.toFloat()
                val scaleH = refHeight.toFloat() / itemHeight.toFloat()
                val scale = minOf(scaleW, scaleH)

                val dstW = (itemWidth * scale).roundToInt().coerceAtLeast(1)
                val dstH = (itemHeight * scale).roundToInt().coerceAtLeast(1)

                val offsetX = (pageW - dstW) / 2
                val offsetY = (pageH - dstH) / 2

                Quad(
                    pageW,
                    pageH,
                    Rect(0, 0, itemWidth, itemHeight),
                    Rect(offsetX, offsetY, offsetX + dstW, offsetY + dstH)
                )
            }

            AlignmentMode.CENTER_CROP -> {
                val pageW = refWidth
                val pageH = refHeight

                val scaleW = refWidth.toFloat() / itemWidth.toFloat()
                val scaleH = refHeight.toFloat() / itemHeight.toFloat()
                val scale = maxOf(scaleW, scaleH)

                val requiredSrcW = (pageW / scale).roundToInt().coerceIn(1, itemWidth)
                val requiredSrcH = (pageH / scale).roundToInt().coerceIn(1, itemHeight)

                val srcX = (itemWidth - requiredSrcW) / 2
                val srcY = (itemHeight - requiredSrcH) / 2

                Quad(
                    pageW,
                    pageH,
                    Rect(srcX, srcY, srcX + requiredSrcW, srcY + requiredSrcH),
                    Rect(0, 0, pageW, pageH)
                )
            }
        }
    }

    private fun logMochiStitch(message: String) {
        try {
            android.util.Log.d("MochiStitch", message)
        } catch (t: Throwable) {
            println("[MochiStitch] $message")
        }
    }

    private fun logDebug(message: String) {
        try {
            android.util.Log.d(TAG, message)
        } catch (t: Throwable) {
            println("[$TAG] $message")
        }
    }

    private data class Quad<A, B, C, D>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D
    )

    private class ByteCountingOutputStream(private val delegate: OutputStream) : OutputStream() {
        var bytesWritten: Long = 0
            private set

        override fun write(b: Int) {
            delegate.write(b)
            bytesWritten++
        }

        override fun write(b: ByteArray) {
            delegate.write(b)
            bytesWritten += b.size
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            delegate.write(b, off, len)
            bytesWritten += len
        }

        override fun flush() {
            delegate.flush()
        }

        override fun close() {
            delegate.close()
        }
    }
}
