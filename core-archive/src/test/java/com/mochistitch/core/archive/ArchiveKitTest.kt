package com.mochistitch.core.archive

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

class ArchiveKitTest {

    @Test
    fun testKinds() {
        assertEquals(ArchiveKit.Kind.ZIP, ArchiveKit.kindOf("a.zip"))
        assertEquals(ArchiveKit.Kind.ZIP, ArchiveKit.kindOf("a.CBZ"))
        assertEquals(ArchiveKit.Kind.RAR, ArchiveKit.kindOf("a.rar"))
        assertEquals(ArchiveKit.Kind.RAR, ArchiveKit.kindOf("a.cbr"))
        assertEquals(ArchiveKit.Kind.SEVEN, ArchiveKit.kindOf("a.7z"))
        assertEquals(ArchiveKit.Kind.SEVEN, ArchiveKit.kindOf("a.cb7"))
        assertNull(ArchiveKit.kindOf("a.png"))
        assertTrue(ArchiveKit.canOpen("k.7z"))
    }

    @Test
    fun testBaseNames() {
        assertEquals("komik", ArchiveKit.baseNameOf("komik.zip"))
        assertEquals("komik", ArchiveKit.baseNameOf("dir/komik.cbr"))
        assertEquals("komik", ArchiveKit.baseNameOf("komik.7z"))
        assertEquals("hal", ArchiveKit.baseNameOf("hal.jpg"))
    }

    @Test
    fun testSanitizeName() {
        assertEquals("a_b_c.png", ArchiveKit.sanitizeName("a/b:c.png"))
        assertEquals("halaman", ArchiveKit.sanitizeName("   .  "))
    }

    @Test
    fun testNaturalSorting() {
        val sorted = listOf("p10.png", "p2.png", "p1.png")
            .sortedWith { a, b -> ArchiveKit.naturalOrder(a, b) }
        assertEquals(listOf("p1.png", "p2.png", "p10.png"), sorted)
    }

    @Test
    fun testZipRoundTrip() {
        val a = byteArrayOf(9, 8, 7)
        val b = byteArrayOf(1, 2, 3)
        val buf = ByteArrayOutputStream()
        assertTrue(
            ArchiveKit.pack(
                listOf(
                    ArchiveItem("b02.png", a),
                    ArchiveItem("folder/b01.jpg", b),
                    ArchiveItem("skip.txt", "x".toByteArray())
                ),
                buf
            ) > 0
        )
        val pages = ArchiveKit.unpack(ByteArrayInputStream(buf.toByteArray()), "k.cbz")
        assertEquals(2, pages.size)
        assertEquals("b01.jpg", pages[0].name)
        assertArrayEquals(b, pages[0].bytes)
        assertEquals("b02.png", pages[1].name)
        assertArrayEquals(a, pages[1].bytes)
    }

    @Test
    fun testSevenRoundTrip() {
        val tmp = File.createTempFile("mochi_test", ".7z")
        tmp.deleteOnExit()
        try {
            SevenZOutputFile(tmp).use { out ->
                val second = SevenZArchiveEntry().apply { name = "p2.png" }
                out.putArchiveEntry(second)
                out.write(byteArrayOf(9, 9, 9))
                out.closeArchiveEntry()
                val first = SevenZArchiveEntry().apply { name = "p1.png" }
                out.putArchiveEntry(first)
                out.write(byteArrayOf(1, 1, 1))
                out.closeArchiveEntry()
            }
            val pages = tmp.inputStream().use { ArchiveKit.unpack(it, "k.7z") }
            assertEquals(2, pages.size)
            assertEquals("p1.png", pages[0].name)
            assertArrayEquals(byteArrayOf(1, 1, 1), pages[0].bytes)
            assertEquals("p2.png", pages[1].name)
            assertArrayEquals(byteArrayOf(9, 9, 9), pages[1].bytes)
        } finally {
            tmp.delete()
        }
    }

    @Test
    fun testUnpackToDisk() {
        val buf = ByteArrayOutputStream()
        ArchiveKit.pack(
            listOf(
                ArchiveItem("b02.png", byteArrayOf(2, 2)),
                ArchiveItem("b01.png", byteArrayOf(1, 1))
            ),
            buf
        )
        val dest = File.createTempFile("mochi_unp", "")
        dest.delete()
        dest.mkdirs()
        try {
            val files = ArchiveKit.unpackTo(ByteArrayInputStream(buf.toByteArray()), "k.zip", dest)
            assertEquals(2, files.size)
            assertEquals("b01.png", files[0].name)
            assertEquals("b02.png", files[1].name)
            assertTrue(files[0].file.exists())
            assertTrue(files[1].file.exists())
            assertTrue(files[0].file.name.startsWith("001_"))
            assertTrue(files[1].file.name.startsWith("002_"))
            assertArrayEquals(byteArrayOf(1, 1), files[0].file.readBytes())
            assertArrayEquals(byteArrayOf(2, 2), files[1].file.readBytes())
        } finally {
            dest.deleteRecursively()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun testRejectsUnknown() {
        ArchiveKit.unpack(ByteArrayInputStream(ByteArray(0)), "k.jpg")
    }
}
