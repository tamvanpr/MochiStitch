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
}
