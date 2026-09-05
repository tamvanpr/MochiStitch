package com.mochistitch.core.imaging

import android.graphics.Bitmap
import com.mochistitch.core.settings.ReadingDirection
import com.mochistitch.core.settings.SplitMode

object SplitEngine {

    /**
     * Slices a bitmap along its primary axis if splitMode is MAX_PIXELS.
     * For VERTICAL reading direction, primary axis is Y (height).
     * For LTR or RTL reading direction, primary axis is X (width).
     */
    fun sliceBitmap(
        source: Bitmap,
        splitMode: SplitMode,
        maxPixelLength: Int,
        direction: ReadingDirection
    ): List<Bitmap> {
        if (splitMode != SplitMode.MAX_PIXELS || maxPixelLength <= 0) {
            return listOf(source)
        }

        val slices = mutableListOf<Bitmap>()
        if (direction == ReadingDirection.VERTICAL) {
            val totalHeight = source.height
            if (totalHeight <= maxPixelLength) {
                slices.add(source)
            } else {
                var currentY = 0
                while (currentY < totalHeight) {
                    val sliceHeight = minOf(maxPixelLength, totalHeight - currentY)
                    val slice = Bitmap.createBitmap(source, 0, currentY, source.width, sliceHeight)
                    slices.add(slice)
                    currentY += sliceHeight
                }
            }
        } else {
            val totalWidth = source.width
            if (totalWidth <= maxPixelLength) {
                slices.add(source)
            } else {
                var currentX = 0
                while (currentX < totalWidth) {
                    val sliceWidth = minOf(maxPixelLength, totalWidth - currentX)
                    val slice = Bitmap.createBitmap(source, currentX, 0, sliceWidth, source.height)
                    slices.add(slice)
                    currentX += sliceWidth
                }
            }
        }

        return slices
    }
}
