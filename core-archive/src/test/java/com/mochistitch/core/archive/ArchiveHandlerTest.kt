package com.mochistitch.core.archive

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class ArchiveHandlerTest {

    @Test
    fun testExportCbz() {
        assertTrue(ArchiveHandler.exportCbz())
    }

    @Test
    fun testDetectKind() {
        assertEquals(ArchiveHandler.ArchiveKind.ZIP, ArchiveHandler.detectKind("komik.zip"))
        assertEquals(ArchiveHandler.ArchiveKind.ZIP, ArchiveHandler.detectKind("komik.CBZ"))
        assertEquals(ArchiveHandler.ArchiveKind.RAR, ArchiveHandler.detectKind("komik.rar"))
        assertEquals(ArchiveHandler.ArchiveKind.RAR, ArchiveHandler.detectKind("komik.cbr"))
        assertEquals(ArchiveHandler.ArchiveKind.RAR, ArchiveHandler.detectKind("komik.7z"))
        assertEquals(null, ArchiveHandler.detectKind("halaman.png"))
        assertEquals(null, ArchiveHandler.detectKind("tanpa-ekstensi"))
    }

    @Test
    fun testIsSupportedArchive() {
        assertTrue(ArchiveHandler.isSupportedArchive("a.zip"))
        assertTrue(ArchiveHandler.isSupportedArchive("a.cbz"))
        assertTrue(ArchiveHandler.isSupportedArchive("a.rar"))
        assertTrue(ArchiveHandler.isSupportedArchive("a.cbr"))
    }

    @Test
    fun testStripKnownExtension() {
        assertEquals("komik_ch1", ArchiveHandler.stripKnownExtension("komik_ch1.zip"))
        assertEquals("komik_ch1", ArchiveHandler.stripKnownExtension("komik_ch1.cbz"))
        assertEquals("komik_ch1", ArchiveHandler.stripKnownExtension("komik_ch1.cbr"))
        assertEquals("halaman01", ArchiveHandler.stripKnownExtension("halaman01.png"))
        assertEquals("tanpa-ekstensi", ArchiveHandler.stripKnownExtension("tanpa-ekstensi"))
    }

    @Test
    fun testCompareNatural_ordersNumerically() {
        val names = listOf("hal_10.png", "hal_2.png", "hal_1.png")
        val sorted = names.sortedWith { a, b -> ArchiveHandler.compareNatural(a, b) }
        assertEquals(listOf("hal_1.png", "hal_2.png", "hal_10.png"), sorted)
    }

    @Test
    fun testExtractZipImages_roundTrip() {
        val img1 = byteArrayOf(1, 2, 3, 4)
        val img2 = byteArrayOf(5, 6, 7, 8)
        val entries = listOf(
            ArchiveEntry("pages/hal_02.png", img1),
            ArchiveEntry("hal_01.jpg", img2),
            ArchiveEntry("readme.txt", "abaikan".toByteArray())
        )
        val out = ByteArrayOutputStream()
        ArchiveHandler.createArchive(entries, out)

        val extracted = ArchiveHandler.extractImages(ByteArrayInputStream(out.toByteArray()), "komik.zip")

        assertEquals(2, extracted.size)
        // Urutan natural: hal_01 dulu walau di arsip belakangan.
        assertEquals("hal_01.jpg", extracted[0].name)
        assertArrayEquals(img2, extracted[0].bytes)
        assertEquals("hal_02.png", extracted[1].name)
        assertArrayEquals(img1, extracted[1].bytes)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testExtractImages_unsupportedFormat_throws() {
        ArchiveHandler.extractImages(ByteArrayInputStream(ByteArray(0)), "halaman.png")
    }

    @Test
    fun testCreateArchive() {
        val entry1Data = "Page 1 content".toByteArray()
        val entry2Data = "Page 2 content".toByteArray()

        val entries = listOf(
            ArchiveEntry("page_001.txt", entry1Data),
            ArchiveEntry("page_002.txt", entry2Data)
        )

        val outputStream = ByteArrayOutputStream()
        val bytesRead = ArchiveHandler.createArchive(entries, outputStream)

        assertTrue(bytesRead > 0)

        val zipInputStream = ZipInputStream(ByteArrayInputStream(outputStream.toByteArray()))

        val readEntries = mutableMapOf<String, ByteArray>()
        var zipEntry = zipInputStream.nextEntry
        while (zipEntry != null) {
            val content = zipInputStream.readBytes()
            readEntries[zipEntry.name] = content
            zipEntry = zipInputStream.nextEntry
        }

        assertEquals(2, readEntries.size)
        assertNotNull(readEntries["page_001.txt"])
        assertArrayEquals(entry1Data, readEntries["page_001.txt"])

        assertNotNull(readEntries["page_002.txt"])
        assertArrayEquals(entry2Data, readEntries["page_002.txt"])
    }
}
