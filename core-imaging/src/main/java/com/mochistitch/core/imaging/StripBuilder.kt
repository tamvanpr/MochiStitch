package com.mochistitch.core.imaging

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.mochistitch.core.settings.SplitRule
import com.mochistitch.core.settings.StitchSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Satu berkas strip hasil rakitan. */
data class BuiltStrip(
    val order: Int,
    val fileName: String,
    val preview: Bitmap,
    val file: File,
    val width: Int,
    val height: Int,
    val flagged: Boolean = false,
    val flagReason: String? = null,
    val bytes: Long = 0L
)

enum class BuildPhase(val label: String) {
    MEASURING("Mengukur halaman"),
    ASSEMBLING("Merakit strip vertikal")
}

/**
 * Orkestrasi v5: ukur -> potong halaman raksasa di celah aman (baris
 * bebas-tepi; tanpa OpenCV/ML) -> kelompokkan dengan menahan pasangan
 * halaman yang bersambung piksel dalam satu berkas -> render -> tulis.
 *
 * Jaminan: garis potong tidak pernah melintasi tinta (balon/panel/teks).
 * Halaman yang tak punya celah aman dibiarkan utuh + ditandai; batas
 * antar-berkas yang terpaksa jatuh di sambungan juga ditandai.
 */
class StripBuilder(
    private val openStream: (Uri) -> InputStream?,
    private val scratchDir: File
) {
    constructor(context: Context) : this(
        openStream = { uri -> context.contentResolver.openInputStream(uri) },
        scratchDir = context.cacheDir
    )

    private val renderer = StripRenderer(openStream)

    /** Satu lembar atomik: halaman utuh atau segmen hasil potong aman. */
    private data class Seg(
        val uri: Uri,
        val order: Int,
        val srcTop: Int,
        val srcBottom: Int,
        val renderedH: Int
    )

    suspend fun build(
        uris: List<Uri>,
        settings: StitchSettings,
        onProgress: (BuildPhase, Float) -> Unit = { _, _ -> }
    ): Result<List<BuiltStrip>> = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Tidak ada gambar."))
        try {
            onProgress(BuildPhase.MEASURING, 0.05f)
            val config = StripConfig.fromSettings(settings)
            val measured = uris.mapNotNull { uri ->
                val (w, h) = renderer.measure(uri)
                if (w > 0 && h > 0) StripRenderer.Measured(uri, w, h) else null
            }
            if (measured.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("Gagal membaca dimensi gambar."))
            }
            val stripWidth = measured.maxOf { it.width }
            val limit = settings.maxStripHeight
            val wantCut = settings.splitRule == SplitRule.MAX_HEIGHT && limit > 0

            // 1) Halaman raksasa -> segmen di celah aman.
            val segs = mutableListOf<Seg>()
            measured.forEachIndexed { i, m ->
                val renderedH = (m.height.toLong() * stripWidth / m.width.coerceAtLeast(1)).toInt().coerceAtLeast(1)
                if (wantCut && renderedH > limit) {
                    segs.addAll(segmentPage(i, m, stripWidth, limit))
                } else {
                    segs.add(Seg(m.uri, i, 0, m.height, renderedH))
                }
            }

            // 2) Pasangan halaman berbeda yang bersambung piksel: tahan satu berkas.
            val byOrder = measured.mapIndexed { i, m -> i to m }.toMap()
            val linked = continuityMap(segs, byOrder)
            val sheets = segs.mapIndexed { idx, s -> PageGrouper.Sheet(order = idx, renderedHeight = s.renderedH) }
            // Batas keras: pinning pasangan bersambung tak boleh lebih dari 1,5x batas.
            val hardCap = limit + limit / 2
            val bundles = PageGrouper.group(
                sheets, settings.splitRule, settings.maxStripHeight, settings.pagesPerPack,
                linked = { a, b -> linked.contains(a to b) }, hardCap = hardCap
            )
            if (bundles.isEmpty()) return@withContext Result.failure(IllegalStateException("Tidak ada yang bisa dirakit."))

            onProgress(BuildPhase.MEASURING, 0.2f)
            val strips = mutableListOf<BuiltStrip>()
            var number = 1
            val series = settings.seriesTitle.ifBlank { "MochiStitch" }
            val chapter = settings.chapterLabel.ifBlank { "1" }

            for ((bi, bundle) in bundles.withIndex()) {
                onProgress(BuildPhase.ASSEMBLING, 0.2f + 0.6f * (bi.toFloat() / bundles.size.toFloat()))
                val placements = bundle.sheets.map { sheet ->
                    val s = segs[sheet.order]
                    StripRenderer.Placement(s.uri, s.srcTop, s.srcBottom)
                }
                val whole = renderer.renderStrip(placements, config).getOrThrow()

                val flagged = bundle.tallSingle || bundle.seamCut
                val reason = when {
                    bundle.tallSingle -> "Melebihi batas ${settings.maxStripHeight}px dan tak ada celah aman — dibiarkan utuh, tangani manual"
                    bundle.seamCut -> "Batas berkas jatuh di sambungan halaman (melewati batas ukuran) — periksa balon di batas berkas"
                    else -> null
                }
                strips.add(
                    store(
                        bitmap = whole, number = number++, series = series, chapter = chapter,
                        settings = settings, config = config, flagged = flagged, flagReason = reason
                    )
                )
            }
            onProgress(BuildPhase.ASSEMBLING, 1.0f)
            Result.success(strips)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    /**
     * Bagi satu halaman raksasa menjadi segmen-segmen ≤ [limit] (rendered)
     * dengan garis potong di pusat celah baris bebas-tepi. Tanpa celah:
     * kembalikan halaman utuh (ditandai tallSingle oleh grouper).
     */
    private fun segmentPage(
        order: Int,
        m: StripRenderer.Measured,
        stripWidth: Int,
        limit: Int
    ): List<Seg> {
        val renderedH = (m.height.toLong() * stripWidth / m.width.coerceAtLeast(1)).toInt().coerceAtLeast(1)
        if (renderedH <= limit) {
            return listOf(Seg(m.uri, order, 0, m.height, renderedH))
        }
        val bmp = renderer.decodeSampled(m.uri, maxPixels = 16_000_000L, maxSample = scanSample(m))
            ?: return listOf(Seg(m.uri, order, 0, m.height, renderedH))
        try {
            val dw = bmp.width
            val dh = bmp.height
            if (dw <= 0 || dh <= 0) {
                return listOf(Seg(m.uri, order, 0, m.height, renderedH))
            }
            val safe = BooleanArray(dh)
            val chunk = 64
            val buf = IntArray(dw * chunk)
            var y = 0
            while (y < dh) {
                val rows = min(chunk, dh - y)
                bmp.getPixels(buf, 0, dw, 0, y, dw, rows)
                for (r in 0 until rows) {
                    safe[y + r] = SeamScan.rowIsSafe(buf, r * dw, dw)
                }
                y += rows
            }
            // Batas ke koordinat decode.
            val f = stripWidth.toDouble() / m.width.toDouble()
            val ks = dh.toDouble() / m.height.toDouble()
            val limitDec = (limit.toDouble() / f * ks).toInt().coerceAtLeast(8)
            // Boleh lewat batas sedikit demi celah aman: lebih baik berkas
            // sedikit lebih tinggi daripada memotong tinta atau halaman utuh.
            val overflowDec = (limitDec / 5).coerceIn(128, 2500)
            val plan = SeamScan.planCuts(safe, limitDec, overflow = overflowDec)
            if (plan.cuts.isEmpty() && !plan.tailSafe) {
                return listOf(Seg(m.uri, order, 0, m.height, renderedH))
            }
            val bounds = mutableListOf<Pair<Int, Int>>()
            var prev = 0
            for (c in plan.cuts) {
                bounds.add(prev to c)
                prev = c
            }
            bounds.add(prev to dh)
            return bounds.map { (dTop, dBot) ->
                val sTop = (dTop.toDouble() / ks).roundToInt().coerceIn(0, m.height)
                var sBot = (dBot.toDouble() / ks).roundToInt().coerceIn(0, m.height)
                if (sBot <= sTop) sBot = min(m.height, sTop + 1)
                val h = ((sBot - sTop).toLong() * stripWidth / m.width.coerceAtLeast(1)).toInt().coerceAtLeast(1)
                Seg(m.uri, order, sTop, sBot, h)
            }
        } finally {
            try { bmp.recycle() } catch (t: Throwable) { }
        }
    }

    /**
     * Sampel pindai: resolusi penuh bila muat (garis tipis seperti ekor
     * balon tidak boleh lolos), turun ke 2 hanya untuk halaman raksasa.
     */
    private fun scanSample(m: StripRenderer.Measured): Int =
        if (m.width.toLong() * m.height.toLong() <= 20_000_000L) 1 else 2

    /**
     * Peta pasangan indeks-segmen berurutan yang bersambung piksel
     * (tepi bawah segmen-a berlanjut ke tepi atas segmen-b). Segmen dari
     * halaman yang sama dilewati: urutannya sudah pasti bersambung.
     * Pasangan latar-datar-vs-datar TIDAK dihitung bersambung (margin
     * putih bertemu margin putih bukan alasan menggabung berkas).
     */
    private fun continuityMap(
        segs: List<Seg>,
        byOrder: Map<Int, StripRenderer.Measured>
    ): Set<Pair<Int, Int>> {
        val out = mutableSetOf<Pair<Int, Int>>()
        for (i in 0 until segs.size - 1) {
            val a = segs[i]
            val b = segs[i + 1]
            if (a.order == b.order) continue
            val ma = byOrder[a.order] ?: continue
            val mb = byOrder[b.order] ?: continue
            val r = 48
            val bottom = renderer.edgeStrip(a.uri, ma.width, a.srcBottom - r, a.srcBottom) ?: continue
            val top = renderer.edgeStrip(b.uri, mb.width, b.srcTop, b.srcTop + r) ?: continue
            if (!SeamScan.hasContent(bottom) && !SeamScan.hasContent(top)) continue
            if (SeamScan.rowsContinue(bottom, top)) out.add(i to i + 1)
        }
        return out
    }

    private fun store(
        bitmap: Bitmap,
        number: Int,
        series: String,
        chapter: String,
        settings: StitchSettings,
        config: StripConfig,
        flagged: Boolean,
        flagReason: String?
    ): BuiltStrip {
        val ext = FileNamer.extensionOf(settings.imageFormat)
        val stem = FileNamer.numbered(settings.namePattern, series, chapter, number, settings.numberWidth, settings.imageFormat)
        val dir = File(scratchDir, "mochi_strips").apply { mkdirs() }
        val tmp = File(dir, "$stem.$ext-${System.nanoTime()}.tmp")
        tmp.outputStream().use { out ->
            bitmap.compress(config.compressFormat, config.quality, out)
        }
        val final = File(dir, "$stem.$ext")
        if (final.exists()) final.delete()
        tmp.renameTo(final)

        // Pratinjau kecil: cukup untuk kartu hasil, tidak memegang strip
        // raksasa penuh di RAM. Bitmap asli dilepas setelah dikompres;
        // lebar/tinggi yang dicatat tetap dimensi berkas output asli.
        val fullWidth = bitmap.width
        val fullHeight = bitmap.height
        val preview = scaleForPreview(bitmap, PREVIEW_CAP)
        if (preview !== bitmap) bitmap.recycle()

        return BuiltStrip(
            order = number,
            fileName = "$stem.$ext",
            preview = preview,
            file = final,
            width = fullWidth,
            height = fullHeight,
            flagged = flagged,
            flagReason = flagReason,
            bytes = final.length()
        )
    }

    private companion object {
        const val PREVIEW_CAP = 2048
    }

    private fun scaleForPreview(src: Bitmap, cap: Int): Bitmap {
        val longest = max(src.width, src.height)
        if (longest <= cap) return src
        val scale = cap.toFloat() / longest
        val w = (src.width * scale).roundToInt().coerceAtLeast(1)
        val h = (src.height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, w, h, true)
    }
}
