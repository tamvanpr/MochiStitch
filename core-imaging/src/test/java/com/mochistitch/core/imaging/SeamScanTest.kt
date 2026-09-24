package com.mochistitch.core.imaging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeamScanTest {

    private fun whiteRow(width: Int) = IntArray(width) { 0xFFFFFFFF.toInt() }

    private fun inkRow(width: Int, inkAt: Int): IntArray =
        IntArray(width) { i -> if (i == inkAt) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }

    @Test
    fun testFlatRowIsSafe() {
        assertTrue(SeamScan.rowIsSafe(whiteRow(64)))
        assertTrue(SeamScan.rowIsSafe(IntArray(0)))
    }

    @Test
    fun testInkedRowIsUnsafe() {
        assertFalse(SeamScan.rowIsSafe(inkRow(64, inkAt = 30)))
    }

    @Test
    fun testSmoothGradientUnderTauIsSafe() {
        val row = IntArray(64) { i ->
            val v = 0xE0 - i
            (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        assertTrue(SeamScan.rowIsSafe(row))
    }

    @Test
    fun testOffsetSlice() {
        val buf = whiteRow(128)
        buf[100] = 0xFF000000.toInt()
        assertTrue(SeamScan.rowIsSafe(buf, 0, 64))
        assertFalse(SeamScan.rowIsSafe(buf, 64, 64))
    }

    @Test
    fun testFindBandsRespectsMinBand() {
        val safe = BooleanArray(30)
        for (i in 4 until 10) safe[i] = true
        for (i in 12 until 24) safe[i] = true
        val bands = SeamScan.findBands(safe, minBand = 8)
        assertEquals(1, bands.size)
        assertEquals(12 until 24, bands[0])
    }

    @Test
    fun testPlanCutsHitsNearLimit() {
        val safe = BooleanArray(350) { true }
        val plan = SeamScan.planCuts(safe, limit = 100)
        assertTrue(plan.tailSafe)
        assertEquals(listOf(100, 200, 300), plan.cuts)
        assertEquals(50, 350 - plan.cuts.last())
        assertTrue(plan.cuts.all { safe[it] })
    }

    @Test
    fun testPlanCutsNoSafeRowsFallsBack() {
        val safe = BooleanArray(500) { false }
        val plan = SeamScan.planCuts(safe, limit = 100)
        assertFalse(plan.tailSafe)
        assertTrue(plan.cuts.isEmpty())
    }

    @Test
    fun testPlanCutsShortPageNeedsNothing() {
        val safe = BooleanArray(80) { true }
        val plan = SeamScan.planCuts(safe, limit = 100)
        assertTrue(plan.tailSafe)
        assertTrue(plan.cuts.isEmpty())
    }

    @Test
    fun testPlanCutsOverflowReachesNextBand() {
        val safe = BooleanArray(350)
        for (i in 0 until 61) safe[i] = true
        for (i in 330 until 350) safe[i] = true
        val plan = SeamScan.planCuts(safe, limit = 100, minChunk = 50, overflow = 250)
        assertTrue(plan.tailSafe)
        assertEquals(listOf(60, 330), plan.cuts)
    }

    @Test
    fun testPlanCutsNoOverflowFallsBack() {
        val safe = BooleanArray(350)
        for (i in 0 until 61) safe[i] = true
        for (i in 330 until 350) safe[i] = true
        val plan = SeamScan.planCuts(safe, limit = 100, minChunk = 50, overflow = 0)
        assertFalse(plan.tailSafe)
        assertEquals(listOf(60), plan.cuts)
    }

    @Test
    fun testHasContentFlatVsInk() {
        assertFalse(SeamScan.hasContent(whiteRow(256)))
        val noisy = IntArray(256) { i ->
            val v = 0xFF - (i % 3)
            (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        assertFalse(SeamScan.hasContent(noisy))
        val withInk = whiteRow(256)
        withInk[200] = 0xFF000000.toInt()
        assertTrue(SeamScan.hasContent(withInk))
        assertFalse(SeamScan.hasContent(IntArray(0)))
    }

    @Test
    fun testRowsContinueIdentical() {
        val a = whiteRow(64)
        val b = whiteRow(64)
        assertTrue(SeamScan.rowsContinue(a, b))
    }

    @Test
    fun testRowsContinueOpposite() {
        val a = whiteRow(64)
        val b = IntArray(64) { 0xFF000000.toInt() }
        assertFalse(SeamScan.rowsContinue(a, b))
    }

    @Test
    fun testRowsContinueToleratesNoise() {
        val a = whiteRow(128)
        val b = IntArray(128) { i ->
            val v = if (i % 16 == 0) 0xF0 else 0xFF
            (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        assertTrue(SeamScan.rowsContinue(a, b))
    }

    @Test
    fun testRowsContinueEmpty() {
        assertFalse(SeamScan.rowsContinue(IntArray(0), whiteRow(8)))
    }
}
