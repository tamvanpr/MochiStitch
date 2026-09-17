package com.mochistitch.core.ui

import com.mochistitch.core.settings.OutputWrapperFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class StitchProjectTest {

    @Test
    fun testEffectiveWrapper_fallsBackToGlobal() {
        val project = StitchProject(sourceName = "komik.zip")
        assertEquals(
            OutputWrapperFormat.ZIP,
            project.effectiveWrapper(OutputWrapperFormat.ZIP)
        )
        assertEquals(
            OutputWrapperFormat.LOOSE_FILES,
            project.effectiveWrapper(OutputWrapperFormat.LOOSE_FILES)
        )
    }

    @Test
    fun testEffectiveWrapper_overrideWins() {
        val project = StitchProject(
            sourceName = "komik.zip",
            wrapperOverride = OutputWrapperFormat.CBZ
        )
        assertEquals(
            OutputWrapperFormat.CBZ,
            project.effectiveWrapper(OutputWrapperFormat.ZIP)
        )
        assertEquals(
            OutputWrapperFormat.CBZ,
            project.effectiveWrapper(OutputWrapperFormat.LOOSE_FILES)
        )
    }
}
