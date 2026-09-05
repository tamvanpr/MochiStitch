package com.mochistitch.core.imaging

import com.mochistitch.core.settings.OutputFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class ImageCompressorAndSplitEngineTest {

    @Test
    fun testFileExtensionAndMimeType() {
        assertEquals("png", ImageCompressor.getFileExtension(OutputFormat.PNG))
        assertEquals("jpg", ImageCompressor.getFileExtension(OutputFormat.JPG))
        assertEquals("webp", ImageCompressor.getFileExtension(OutputFormat.WEBP))

        assertEquals("image/png", ImageCompressor.getMimeType(OutputFormat.PNG))
        assertEquals("image/jpeg", ImageCompressor.getMimeType(OutputFormat.JPG))
        assertEquals("image/webp", ImageCompressor.getMimeType(OutputFormat.WEBP))
    }

    @Test
    fun testFilenameFormatter() {
        val filename1 = FilenameFormatter.formatFilename(
            template = "{project}_ch{chapter}_{index}",
            project = "SoloLeveling",
            chapter = "01",
            index = 5,
            indexPaddingDigits = 3,
            format = OutputFormat.PNG
        )
        assertEquals("SoloLeveling_ch01_005.png", filename1)

        val filename2 = FilenameFormatter.formatFilename(
            template = "page_{index}",
            project = "",
            chapter = "",
            index = 12,
            indexPaddingDigits = 2,
            format = OutputFormat.JPG
        )
        assertEquals("page_12.jpg", filename2)
    }
}
