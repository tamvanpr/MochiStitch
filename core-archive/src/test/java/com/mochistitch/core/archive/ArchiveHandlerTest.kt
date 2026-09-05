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
