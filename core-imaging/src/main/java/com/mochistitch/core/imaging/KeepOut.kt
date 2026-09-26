package com.mochistitch.core.imaging

object KeepOut {
    /**
     * Daerah terang yang TERKURUNG (tak terjangkau flood fill dari tepi
     * gambar) = bagian dalam balon / kotak dialog. Baris yang sebagian
     * besarnya terkurung dilarang untuk potong.
     *
     * Hanya komponen terkurung yang KECIL (<= [maxAreaFrac] luas gambar)
     * yang dihitung: bingkai panel menutup region raksasa (isi panel =
     * art yang boleh dipotong), sedangkan balon berukuran kecil.
     *
     * Murni array (unit-testable, tanpa Bitmap). Panggil dengan gambar
     * kecil (lebar ~240px) lalu petakan hasilnya ke skala pindai.
     */
    fun enclosed(
        px: IntArray,
        w: Int,
        h: Int,
        brightTolerance: Int = 28,
        minFraction: Float = 0.04f,
        maxAreaFrac: Float = 0.20f
    ): BooleanArray {
        val none = BooleanArray(h)
        if (w <= 0 || h <= 0 || px.size < w * h) return none
        val paper = paperLuma(px, w, h)
        if (paper < MIN_PAPER_LUMA) return none
        val bright = BooleanArray(w * h)
        for (i in 0 until w * h) {
            bright[i] = luma(px[i]) >= paper - brightTolerance
        }
        val reached = BooleanArray(w * h)
        val queue = ArrayDeque<Int>()
        for (x in 0 until w) {
            if (bright[x]) {
                reached[x] = true
                queue.add(x)
            }
            val b = (h - 1) * w + x
            if (bright[b] && !reached[b]) {
                reached[b] = true
                queue.add(b)
            }
        }
        for (y in 0 until h) {
            val l = y * w
            if (bright[l] && !reached[l]) {
                reached[l] = true
                queue.add(l)
            }
            val r = l + w - 1
            if (bright[r] && !reached[r]) {
                reached[r] = true
                queue.add(r)
            }
        }
        while (queue.isNotEmpty()) {
            val i = queue.removeFirst()
            val x = i % w
            val y = i / w
            if (x > 0) visit(bright, reached, queue, i - 1)
            if (x < w - 1) visit(bright, reached, queue, i + 1)
            if (y > 0) visit(bright, reached, queue, i - w)
            if (y < h - 1) visit(bright, reached, queue, i + w)
        }
        val out = BooleanArray(h)
        // Labeli komponen terkurung; buang yang raksasa (isi panel/art).
        val label = IntArray(w * h)
        var nextLabel = 0
        val areas = ArrayList<Int>()
        for (i in 0 until w * h) {
            if (!bright[i] || reached[i] || label[i] != 0) continue
            nextLabel++
            var area = 0
            val stack = ArrayDeque<Int>()
            stack.add(i)
            label[i] = nextLabel
            while (stack.isNotEmpty()) {
                val c = stack.removeLast()
                area++
                val x = c % w
                val y = c / w
                if (x > 0) pushLabel(bright, reached, label, stack, nextLabel, c - 1)
                if (x < w - 1) pushLabel(bright, reached, label, stack, nextLabel, c + 1)
                if (y > 0) pushLabel(bright, reached, label, stack, nextLabel, c - w)
                if (y < h - 1) pushLabel(bright, reached, label, stack, nextLabel, c + w)
            }
            areas.add(area)
        }
        val maxArea = (w.toLong() * h * maxAreaFrac).toInt()
        val big = BooleanArray(nextLabel + 1)
        for (l in 1..nextLabel) {
            if (areas[l - 1] > maxArea) big[l] = true
        }
        for (y in 0 until h) {
            var enclosedCount = 0
            val base = y * w
            for (x in 0 until w) {
                val li = label[base + x]
                if (li != 0 && !big[li]) enclosedCount++
            }
            out[y] = enclosedCount.toFloat() / w > minFraction
        }
        return out
    }

    private fun pushLabel(
        bright: BooleanArray,
        reached: BooleanArray,
        label: IntArray,
        stack: ArrayDeque<Int>,
        value: Int,
        i: Int
    ) {
        if (bright[i] && !reached[i] && label[i] == 0) {
            label[i] = value
            stack.add(i)
        }
    }

    private fun visit(bright: BooleanArray, reached: BooleanArray, queue: ArrayDeque<Int>, i: Int) {
        if (bright[i] && !reached[i]) {
            reached[i] = true
            queue.add(i)
        }
    }

    /**
     * Zona teks: baris-baris "terstruktur" (goresan, bukan screentone)
     * dikelompokkan bila celah antar-run <= [gap], lalu tiap kelompok
     * diperlebar ±[expand] (dinding balon + ekor). Interior balon berekor
     * — yang lolos dari flood fill karena garis pinggirnya bocor —
     * tertangkap di sini karena kuncinya teks di dalamnya, bukan garisnya.
     * Kelompok satu baris tak diperlebar (cukup dilasi margin biasa).
     */
    fun textZones(
        structured: BooleanArray,
        gap: Int = 20,
        expand: Int = 20
    ): BooleanArray {
        val h = structured.size
        val out = BooleanArray(h)
        var i = 0
        while (i < h) {
            while (i < h && !structured[i]) i++
            if (i >= h) break
            var j = i
            var k = j + 1
            while (k < h && !structured[k]) k++
            while (k < h && k - j - 1 <= gap) {
                j = k
                k = j + 1
                while (k < h && !structured[k]) k++
            }
            val pad = if (j > i) expand else 0
            for (y in (i - pad).coerceAtLeast(0)..(j + pad).coerceAtMost(h - 1)) out[y] = true
            i = j + 1
        }
        return out
    }

    private fun paperLuma(px: IntArray, w: Int, h: Int): Int {
        val samples = IntArray(2 * (w + h))
        var n = 0
        for (x in 0 until w) {
            samples[n++] = luma(px[x])
            samples[n++] = luma(px[(h - 1) * w + x])
        }
        for (y in 0 until h) {
            samples[n++] = luma(px[y * w])
            samples[n++] = luma(px[y * w + w - 1])
        }
        samples.sort(0, n)
        return samples[n / 2]
    }

    private fun luma(c: Int): Int =
        (((c shr 16) and 0xFF) * 77 + ((c shr 8) and 0xFF) * 150 + (c and 0xFF) * 29) shr 8

    private const val MIN_PAPER_LUMA = 110
}
