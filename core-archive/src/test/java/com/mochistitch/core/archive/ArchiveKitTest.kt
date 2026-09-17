package com.mochistitch.core.archive

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class ArchiveKitTest {

    @Test
    fun testKinds() {
        assertEquals(ArchiveKit.Kind.ZIP, ArchiveKit.kindOf("a.zip"))
        assertEquals(ArchiveKit.Kind.ZIP, ArchiveKit.kindOf("a.CBZ"))
        assertEquals(ArchiveKit.Kind.RAR, ArchiveKit.kindOf("a.rar"))
        assertEquals(ArchiveKit.Kind.RAR, ArchiveKit.kindOf("a.cbr"))
        assertNull(ArchiveKit.kindOf("a.png"))
    }

    @Test
    fun testBaseNames() {
        assertEquals("komik", ArchiveKit.baseNameOf("komik.zip"))
        assertEquals("komik", ArchiveKit.baseNameOf("dir/komik.cbr"))
        assertEquals("hal", ArchiveKit.baseNameOf("hal.jpg"))
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

    @Test(expected = IllegalArgumentException::class)
    fun testRejectsUnknown() {
        ArchiveKit.unpack(ByteArrayInputStream(ByteArray(0)), "k.jpg")
    }
}
