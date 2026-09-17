package com.mochistitch.core.imaging

import com.mochistitch.core.settings.SplitRule

/**
 * Pengelompok halaman sadar-batas: tiap berkas output berisi halaman UTUH
 * yang berurutan. Potongan tidak pernah jatuh di tengah halaman.
 */
object PageGrouper {

    /** [renderedHeight] = tinggi halaman setelah diskala ke lebar strip. */
    data class Sheet(val order: Int, val renderedHeight: Int)

    data class Bundle(val sheets: List<Sheet>, val tallSingle: Boolean = false)

    fun group(
        sheets: List<Sheet>,
        rule: SplitRule,
        maxStripHeight: Int,
        pagesPerPack: Int
    ): List<Bundle> {
        if (sheets.isEmpty()) return emptyList()
        return when (rule) {
            SplitRule.WHOLE -> listOf(Bundle(sheets))
            SplitRule.PAGES_PER_PACK -> sheets.chunked(pagesPerPack.coerceAtLeast(1)) { chunk ->
                Bundle(chunk, tallSingle = maxStripHeight > 0 && chunk.any { it.renderedHeight > maxStripHeight })
            }
            SplitRule.MAX_HEIGHT -> groupByHeight(sheets, maxStripHeight)
        }
    }

    private fun groupByHeight(sheets: List<Sheet>, limit: Int): List<Bundle> {
        if (limit <= 0) return listOf(Bundle(sheets))
        val out = mutableListOf<Bundle>()
        var current = mutableListOf<Sheet>()
        var height = 0
        for (sheet in sheets) {
            if (sheet.renderedHeight > limit) {
                if (current.isNotEmpty()) {
                    out.add(Bundle(current))
                    current = mutableListOf()
                    height = 0
                }
                out.add(Bundle(listOf(sheet), tallSingle = true))
                continue
            }
            if (current.isNotEmpty() && height + sheet.renderedHeight > limit) {
                out.add(Bundle(current))
                current = mutableListOf()
                height = 0
            }
            current.add(sheet)
            height += sheet.renderedHeight
        }
        if (current.isNotEmpty()) out.add(Bundle(current))
        return out
    }
}
