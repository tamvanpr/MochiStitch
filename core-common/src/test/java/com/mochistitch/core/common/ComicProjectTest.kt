package com.mochistitch.core.common

import com.mochistitch.core.settings.PackFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class ComicProjectTest {
    @Test
    fun testPackResolution() {
        val p = ComicProject(origin = "k.zip")
        assertEquals(PackFormat.ZIP, p.packFor(PackFormat.ZIP))
        assertEquals(PackFormat.FILES, p.packFor(PackFormat.FILES))
        assertEquals(PackFormat.CBZ, p.copy(packOverride = PackFormat.CBZ).packFor(PackFormat.ZIP))
    }
}
