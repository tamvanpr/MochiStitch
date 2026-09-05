package com.mochistitch.core.archive

import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveHandlerTest {
    @Test
    fun testExportCbz() {
        assertTrue(ArchiveHandler.exportCbz())
    }
}
