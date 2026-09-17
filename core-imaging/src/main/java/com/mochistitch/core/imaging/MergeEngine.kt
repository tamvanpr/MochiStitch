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
 * Mesin gabung vertikal: menumpuk halaman menjadi strip webtoon.
 * Tidak ada mode horizontal dalam bentuk apa pun.
 */
class MergeEngine(private val openInputStream: (Uri) -> InputStream?) {

    constructor(context: Context) : this({ uri -> context.contentResolver.openInputStream(uri) })

    data class ImageSize(val uri: Uri, val width: Int, val height: Int)

    data class ItemPlacement(val uri: Uri, val srcRect: Rect, val dstRect: Rect, val pageBox: Rect)

    fun getImageDimensions(uri: Uri): Pair<Int, Int> {
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

    /** Lebar referensi = lebar input terlebar; tinggi tiap halaman proporsional. */
    fun calculatePlacements(sizes: List<ImageSize>, refWidth: Int, config: MergeConfig): List<ItemPlacement> {
        val placements = mutableListOf<ItemPlacement>()
        var currentY = 0
        for (item in sizes) {
            val dstH = when (config.alignmentMode) {
                AlignmentMode.RESIZE_PROPORTIONAL ->
                    (item.height.toFloat() * refWidth / item.width).roundToInt().coerceAtLeast(1)
                AlignmentMode.PADDING, AlignmentMode.CENTER_CROP -> refWidth * item.height / max(1, item.width)
            }.coerceAtLeast(1)
            // PADDING/CENTER_CROP memakai box penuh refWidth x dstH yang sama;
            // perbedaan perilaku crop/padding ditangani saat render bila perlu.
            // v2 memakai RESIZE_PROPORTIONAL sebagai perilaku utama.
            val pageH = dstH
            val pageBox = Rect(0, currentY, refWidth, currentY + pageH)
            val dstRect = Rect(0, currentY, refWidth, currentY + dstH)
            placements.add(
                ItemPlacement(item.uri, Rect(0, 0, item.width, item.height), dstRect, pageBox)
            )
            currentY += pageH
        }
        return placements
    }

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
                    return@withContext Result.failure(
                        IllegalStateException("Failed to decode image dimensions for URI: $uri")
                    )
                }
                ImageSize(uri, w, h)
            }
            val refWidth = sizes.maxOf { it.width }
            val items = calculatePlacements(sizes, refWidth, config)
            val totalHeight = items.maxOfOrNull { it.pageBox.bottom } ?: 1

            val canvasBitmap = Bitmap.createBitmap(max(1, refWidth), max(1, totalHeight), Bitmap.Config.RGB_565)
            val canvas = Canvas(canvasBitmap)
            canvas.drawColor(config.paddingColor.colorInt)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

            items.forEachIndexed { index, item ->
                val stream = openInputStream(item.uri)
                    ?: return@withContext Result.failure(IllegalStateException("Could not open stream for URI: ${item.uri}"))
                val sampleSize = calculateInSampleSize(
                    item.srcRect.width(), item.srcRect.height(),
                    item.dstRect.width(), item.dstRect.height()
                )
                val options = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.RGB_565
                    inSampleSize = sampleSize
                }
                val src = BitmapFactory.decodeStream(stream, null, options)
                stream.close()
                if (src == null) {
                    canvasBitmap.recycle()
                    return@withContext Result.failure(IllegalStateException("Could not decode bitmap for URI: ${item.uri}"))
                }
                val scaledSrc = Rect(
                    item.srcRect.left / sampleSize,
                    item.srcRect.top / sampleSize,
                    min(item.srcRect.right / sampleSize, src.width),
                    min(item.srcRect.bottom / sampleSize, src.height)
                )
                canvas.drawBitmap(src, scaledSrc, item.dstRect, paint)
                src.recycle()
                onProgress(0.1f + 0.8f * ((index + 1).toFloat() / items.size.toFloat()))
            }
            onProgress(1.0f)
            Result.success(canvasBitmap)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    suspend fun merge(
        imageUris: List<Uri>,
        outputStream: OutputStream,
        config: MergeConfig = MergeConfig(),
        onProgress: (Float) -> Unit = {}
    ): MergeResult = withContext(Dispatchers.IO) {
        mergeToBitmap(imageUris, config, onProgress).fold(
            onSuccess = { bitmap ->
                try {
                    var written = 0L
                    val counting = object : OutputStream() {
                        override fun write(b: Int) {
                            outputStream.write(b)
                            written++
                        }

                        override fun write(b: ByteArray, off: Int, len: Int) {
                            outputStream.write(b, off, len)
                            written += len
                        }
                    }
                    bitmap.compress(config.compressFormat, config.quality, counting)
                    counting.flush()
                    val w = bitmap.width
                    val h = bitmap.height
                    bitmap.recycle()
                    MergeResult.Success(w, h, written)
                } catch (e: Throwable) {
                    bitmap.recycle()
                    MergeResult.Error(e.message ?: "Failed to compress bitmap", e)
                }
            },
            onFailure = { MergeResult.Error(it.message ?: "Unknown merge error", it) }
        )
    }

    private fun calculateInSampleSize(srcW: Int, srcH: Int, dstW: Int, dstH: Int): Int {
        var sampleSize = 1
        if (srcH > dstH * 2 || srcW > dstW * 2) {
            val halfH = srcH / 2
            val halfW = srcW / 2
            while ((halfH / sampleSize) >= dstH && (halfW / sampleSize) >= dstW) {
                sampleSize *= 2
            }
        }
        return max(1, sampleSize)
    }
}
