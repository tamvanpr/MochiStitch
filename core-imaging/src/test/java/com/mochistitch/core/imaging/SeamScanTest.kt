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
    fun testUniformDarkRowIsUnsafe() {
        // Regresi v5: baris hitam merata lolos sebagai "aman" karena tak ada
        // variasi horizontal — padahal itu tinta penuh (border/balok).
        val black = IntArray(64) { 0xFF000000.toInt() }
        assertFalse(SeamScan.rowIsSafe(black))
    }

    @Test
    fun testPaperAwareRejectsGrayWash() {
        val gray = IntArray(64) { 0xFF808080.toInt() }
        // Tanpa paper ref masih aman (konservatif), tapi jauh dari kertas
        // putih harus ditolak bila paper diketahui.
        assertTrue(SeamScan.rowIsSafe(gray))
        assertFalse(SeamScan.rowIsSafe(gray, 0, gray.size, paperLum = 255))
        assertTrue(SeamScan.rowIsSafe(whiteRow(64), 0, 64, paperLum = 255))
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
    fun testVerticalEdgeMarksBothSides() {
        // Border panel horizontal: dua baris persis beda 0 vs 255.
        val w = 32
        val white = whiteRow(w)
        val black = IntArray(w) { 0xFF000000.toInt() }
        val rows = listOf(white, white, black, black)
        val vert = SeamScan.rowsVertSafe(w, 4) { y, out -> rows[y].copyInto(out) }
        // Tepi antara baris 1 dan 2 harus menandai keduanya tidak aman.
        assertTrue(vert[0])
        assertFalse(vert[1])
        assertFalse(vert[2])
        assertTrue(vert[3])
    }

    @Test
    fun testFindBandsRespectsMinBand() {
        val safe = BooleanArray(30)
        for (i in 4 until 10) safe[i] = true
        for (i in 12 until 28) safe[i] = true
        val bands = SeamScan.findBands(safe, minBand = 16)
        assertEquals(1, bands.size)
        assertEquals(12 until 28, bands[0])
    }

    @Test
    fun testPlanCutsStaysInsideLimitWithMargin() {
        val safe = BooleanArray(350) { true }
        val plan = SeamScan.planCuts(safe, limit = 100)
        assertTrue(plan.tailSafe)
        assertTrue(plan.cuts.isNotEmpty())
        // Tiap chunk ≤ limit, tiap potongan di baris aman dan berjarak
        // dari awal/akhir gambar (margin tengah, bukan tepi pita).
        var y = 0
        for (c in plan.cuts) {
            assertTrue(safe[c])
            val chunk = c - y
            assertTrue("chunk $chunk > limit", chunk <= 100)
            assertTrue("chunk $chunk terlalu kecil", chunk >= 50)
            assertTrue("potongan menempel tepi gambar", c > SeamScan.BAND_MARGIN)
            y = c
        }
        assertTrue(350 - y <= 100)
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
        assertEquals(2, plan.cuts.size)
        // Potongan pertama di pita pertama (dengan margin), kedua di pita jauh.
        assertTrue(plan.cuts[0] in 4..60)
        assertTrue(plan.cuts[1] in 334..349)
    }

    @Test
    fun testPlanCutsNoOverflowFallsBack() {
        val safe = BooleanArray(350)
        for (i in 0 until 61) safe[i] = true
        for (i in 330 until 350) safe[i] = true
        val plan = SeamScan.planCuts(safe, limit = 100, minChunk = 50, overflow = 0)
        assertFalse(plan.tailSafe)
        assertEquals(1, plan.cuts.size)
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

    @Test
    fun testSeamContinuesMatchesAdjacentRows() {
        // v6: hanya baris seam yang dibandingkan. Garis vertikal kontinu
        // (kolom hitam sama di kedua sisi seam) = bersambung.
        val w = 32
        fun striped(blackAt: Int): IntArray {
            val rows = 8
            return IntArray(w * rows) { idx ->
                val x = idx % w
                if (x == blackAt) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            }
        }
        val bottom = striped(10)
        val topSame = striped(10)
        val topDiff = striped(20)
        assertTrue(SeamScan.seamContinues(bottom, w, 8, topSame, w, 8))
        assertFalse(SeamScan.seamContinues(bottom, w, 8, topDiff, w, 8))
    }

    @Test
    fun testSeamContinuesIgnoresInterior() {
        // Interior beda jauh tapi seam sama -> tetap bersambung (v5 gagal
        // di sini karena membandingkan interior-vs-interior).
        val w = 16
        val bottom = IntArray(w * 8) { 0xFFFFFFFF.toInt() }
        val top = IntArray(w * 8) { 0xFFFFFFFF.toInt() }
        // Coret interior atas patch bawah (jauh dari seam) jadi hitam.
        for (x in 0 until w) bottom[x] = 0xFF000000.toInt()
        assertTrue(SeamScan.seamContinues(bottom, w, 8, top, w, 8))
    }
}
