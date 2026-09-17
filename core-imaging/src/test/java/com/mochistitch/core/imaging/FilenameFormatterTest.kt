package com.mochistitch.core.imaging

import com.mochistitch.core.settings.OutputWrapperFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class FilenameFormatterTest {

    @Test
    fun testArchiveName_zipToZip_staysSame() {
        assertEquals(
            "komik_ch1.zip",
            FilenameFormatter.resolveArchiveOutputName("komik_ch1.zip", OutputWrapperFormat.ZIP)
        )
    }

    @Test
    fun testArchiveName_zipToCbz_sameBasename() {
        assertEquals(
            "komik_ch1.cbz",
            FilenameFormatter.resolveArchiveOutputName("komik_ch1.zip", OutputWrapperFormat.CBZ)
        )
    }

    @Test
    fun testArchiveName_cbzToZip_sameBasename() {
        assertEquals(
            "komik_ch1.zip",
            FilenameFormatter.resolveArchiveOutputName("komik_ch1.cbz", OutputWrapperFormat.ZIP)
        )
    }

    @Test
    fun testArchiveName_rarToCbz_sameBasename() {
        assertEquals(
            "komik_ch1.cbz",
            FilenameFormatter.resolveArchiveOutputName("komik_ch1.rar", OutputWrapperFormat.CBZ)
        )
    }

    @Test
    fun testArchiveName_imageSource_stripsImageExt() {
        assertEquals(
            "halaman01.zip",
            FilenameFormatter.resolveArchiveOutputName("halaman01.png", OutputWrapperFormat.ZIP)
        )
    }

    @Test
    fun testArchiveName_noExtension_appendsExt() {
        assertEquals(
            "proyek.cbz",
            FilenameFormatter.resolveArchiveOutputName("proyek", OutputWrapperFormat.CBZ)
        )
    }

    @Test
    fun testArchiveName_withTimestamp() {
        assertEquals(
            "komik_ch1_20240101_120000.zip",
            FilenameFormatter.resolveArchiveOutputName(
                "komik_ch1.zip", OutputWrapperFormat.ZIP, "20240101_120000"
            )
        )
    }

    @Test
    fun testArchiveName_blankSource_fallsBack() {
        assertEquals(
            "MochiStitch.zip",
            FilenameFormatter.resolveArchiveOutputName("", OutputWrapperFormat.ZIP)
        )
    }
}
