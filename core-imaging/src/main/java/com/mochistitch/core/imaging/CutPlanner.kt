package com.mochistitch.core.imaging

import kotlin.math.min

data class Band(val start: Int, val end: Int) {
    val center: Int get() = (start + end) / 2
    val size: Int get() = end - start
}

data class PlannedCut(val y: Int, val forced: Boolean)

object CutPlanner {
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

    /** Baris yang terlarang: sibuk atau berjarak <= [margin] dari baris sibuk. */
    fun blockedRows(busy: BooleanArray, margin: Int): BooleanArray {
        val h = busy.size
        val blocked = BooleanArray(h)
        var lastBusy = -margin - 1
        for (y in 0 until h) {
            if (busy[y]) lastBusy = y
            if (y - lastBusy <= margin) blocked[y] = true
        }
        return blocked
    }

    fun plan(
        profile: RowProfile,
        maxLen: Int,
        minLen: Int = maxLen / 3,
        overshoot: Int = maxLen / 4,
        margin: Int = 8,
        minBand: Int = margin * 2
    ): List<PlannedCut> {
        val h = profile.busy.size
        val blocked = blockedRows(profile.busy, margin)
        val bands = safeBands(profile.busy, margin, minBand)
        val cuts = mutableListOf<PlannedCut>()
        var start = 0
        while (h - start > maxLen) {
            val target = start + maxLen
            val pick = bands.lastOrNull { it.center in (start + minLen)..target }
                ?: bands.firstOrNull { it.center in (target + 1)..(target + overshoot) }
            if (pick != null) {
                cuts += PlannedCut(pick.center, forced = false)
                start = pick.center
            } else {
                val lo = start + minLen
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
        return cuts
    }
}
