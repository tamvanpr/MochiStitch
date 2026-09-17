package com.mochistitch.core.common

import com.mochistitch.core.settings.OutputWrapperFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class CommonUtilsTest {
    @Test
    fun testGetVersionName() {
        assertEquals("2.0.0", CommonUtils.getVersionName())
    }

    @Test
    fun testProjectEffectiveWrapper() {
        val p = StitchProject(sourceName = "komik.zip")
        assertEquals(OutputWrapperFormat.ZIP, p.effectiveWrapper(OutputWrapperFormat.ZIP))
        assertEquals(
            OutputWrapperFormat.LOOSE_FILES,
            p.effectiveWrapper(OutputWrapperFormat.LOOSE_FILES)
        )
        val over = p.copy(wrapperOverride = OutputWrapperFormat.CBZ)
        assertEquals(OutputWrapperFormat.CBZ, over.effectiveWrapper(OutputWrapperFormat.ZIP))
    }
}
