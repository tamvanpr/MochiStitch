package com.mochistitch.core.imaging

import kotlin.math.min

data class Band(val start: Int, val end: Int) {
    val center: Int get() = (start + end) / 2
    val size: Int get() = end - start
}

data class PlannedCut(val y: Int, val forced: Boolean)

object CutPlanner {
    fun safeBands(busy: BooleanArray, margin: Int, minBand: Int): List<Band> {
        val h = busy.size
        val blocked = BooleanArray(h)
        var lastBusy = -margin - 1
        for (y in 0 until h) {
            if (busy[y]) lastBusy = y
            if (y - lastBusy <= margin) blocked[y] = true
        }
        val bands = mutableListOf<Band>()
        var s = -1
        for (y in 0..h) {
            val free = y == h || !blocked[y]
            if (free && s < 0) s = y
            if (!free && s >= 0) {
                if (y - s >= minBand) bands += Band(s, y)
                s = -1
            }
        }
        return bands
    }

    fun plan(
        profile: RowProfile,
        maxLen: Int,
        minLen: Int = maxLen / 3,
        overshoot: Int = maxLen / 4,
        margin: Int = 8,
        minBand: Int = 2
    ): List<PlannedCut> {
        val h = profile.busy.size
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
                var bestY = lo
                var bestScore = Long.MAX_VALUE
                for (y in lo..hi) {
                    val score = profile.ink[y] * 1000L + (target - y)
                    if (score < bestScore) {
                        bestScore = score
                        bestY = y
                    }
                }
                cuts += PlannedCut(bestY, forced = true)
                start = bestY
            }
        }
        return cuts
    }
}
