package com.mochistitch.core.imaging

import com.mochistitch.core.settings.SplitRule

/**
 * Pengelompok halaman sadar-sambungan: tiap berkas output berisi halaman
 * (atau segmen halaman) UTUH yang berurutan.
 *
 * - Potongan dalam halaman hanya terjadi di celah yang sudah direncanakan
 *   aman oleh pemanggil (baris bebas-tepi); grouper tidak pernah memotong
 *   sendiri.
 * - Batas antar-berkas diusahakan tidak jatuh di pasangan lembar yang
 *   bersambung piksel ([linked]); pasangan bersambung digabung sampai batas
 *   lunak terlampaui atau batas keras [hardCap] tercapai. Batas yang
 *   terpaksa jatuh di sambungan ditandai [Bundle.seamCut] agar tampil
 *   sebagai flag tinjau di pratinjau.
 */
object PageGrouper {

    /**
     * [renderedHeight] = tinggi lembar setelah diskala ke lebar strip.
     * [safeBreak] = tepi ATAS lembar ini boleh menjadi batas antar-berkas
     * (tepi hasil potongan terencana yang sudah terverifikasi aman).
     * Tepi batas halaman asli = false: batas berkas tidak boleh jatuh di
     * sana kecuali terpaksa (ditandai [Bundle.seamCut]).
     */
    data class Sheet(val order: Int, val renderedHeight: Int, val safeBreak: Boolean = true)

    data class Bundle(
        val sheets: List<Sheet>,
        val tallSingle: Boolean = false,
        val seamCut: Boolean = false
    )

    fun group(
        sheets: List<Sheet>,
        rule: SplitRule,
        maxStripHeight: Int,
        pagesPerPack: Int
    ): List<Bundle> = group(sheets, rule, maxStripHeight, pagesPerPack, null, 0)

    /**
     * @param linked (a, b) true bila lembar order-a bersambung piksel dengan
     *   order-b. null = tanpa informasi sambungan (perilaku lama).
     * @param hardCap tinggi maksimum mutlak satu berkas saat menahan
     *   pasangan bersambung (0 = tanpa penahanan).
     *   Lembar dengan [Sheet.safeBreak] = false ikut ditahan sampai
     *   [hardCap]; bila tetap tak muat, batas paksa ditandai
     *   [Bundle.seamCut].
     */
    fun group(
        sheets: List<Sheet>,
        rule: SplitRule,
        maxStripHeight: Int,
        pagesPerPack: Int,
        linked: ((Int, Int) -> Boolean)?,
        hardCap: Int
    ): List<Bundle> {
        if (sheets.isEmpty()) return emptyList()
        return when (rule) {
            SplitRule.WHOLE -> listOf(Bundle(sheets))
            SplitRule.PAGES_PER_PACK -> sheets.chunked(pagesPerPack.coerceAtLeast(1)) { chunk ->
                Bundle(chunk, tallSingle = maxStripHeight > 0 && chunk.any { it.renderedHeight > maxStripHeight })
            }
            SplitRule.MAX_HEIGHT -> groupByHeight(sheets, maxStripHeight, linked, hardCap)
        }
    }

    private fun groupByHeight(
        sheets: List<Sheet>,
        limit: Int,
        linked: ((Int, Int) -> Boolean)?,
        hardCap: Int
    ): List<Bundle> {
        if (limit <= 0) return listOf(Bundle(sheets))
        val cap = if (hardCap > limit) hardCap else limit
        val canHold = linked != null && hardCap > limit
        val out = mutableListOf<Bundle>()
        var current = mutableListOf<Sheet>()
        var height = 0
        var seamCut = false
        for (sheet in sheets) {
            if (sheet.renderedHeight > limit) {
                if (current.isNotEmpty()) {
                    out.add(Bundle(current, seamCut = seamCut))
                    current = mutableListOf()
                    height = 0
                    seamCut = false
                }
                out.add(Bundle(listOf(sheet), tallSingle = true))
                continue
            }
            if (current.isNotEmpty() && height + sheet.renderedHeight > limit) {
                val prev = current.last()
                val pairLinked = linked?.invoke(prev.order, sheet.order) == true
                if (pairLinked && canHold && height + sheet.renderedHeight <= cap) {
                    // Tahan: gabung pasangan bersambung walau melewati batas lunak.
                    current.add(sheet)
                    height += sheet.renderedHeight
                } else if (!sheet.safeBreak && height + sheet.renderedHeight <= cap) {
                    // Tepi atas lembar ini bukan potongan terencana (batas
                    // halaman asli): tahan dalam berkas yang sama agar batas
                    // berkas tidak membelah konten yang belum dicek.
                    current.add(sheet)
                    height += sheet.renderedHeight
                } else {
                    // Putus di batas aman terakhir (mundur), bukan di tengah
                    // sambungan — kecuali seluruh berkas memang satu sambungan.
                    val linkFn = linked
                    val oldSize = current.size
                    val cutAt = if (pairLinked && linkFn != null) lastSafeBreak(current, linkFn) else oldSize
                    out.add(Bundle(current.subList(0, cutAt).toList(), seamCut = seamCut))
                    val rest = current.subList(cutAt, oldSize).toList()
                    current = (rest + sheet).toMutableList()
                    height = rest.sumOf { it.renderedHeight } + sheet.renderedHeight
                    // Berkas baru ditandai bila batasnya jatuh di sambungan
                    // ATAU di tepi yang belum terverifikasi aman.
                    seamCut = cutAt == oldSize && (pairLinked || !sheet.safeBreak)
                }
            } else {
                current.add(sheet)
                height += sheet.renderedHeight
            }
        }
        if (current.isNotEmpty()) out.add(Bundle(current, seamCut = seamCut))
        return out
    }

    /**
     * Indeks mulai berkas baru: tepat setelah batas non-sambung terakhir
     * (batas aman). Bila seluruh isi berkas saling bersambung, kembalikan
     * size (= putus paksa di batas limit, ditandai seamCut oleh pemanggil).
     */
    private fun lastSafeBreak(current: List<Sheet>, linked: (Int, Int) -> Boolean): Int {
        for (j in current.size - 1 downTo 1) {
            if (!linked(current[j - 1].order, current[j].order)) return j
        }
        return current.size
    }
}
