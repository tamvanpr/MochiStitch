package com.mochistitch.app

import org.junit.Assert.assertEquals
import org.junit.Test

class DeduplicationTest {

    @Test
    fun testDeduplicateUris() {
        val existingUris = setOf("file://a.jpg", "file://b.jpg")
        val incomingUris = listOf("file://b.jpg", "file://c.jpg", "file://c.jpg")

        val distinctIncoming = incomingUris.distinct()
        val newUris = distinctIncoming.filterNot { existingUris.contains(it) }
        val duplicateCount = incomingUris.size - newUris.size

        assertEquals(1, newUris.size)
        assertEquals("file://c.jpg", newUris[0])
        assertEquals(2, duplicateCount)
    }

    @Test
    fun testAllDuplicatesIgnored() {
        val existingUris = setOf("file://a.jpg", "file://b.jpg")
        val incomingUris = listOf("file://a.jpg", "file://b.jpg")

        val distinctIncoming = incomingUris.distinct()
        val newUris = distinctIncoming.filterNot { existingUris.contains(it) }
        val duplicateCount = incomingUris.size - newUris.size

        assertEquals(0, newUris.size)
        assertEquals(2, duplicateCount)
    }
}
