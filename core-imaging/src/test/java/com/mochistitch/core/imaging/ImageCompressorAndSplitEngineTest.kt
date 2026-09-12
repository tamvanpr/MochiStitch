package com.mochistitch.core.imaging

import com.mochistitch.core.settings.DetectionSensitivity
import com.mochistitch.core.settings.MochiStitchSettings
import com.mochistitch.core.settings.OutputFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        assertEquals("SoloLeveling_ch01_005", filename1)

        val filename2 = FilenameFormatter.formatFilename(
            template = "page_{index}",
            project = "",
            chapter = "",
            index = 12,
            indexPaddingDigits = 2,
            format = OutputFormat.JPG
        )
        assertEquals("page_12", filename2)
    }

    @Test
    fun testMochiSmartSettingsDefaults() {
        val settings = MochiStitchSettings()
        assertEquals(true, settings.mochiSmartEnabled)
        assertEquals(150, settings.mochiSmartTolerance)
        assertEquals(DetectionSensitivity.MEDIUM, settings.mochiSmartSensitivity)
        assertEquals(true, settings.showManualReviewMarkers)
        assertTrue(settings.autoGutterDetectionEnabled)
        assertEquals(0.5f, settings.pixelComparisonSensitivity, 0.001f)
        assertEquals(0, settings.pixelComparisonMargins)
        assertEquals(5, settings.pixelComparisonStep)
        assertEquals(0.2f, settings.pixelComparisonMaxDeviationFactor, 0.001f)
    }
}
