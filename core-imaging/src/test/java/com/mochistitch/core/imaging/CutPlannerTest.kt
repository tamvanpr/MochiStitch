package com.mochistitch.core.imaging

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CutPlannerTest {
    @Test
    fun textBandIsNeverCut() {
        val busy = BooleanArray(200)
        for (y in 80..120) busy[y] = true
        val bands = CutPlanner.safeBands(busy, margin = 10, minBand = 5)
        assertTrue(bands.none { it.center in 70..130 })
    }

    @Test
    fun planSkipsBusyArea() {
        val busy = BooleanArray(400)
        val ink = IntArray(400)
        for (y in 95..115) {
            busy[y] = true
            ink[y] = 50
        }
        val cuts = CutPlanner.plan(RowProfile(busy, ink), maxLen = 100, minLen = 40, margin = 10)
        assertTrue(cuts.isNotEmpty())
        assertTrue(cuts.none { it.y in 80..130 })
        assertTrue(cuts.none { it.forced })
    }

    @Test
    fun noSafeBandIsMarkedForced() {
        val busy = BooleanArray(300) { true }
        val cuts = CutPlanner.plan(RowProfile(busy, IntArray(300)), maxLen = 100, minLen = 40, margin = 4)
        assertTrue(cuts.isNotEmpty())
        assertTrue(cuts.all { it.forced })
    }

    @Test
    fun cutPositionsAdvance() {
        val busy = BooleanArray(600)
        val cuts = CutPlanner.plan(RowProfile(busy, IntArray(600)), maxLen = 100, minLen = 40, margin = 4)
        var previous = 0
        cuts.forEach {
            assertTrue(it.y > previous)
            previous = it.y
        }
        assertFalse(cuts.isEmpty())
        assertTrue(600 - previous <= 100)
    }
}
