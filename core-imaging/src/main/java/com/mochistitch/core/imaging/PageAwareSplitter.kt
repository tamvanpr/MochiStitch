package com.mochistitch.core.imaging

import com.mochistitch.core.settings.SplitMode

/**
 * Pembagi sadar-halaman (fondasi rombak total v2).
 *
 * Penggabung SELALU tahu di mana tiap halaman asli mulai/berakhir
 * ([PageRef.height] per halaman). Jadi pemotongan output dilakukan
 * TEPAT di batas halaman — tidak pernah di tengah konten. Tidak ada
 * deteksi gambar, ambang, atau tebakan yang terlibat di sini.
 *
 * Satu-satunya pengecualian: satu halaman tunggal yang tingginya
 * melebihi [maxPixelLength] ([PageGroup.overflow] = true). Halaman itu
 * dibelah di celah kertas via GutterScanner oleh pemanggil.
 */
object PageAwareSplitter {

    /** Satu halaman asli. [height] = tinggi halaman SETELAH diskala ke lebar canvas. */
    data class PageRef(val index: Int, val height: Int)

    /** Satu file output: [pages] berurutan. */
    data class PageGroup(val pages: List<PageRef>, val overflow: Boolean = false)

    fun split(
        pages: List<PageRef>,
        splitMode: SplitMode,
        maxPixelLength: Int,
        maxPagesPerFile: Int
    ): List<PageGroup> {
        if (pages.isEmpty()) return emptyList()
        return when (splitMode) {
            SplitMode.NO_LIMIT -> listOf(PageGroup(pages))
            SplitMode.PAGES_PER_FILE -> splitByCount(pages, maxPagesPerFile, maxPixelLength)
            SplitMode.MAX_PIXELS -> splitByLength(pages, maxPixelLength)
        }
    }

    private fun splitByCount(
        pages: List<PageRef>,
        maxPagesPerFile: Int,
        maxPixelLength: Int
    ): List<PageGroup> {
        val size = maxPagesPerFile.coerceAtLeast(1)
        return pages.chunked(size).map { chunk ->
            PageGroup(
                pages = chunk,
                overflow = maxPixelLength > 0 && chunk.any { it.height > maxPixelLength }
            )
        }
    }

    private fun splitByLength(pages: List<PageRef>, maxPixelLength: Int): List<PageGroup> {
        if (maxPixelLength <= 0) return listOf(PageGroup(pages))
        val groups = mutableListOf<PageGroup>()
        var current = mutableListOf<PageRef>()
        var currentHeight = 0
        for (page in pages) {
            if (page.height > maxPixelLength) {
                // Halaman raksasa: grup sendiri, wajib dibelah di kertas.
                if (current.isNotEmpty()) {
                    groups.add(PageGroup(current))
                    current = mutableListOf()
                    currentHeight = 0
                }
                groups.add(PageGroup(listOf(page), overflow = true))
                continue
            }
            if (current.isNotEmpty() && currentHeight + page.height > maxPixelLength) {
                groups.add(PageGroup(current))
                current = mutableListOf()
                currentHeight = 0
            }
            current.add(page)
            currentHeight += page.height
        }
        if (current.isNotEmpty()) groups.add(PageGroup(current))
        return groups
    }
}
