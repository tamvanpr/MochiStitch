package com.mochistitch.core.archive

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class ArchiveHandlerTest {

    @Test
    fun testDetectKind() {
        assertEquals(ArchiveHandler.ArchiveKind.ZIP, ArchiveHandler.detectKind("k.zip"))
        assertEquals(ArchiveHandler.ArchiveKind.ZIP, ArchiveHandler.detectKind("k.CBZ"))
        assertEquals(ArchiveHandler.ArchiveKind.RAR, ArchiveHandler.detectKind("k.rar"))
        assertEquals(ArchiveHandler.ArchiveKind.RAR, ArchiveHandler.detectKind("k.cbr"))
        assertEquals(ArchiveHandler.ArchiveKind.RAR, ArchiveHandler.detectKind("k.7z"))
        assertNull(ArchiveHandler.detectKind("k.png"))
    }

    @Test
    fun testStripKnownExtension() {
        assertEquals("komik", ArchiveHandler.stripKnownExtension("komik.zip"))
        assertEquals("komik", ArchiveHandler.stripKnownExtension("komik.cbz"))
        assertEquals("komik", ArchiveHandler.stripKnownExtension("komik.cbr"))
        assertEquals("hal", ArchiveHandler.stripKnownExtension("hal.png"))
        assertEquals("tanpa", ArchiveHandler.stripKnownExtension("tanpa"))
    }

    @Test
    fun testCompareNatural() {
        val sorted = listOf("hal_10.png", "hal_2.png", "hal_1.png")
            .sortedWith { a, b -> ArchiveHandler.compareNatural(a, b) }
        assertEquals(listOf("hal_1.png", "hal_2.png", "hal_10.png"), sorted)
    }

    @Test
    fun testZipRoundTrip() {
        val img1 = byteArrayOf(1, 2, 3)
        val img2 = byteArrayOf(4, 5, 6)
        val out = ByteArrayOutputStream()
        val total = ArchiveHandler.createArchive(
            listOf(
                ArchiveEntry("p02.png", img1),
                ArchiveEntry("sub/p01.jpg", img2),
                ArchiveEntry("catatan.txt", "abaikan".toByteArray())
            ),
            out
        )
        assertTrue(total > 0)
        val extracted = ArchiveHandler.extractImages(ByteArrayInputStream(out.toByteArray()), "k.zip")
        assertEquals(2, extracted.size)
        assertEquals("p01.jpg", extracted[0].name)
        assertArrayEquals(img2, extracted[0].bytes)
        assertEquals("p02.png", extracted[1].name)
        assertArrayEquals(img1, extracted[1].bytes)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testUnsupportedThrows() {
        ArchiveHandler.extractImages(ByteArrayInputStream(ByteArray(0)), "k.png")
    }
}
