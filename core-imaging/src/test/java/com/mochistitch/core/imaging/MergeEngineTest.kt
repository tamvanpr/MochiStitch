package com.mochistitch.core.imaging

import android.net.Uri
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class MergeEngineTest {

    @Test
    fun testMergeEmptyListReturnsError() = runBlocking {
        val engine = MergeEngine { null }
        val outputStream = ByteArrayOutputStream()
        val result = engine.merge(
            imageUris = emptyList(),
            outputStream = outputStream
        )

        assertTrue(result is MergeResult.Error)
        val error = result as MergeResult.Error
        assertEquals("No input images provided.", error.message)
    }

    @Test
    fun testMergeConfigDefaults() {
        val config = MergeConfig()
        assertEquals(MergeDirection.VERTICAL, config.direction)
        assertEquals(AlignmentMode.RESIZE_PROPORTIONAL, config.alignmentMode)
        assertEquals(PaddingColor.WHITE, config.paddingColor)
    }

    @Test
    fun testMergeInvalidStreamReturnsError() = runBlocking {
        val engine = MergeEngine { null }
        val outputStream = ByteArrayOutputStream()
        val dummyUri = Uri.parse("content://dummy/1")

        val result = engine.merge(
            imageUris = listOf(dummyUri),
            outputStream = outputStream
        )

        assertTrue(result is MergeResult.Error)
        val error = result as MergeResult.Error
        assertTrue(error.message.contains("Failed to decode image dimensions"))
    }

    @Test
    fun testProportionalScalingCalculation() {
        // Given item 1: 100x200, item 2: 200x300
        // refWidth = 200 (max width)
        // Item 1 scaled to refWidth 200: height becomes 200 * (200 / 100) = 400
        // Item 2 scaled to refWidth 200: height stays 300
        // Expected total height for vertical merge = 400 + 300 = 700
        val w1 = 100
        val h1 = 200
        val w2 = 200
        val h2 = 300

        val refWidth = maxOf(w1, w2)
        val refHeight = maxOf(h1, h2)

        assertEquals(200, refWidth)
        assertEquals(300, refHeight)

        val scaledHeight1 = (h1.toFloat() * refWidth.toFloat() / w1.toFloat()).toInt()
        val scaledHeight2 = (h2.toFloat() * refWidth.toFloat() / w2.toFloat()).toInt()

        assertEquals(400, scaledHeight1)
        assertEquals(300, scaledHeight2)
        assertEquals(700, scaledHeight1 + scaledHeight2)
    }
}
