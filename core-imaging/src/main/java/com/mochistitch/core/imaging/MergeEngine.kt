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
import kotlin.math.roundToInt

class MergeEngine(
    private val openInputStream: (Uri) -> InputStream?
) {
    constructor(context: Context) : this({ uri -> context.contentResolver.openInputStream(uri) })

    data class ImageSize(val uri: Uri, val width: Int, val height: Int)

    suspend fun merge(
        imageUris: List<Uri>,
        outputStream: OutputStream,
        config: MergeConfig = MergeConfig(),
        onProgress: (Float) -> Unit = {}
    ): MergeResult = withContext(Dispatchers.IO) {
        if (imageUris.isEmpty()) {
            return@withContext MergeResult.Error("No input images provided.")
        }

        try {
            onProgress(0.05f)
            val sizes = imageUris.map { uri ->
                val (w, h) = getImageDimensions(uri)
                if (w <= 0 || h <= 0) {
                    return@withContext MergeResult.Error("Failed to decode image dimensions for URI: $uri")
                }
                ImageSize(uri, w, h)
            }

            val refWidth = sizes.maxOf { it.width }
            val refHeight = sizes.maxOf { it.height }

            // Compute dimensions for each image and total canvas size
            val items = calculateItemPlacements(sizes, refWidth, refHeight, config)

            var canvasWidth = if (config.direction == MergeDirection.VERTICAL) {
                refWidth
            } else {
                items.maxOf { item -> item.dstRect.right }
            }

            var canvasHeight = if (config.direction == MergeDirection.VERTICAL) {
                items.maxOf { item -> item.dstRect.bottom }
            } else {
                refHeight
            }

            // Cap max canvas dimensions to prevent OOM on extreme sizes
            val maxCanvasDim = 8192
            var scaleFactor = 1.0f
            if (canvasWidth > maxCanvasDim || canvasHeight > maxCanvasDim) {
                val scaleW = maxCanvasDim.toFloat() / canvasWidth.toFloat()
                val scaleH = maxCanvasDim.toFloat() / canvasHeight.toFloat()
                scaleFactor = minOf(scaleW, scaleH)
                canvasWidth = (canvasWidth * scaleFactor).roundToInt().coerceAtLeast(1)
                canvasHeight = (canvasHeight * scaleFactor).roundToInt().coerceAtLeast(1)
            }

            val canvasBitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(canvasBitmap)
            canvas.drawColor(config.paddingColor.colorInt)

            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

            val totalCount = items.size
            for ((index, item) in items.withIndex()) {
                val inputStream = openInputStream(item.uri)
                    ?: return@withContext MergeResult.Error("Could not open stream for URI: ${item.uri}")

                val options = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val srcBitmap = BitmapFactory.decodeStream(inputStream, null, options)
                inputStream.close()

                if (srcBitmap == null) {
                    canvasBitmap.recycle()
                    return@withContext MergeResult.Error("Could not decode bitmap for URI: ${item.uri}")
                }

                // If canvas was downscaled, adjust destination rect
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

                canvas.drawBitmap(srcBitmap, item.srcRect, dstRectF, paint)
                srcBitmap.recycle()

                val progress = 0.1f + 0.8f * ((index + 1).toFloat() / totalCount.toFloat())
                onProgress(progress)
            }

            val byteCountingStream = ByteCountingOutputStream(outputStream)
            canvasBitmap.compress(config.compressFormat, config.quality, byteCountingStream)
            byteCountingStream.flush()
            val bytesWritten = byteCountingStream.bytesWritten
            canvasBitmap.recycle()

            onProgress(1.0f)
            MergeResult.Success(
                width = canvasWidth,
                height = canvasHeight,
                bytesWritten = bytesWritten
            )
        } catch (e: Throwable) {
            MergeResult.Error(e.message ?: "Unknown error occurred during merge", e)
        }
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
