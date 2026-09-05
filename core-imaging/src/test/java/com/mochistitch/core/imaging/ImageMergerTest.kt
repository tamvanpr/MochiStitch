package com.mochistitch.core.imaging

import org.junit.Assert.assertTrue
import org.junit.Test

class ImageMergerTest {
    @Test
    fun testMergeImages() {
        assertTrue(ImageMerger.mergeImages())
    }
}
