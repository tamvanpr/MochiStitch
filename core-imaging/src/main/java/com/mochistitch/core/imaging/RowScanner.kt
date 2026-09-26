package com.mochistitch.core.imaging

import android.graphics.Bitmap
import kotlin.math.abs

data class RowProfile(
    val busy: BooleanArray,
    val ink: IntArray,
    val structured: BooleanArray = BooleanArray(busy.size),
    val maxRun: IntArray = IntArray(busy.size)
)

object RowScanner {
    fun scan(
        bmp: Bitmap,
        edgeThreshold: Int = 24,
        rangeThreshold: Int = 40,
        ignoreBorder: Float = 0.02f,
        noisePixels: Int = 1
    ): RowProfile {
        val w = bmp.width
        val h = bmp.height
        val row = IntArray(w)
        val lumBuf = IntArray(w)
        val hist = IntArray(256)
        val busy = BooleanArray(h)
        val ink = IntArray(h)
        val structured = BooleanArray(h)
        val maxRun = IntArray(h)
        for (y in 0 until h) {
            bmp.getPixels(row, 0, w, 0, y, w, 1)
            val r = scanRow(row, w, edgeThreshold, rangeThreshold, ignoreBorder, noisePixels, 0, lumBuf, hist)
            busy[y] = r.busy
            ink[y] = r.ink
            structured[y] = r.structured
            maxRun[y] = r.maxRun
        }
        return RowProfile(busy, ink, structured, maxRun)
    }

    fun scanBuffer(
        px: IntArray,
        w: Int,
        h: Int,
        edgeThreshold: Int = 24,
        rangeThreshold: Int = 40,
        ignoreBorder: Float = 0.02f,
        noisePixels: Int = 1
    ): RowProfile {
        require(px.size >= w * h)
        val lumBuf = IntArray(w)
        val hist = IntArray(256)
        val busy = BooleanArray(h)
        val ink = IntArray(h)
        val structured = BooleanArray(h)
        val maxRun = IntArray(h)
        for (y in 0 until h) {
            val r = scanRow(px, w, edgeThreshold, rangeThreshold, ignoreBorder, noisePixels, y * w, lumBuf, hist)
            busy[y] = r.busy
            ink[y] = r.ink
            structured[y] = r.structured
            maxRun[y] = r.maxRun
        }
        return RowProfile(busy, ink, structured, maxRun)
    }

    /**
     * Tiga lapis cek per baris: selisih antar-piksel bertetangga (garis tepi),
     * rentang min-max (glow/gradasi), dan porsi piksel yang menyimpang dari
     * median baris (kalimat pudar/tipis: tiap piksel bedanya kecil, tapi ada
     * puluhan piksel yang beda dari latar).
     *
     * Plus sinyal "terstruktur" (banyak run gelap pendek = goresan teks,
     * bukan titik screentone) dan run gelap terpanjang per baris.
     */
    private data class RowScan(
        val busy: Boolean,
        val ink: Int,
        val structured: Boolean,
        val maxRun: Int
    )

    private fun scanRow(
        px: IntArray,
        w: Int,
        edgeThreshold: Int,
        rangeThreshold: Int,
        ignoreBorder: Float,
        noisePixels: Int,
        base: Int,
        lumBuf: IntArray,
        hist: IntArray
    ): RowScan {
        val x0 = (w * ignoreBorder).toInt().coerceIn(0, w - 1)
        val x1 = (w - x0).coerceIn(x0 + 1, w)
        hist.fill(0)
        var prev = luma(px[base + x0])
        lumBuf[x0] = prev
        hist[prev]++
        var lo = prev
        var hi = prev
        var count = 0
        for (x in x0 + 1 until x1) {
            val l = luma(px[base + x])
            lumBuf[x] = l
            hist[l]++
            if (abs(l - prev) > edgeThreshold) count++
            if (l < lo) lo = l
            if (l > hi) hi = l
            prev = l
        }
        if (count > noisePixels || (hi - lo > rangeThreshold && count > 0)) {
            val runs = darkRuns(lumBuf, x0, x1, lo)
            return RowScan(true, count, runs.runs >= MIN_STRUCT_RUNS && runs.maxRun >= MIN_STRUCT_RUN, runs.maxRun)
        }
        val n = x1 - x0
        var acc = 0
        var median = 0
        val half = n / 2
        for (v in 0..255) {
            acc += hist[v]
            if (acc > half) {
                median = v
                break
            }
        }
        var dev = 0
        var centralDev = 0
        var maxBin = 0
        val cx0 = x0 + n / 4
        val cx1 = x1 - n / 4
        for (x in x0 until x1) {
            if (abs(lumBuf[x] - median) > MEDIAN_DEVIATION) {
                dev++
                if (x in cx0 until cx1) centralDev++
            }
        }
        for (v in 0..255) {
            if (hist[v] > maxBin) maxBin = hist[v]
        }
        // Baris sibuk bila banyak menyimpang DENGAN populasi dominan
        // (teks pudar: dua gugus sempit) atau simpangan terpusat di tengah
        // (kalimat). Gradasi mulus (sebaran merata) bukan konten.
        val busy = dev > maxOf(2, n / 100) &&
            (centralDev * 4 >= dev || maxBin * 5 >= n * 2)
        if (!busy) return RowScan(false, count, false, 0)
        val runs = darkRuns(lumBuf, x0, x1, lo)
        return RowScan(true, count, runs.runs >= MIN_STRUCT_RUNS && runs.maxRun >= MIN_STRUCT_RUN, runs.maxRun)
    }

    private data class RunInfo(val runs: Int, val maxRun: Int)

    /**
     * Run gelap (dekat ujung tergelap baris): kalimat = banyak goresan
     * (run 4-12px); arsir = 1-2 garis panjang; screentone = banyak run
     * 1-3px. Uji terhadap ujung gelap (bukan tengah) agar baris
     * screentone — yang terang-gelapnya selang-seling — tidak ikut lolos.
     */
    private fun darkRuns(
        lumBuf: IntArray,
        x0: Int,
        x1: Int,
        lo: Int
    ): RunInfo {
        val darkBelow = lo + MEDIAN_DEVIATION
        var run = 0
        var runs = 0
        var maxRun = 0
        for (x in x0 until x1) {
            if (lumBuf[x] <= darkBelow) {
                run++
            } else if (run > 0) {
                runs++
                if (run > maxRun) maxRun = run
                run = 0
            }
        }
        if (run > 0) {
            runs++
            if (run > maxRun) maxRun = run
        }
        return RunInfo(runs, maxRun)
    }

    private fun luma(c: Int): Int =
        (((c shr 16) and 0xFF) * 77 + ((c shr 8) and 0xFF) * 150 + (c and 0xFF) * 29) shr 8

    private const val MEDIAN_DEVIATION = 16
    private const val MIN_STRUCT_RUNS = 3
    private const val MIN_STRUCT_RUN = 4
}
