package com.mochistitch.core.imaging

import kotlin.math.min

data class Band(val start: Int, val end: Int) {
    val center: Int get() = (start + end) / 2
    val size: Int get() = end - start
}

data class PlannedCut(val y: Int, val forced: Boolean)

object CutPlanner {
    private const val BAND_EDGE_BACKOFF = 2

    /**
     * Pita aman = baris bebas setelah dilasi [margin]. Pita yang lebih
     * sempit dari [minBand] diabaikan: celah sekecil jarak antar-baris teks
     * di dalam balon BUKAN tempat potong yang aman.
     */
    fun safeBands(busy: BooleanArray, margin: Int, minBand: Int): List<Band> {
        val blocked = blockedRows(busy, margin)
        val h = busy.size
        val bands = mutableListOf<Band>()
        var s = -1
        for (y in 0 until h) {
            if (!blocked[y]) {
                if (s < 0) s = y
            } else if (s >= 0) {
                if (y - s >= minBand) bands += Band(s, y)
                s = -1
            }
        }
        if (s >= 0 && h - s >= minBand) bands += Band(s, h)
        return bands
    }

    /** Baris yang terlarang: sibuk atau berjarak <= [margin] dari baris sibuk (dua arah). */
    fun blockedRows(busy: BooleanArray, margin: Int): BooleanArray {
        val h = busy.size
        val blocked = BooleanArray(h)
        var lastBusy = -margin - 1
        for (y in 0 until h) {
            if (busy[y]) lastBusy = y
            if (y - lastBusy <= margin) blocked[y] = true
        }
        var nextBusy = h + margin + 1
        for (y in h - 1 downTo 0) {
            if (busy[y]) nextBusy = y
            if (nextBusy - y <= margin) blocked[y] = true
        }
        return blocked
    }

    /**
     * Rencana potong: di tiap jendela [start+minLen, start+maxLen] pilih
     * CELAH AMAN TERLEBAR (bukan yang terakhir) lalu potong di dekat ujung
     * terjauhnya. Celah lebar = jarak maksimal dari tinta di kedua sisi,
     * jadi kecil kemungkinan mendarat di teks yang lolos deteksi — dan
     * posisi potong tidak lagi terpaku di kelipatan batas ukuran.
     */
    fun plan(
        profile: RowProfile,
        maxLen: Int,
        minLen: Int = maxLen / 3,
        overshoot: Int = maxLen / 4,
        margin: Int = 8,
        minBand: Int = margin
    ): List<PlannedCut> {
        val h = profile.busy.size
        val blocked = blockedRows(profile.busy, margin)
        val bands = safeBands(profile.busy, margin, minBand)
        val cuts = mutableListOf<PlannedCut>()
        var start = 0
        while (h - start > maxLen) {
            val target = start + maxLen
            val lo = start + minLen
            var bestStart = -1
            var bestEnd = -1
            var bestSize = -1
            for (b in bands) {
                val s = maxOf(b.start, lo)
                val e = minOf(b.end, target + 1)
                val size = e - s
                if (size >= minBand && size >= bestSize) {
                    bestSize = size
                    bestStart = s
                    bestEnd = e
                }
            }
            if (bestStart >= 0) {
                val y = (bestEnd - 1 - BAND_EDGE_BACKOFF).coerceAtLeast(bestStart)
                cuts += PlannedCut(y, forced = false)
                start = y
            } else {
                // Overshoot: potong di baris aman paling AWAL lewat batas
                // (bukan tengah pita) agar berkas hanya sedikit melewati
                // batas, bukan melompat jauh.
                val over = bands.firstOrNull { it.end > target + 1 && it.start <= target + overshoot }
                if (over != null) {
                    val y = maxOf(over.start, target + 1).coerceAtMost(over.end - 1)
                    cuts += PlannedCut(y, forced = false)
                    start = y
                } else {
                    val hi = min(target, h - 1)
                    if (lo > hi) break
                    var bestY = -1
                    var bestScore = Long.MAX_VALUE
                    for (y in lo..hi) {
                        if (blocked[y]) continue
                        val score = profile.ink[y] * 1000L + (target - y)
                        if (score < bestScore) {
                            bestScore = score
                            bestY = y
                        }
                    }
                    if (bestY < 0) {
                        bestY = lo
                        bestScore = Long.MAX_VALUE
                        for (y in lo..hi) {
                            val score = profile.ink[y] * 1000L + (target - y)
                            if (score < bestScore) {
                                bestScore = score
                                bestY = y
                            }
                        }
                    }
                    cuts += PlannedCut(bestY, forced = true)
                    start = bestY
                }
            }
        }
        return cuts
    }
}
