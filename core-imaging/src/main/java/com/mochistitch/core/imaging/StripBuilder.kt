package com.mochistitch.core.imaging

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.mochistitch.core.common.BannerPolicy
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
    val bytes: Long = 0L,
    /** Strip banner situs dicrop di berkas ini (info, bukan peringatan). */
    val bannerCut: Boolean = false
)

enum class BuildPhase(val label: String) {
    MEASURING("Mengukur halaman"),
    ASSEMBLING("Merakit strip vertikal")
}

/**
 * Orkestrasi v6: ukur -> potong halaman raksasa di celah aman (paper-aware
 * + cek vertikal + potong tengah; tanpa OpenCV/ML) -> kelompokkan dengan
 * menahan pasangan halaman yang bersambung piksel dalam satu berkas ->
 * render region-decode -> tulis.
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
        val renderedH: Int,
        val bannerCut: Boolean = false
    )

    /** Hasil rakitan + catatan banner (null = kebijakan banner tak dipakai). */
    data class BuildOutput(val strips: List<BuiltStrip>, val bannerNote: String? = null)

    suspend fun build(
        uris: List<Uri>,
        settings: StitchSettings,
        onProgress: (BuildPhase, Float) -> Unit = { _, _ -> },
        banner: BannerPolicy? = null
    ): Result<BuildOutput> = withContext(Dispatchers.IO) {
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

            // 0) Banner situs (mis. baozimh 200px): crop HANYA bila strip
            // atas/bawah terbukti identik antar-halaman (gerbang BannerGate).
            // Selalu ada catatan keputusan (null = kebijakan tak dipakai).
            val bannerResult = bannerCrops(measured, banner)
            val bannerCrops = bannerResult.crops

            // 1) Halaman raksasa -> segmen di celah aman (v6: vertikal+paper+tengah).
            val segs = mutableListOf<Seg>()
            measured.forEachIndexed { i, m ->
                val (cutTop, cutBot) = bannerCrops[i] ?: (0 to 0)
                val effTop = cutTop.coerceIn(0, m.height)
                val effBot = (m.height - cutBot).coerceIn(effTop + 1, m.height)
                val effH = (effBot - effTop).coerceAtLeast(1)
                val renderedH = (effH.toLong() * stripWidth / m.width.coerceAtLeast(1)).toInt().coerceAtLeast(1)
                val wasCut = effTop > 0 || effBot < m.height
                if (wantCut && renderedH > limit) {
                    segs.addAll(segmentPage(i, m, stripWidth, limit, effTop, effBot, wasCut))
                } else {
                    segs.add(Seg(m.uri, i, effTop, effBot, renderedH, wasCut))
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

                val anyBanner = bundle.sheets.any { sheet -> segs[sheet.order].bannerCut }
                val flagged = bundle.tallSingle || bundle.seamCut
                val reason = when {
                    bundle.tallSingle -> "Melebihi batas ${settings.maxStripHeight}px dan tak ada celah aman — dibiarkan utuh, tangani manual"
                    bundle.seamCut -> "Batas berkas jatuh di sambungan halaman (melewati batas ukuran) — periksa balon di batas berkas"
                    else -> null
                }
                strips.add(
                    store(
                        bitmap = whole, number = number++, series = series, chapter = chapter,
                        settings = settings, config = config, flagged = flagged, flagReason = reason,
                        bannerCut = anyBanner
                    )
                )
            }
            onProgress(BuildPhase.ASSEMBLING, 1.0f)
            Result.success(BuildOutput(strips, bannerResult.note))
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    /** Hasil gerbang banner: crop per indeks + catatan keputusan untuk user. */
    private data class BannerResult(val crops: Map<Int, Pair<Int, Int>>, val note: String?)

    /**
     * Kebijakan banner -> crop (topCut, bottomCut) per indeks halaman,
     * dalam piksel gambar asli. Keputusan per halaman (bukan bulat):
     * halaman yang strip-nya tidak cocok dibiarkan utuh. Selalu sertakan
     * catatan MENGAPA begitu (diagnostik, bukan diam).
     */
    private fun bannerCrops(
        measured: List<StripRenderer.Measured>,
        policy: BannerPolicy?
    ): BannerResult {
        if (policy == null || measured.isEmpty()) return BannerResult(emptyMap(), null)
        val need = policy.minPages.coerceAtLeast(2)
        val tallIdx = measured.indices.filter { i ->
            measured[i].height >= max(policy.minPageH, policy.stripPx * 2 + 100)
        }
        if (tallIdx.size < need) {
            return BannerResult(emptyMap(), "Banner: hanya ${tallIdx.size} halaman cukup tinggi (butuh $need) — tidak dicek.")
        }
        fun strip(m: StripRenderer.Measured, top: Boolean): BannerGate.Strip? {
            val h = policy.stripPx.coerceIn(1, m.height)
            val patch = if (top) {
                renderer.edgePatch(m.uri, m.width, m.height, 0, h)
            } else {
                renderer.edgePatch(m.uri, m.width, m.height, m.height - h, m.height)
            } ?: return null
            if (patch.h <= 0 || patch.w <= 0) return null
            return BannerGate.Strip(patch.px, patch.w, patch.h)
        }
        val tops = tallIdx.mapNotNull { i ->
            strip(measured[i], top = true)?.let { i to it }
        }
        val bots = tallIdx.mapNotNull { i ->
            strip(measured[i], top = false)?.let { i to it }
        }
        val need = policy.minPages.coerceAtLeast(2)
        if (policy.checkTop && tops.size < need && policy.checkBottom && bots.size < need) {
            return BannerResult(emptyMap(), "Banner: strip gagal dibaca (${tops.size}/${bots.size} dari $need) — dilewati.")
        }
        val topStrips = tops.map { it.second }
        val botStrips = bots.map { it.second }
        val decision = BannerGate.decide(topStrips, botStrips, policy)
        // Petakan kembali indeks keputusan (dalam daftar kandidat) ke indeks halaman.
        val topIdx = tops.map { it.first }
        val botIdx = bots.map { it.first }
        val out = mutableMapOf<Int, Pair<Int, Int>>()
        decision.top.forEach { k -> topIdx.getOrNull(k)?.let { i -> out[i] = policy.stripPx to (out[i]?.second ?: 0) } }
        decision.bottom.forEach { k -> botIdx.getOrNull(k)?.let { i -> out[i] = (out[i]?.first ?: 0) to policy.stripPx } }
        if (out.isEmpty()) {
            return BannerResult(
                emptyMap(),
                "Banner: strip atas/bawah beda-beda antar-halaman (${tallIdx.size} dicek) — bukan banner seragam, dilewati."
            )
        }
        val t = decision.top.size
        val b = decision.bottom.size
        val where = listOf(
            if (t > 0) "atas $t" else null,
            if (b > 0) "bawah $b" else null
        ).filterNotNull().joinToString(" + ")
        return BannerResult(out, "Banner: dicrop $where (${out.size} halaman).")
    }

    /**
     * Bagi satu halaman raksasa menjadi segmen-segmen ≤ [limit] (rendered)
     * dengan garis potong di TENGAH celah paper-aware (horizontal + vertikal).
     * Rentang sumber dibatasi [effTop, effBot) (sesudah crop banner).
     * Tanpa celah: kembalikan halaman utuh (ditandai tallSingle oleh grouper).
     *
     * Partisi dijamin eksak: sTop[0]=effTop, sBot[last]=effBot,
     * sBot[i]=sTop[i+1].
     */
    private fun segmentPage(
        order: Int,
        m: StripRenderer.Measured,
        stripWidth: Int,
        limit: Int,
        effTop: Int,
        effBot: Int,
        wasCut: Boolean
    ): List<Seg> {
        val effH = (effBot - effTop).coerceAtLeast(1)
        val renderedH = (effH.toLong() * stripWidth / m.width.coerceAtLeast(1)).toInt().coerceAtLeast(1)
        if (renderedH <= limit) {
            return listOf(Seg(m.uri, order, effTop, effBot, renderedH, wasCut))
        }
        val bmp = renderer.decodeSampled(m.uri, maxPixels = 16_000_000L, maxSample = scanSample(m))
            ?: return listOf(Seg(m.uri, order, 0, m.height, renderedH))
        try {
            val dw = bmp.width
            val dh = bmp.height
            if (dw <= 0 || dh <= 0) {
                return listOf(Seg(m.uri, order, 0, m.height, renderedH))
            }
            // — Pindai v6: masker horizontal + vertikal + paper. —
            val horiz = BooleanArray(dh)
            val chunk = 64
            val buf = IntArray(dw * chunk)
            // Kumpulkan sampel baris untuk estimasi kertas.
            val paperSamples = mutableListOf<IntArray>()
            var y = 0
            while (y < dh) {
                val rows = min(chunk, dh - y)
                bmp.getPixels(buf, 0, dw, 0, y, dw, rows)
                for (r in 0 until rows) {
                    horiz[y + r] = SeamScan.rowIsSafe(buf, r * dw, dw)
                    if (paperSamples.size < 24 && (y + r) % max(1, dh / 24) == 0) {
                        paperSamples.add(buf.copyOfRange(r * dw, r * dw + dw))
                    }
                }
                y += rows
            }
            val paper = SeamScan.estimatePaper(paperSamples)
            // Terapkan paper-aware: baris yang median-nya jauh dari kertas
            // (mis. abu screentone pekat / tinta merata yang lolos cek
            // horizontal) ditandai tidak aman.
            y = 0
            while (y < dh) {
                val rows = min(chunk, dh - y)
                bmp.getPixels(buf, 0, dw, 0, y, dw, rows)
                for (r in 0 until rows) {
                    if (horiz[y + r]) {
                        horiz[y + r] = SeamScan.rowIsSafe(buf, r * dw, dw, paper)
                    }
                }
                y += rows
            }
            // Masker vertikal: butuh akses baris acak — baca per baris via
            // getPixels 1-baris (murah pada bitmap pindai ≤16MP).
            val rowBuf = IntArray(dw)
            val vert = SeamScan.rowsVertSafe(dw, dh, getRow = { yy, out ->
                bmp.getPixels(out, 0, dw, 0, yy, dw, 1)
            })
            // Hindari alokasi ganda: pakai rowBuf agar lambda tidak
            // mengalokasi sendiri (diabaikan, getPixels menulis ke out).
            @Suppress("UNUSED_VARIABLE")
            val keep = rowBuf
            val safe = SeamScan.combineSafe(horiz, vert)
            // Batas ke koordinat decode; rencana potong hanya di jendela
            // efektif [effTop, effBot) (sesudah crop banner).
            val f = stripWidth.toDouble() / m.width.toDouble()
            val ks = dh.toDouble() / m.height.toDouble()
            val eTopDec = (effTop.toDouble() * ks).roundToInt().coerceIn(0, dh)
            val eBotDec = (effBot.toDouble() * ks).roundToInt().coerceIn(eTopDec + 1, dh)
            val limitDec = (limit.toDouble() / f * ks).toInt().coerceAtLeast(8)
            // Boleh lewat batas sedikit demi celah aman: lebih baik berkas
            // sedikit lebih tinggi daripada memotong tinta atau halaman utuh.
            val overflowDec = (limitDec / 5).coerceIn(128, 2500)
            val window = safe.copyOfRange(eTopDec, eBotDec)
            val plan = SeamScan.planCuts(window, limitDec, overflow = overflowDec)
            if (plan.cuts.isEmpty() && !plan.tailSafe) {
                return listOf(Seg(m.uri, order, effTop, effBot, renderedH, wasCut))
            }
            // — Partisi eksak dalam koordinat sumber (tanpa gap/duplikat). —
            val dBounds = mutableListOf<Pair<Int, Int>>()
            var prev = eTopDec
            for (c in plan.cuts) {
                val cc = (c + eTopDec).coerceIn(eTopDec + 1, eBotDec)
                if (cc <= prev) return listOf(Seg(m.uri, order, effTop, effBot, renderedH, wasCut))
                dBounds.add(prev to cc)
                prev = cc
            }
            dBounds.add(prev to eBotDec)
            val sBounds = dBounds.map { (dTop, dBot) ->
                val sTop = (dTop.toDouble() / ks).roundToInt().coerceIn(effTop, effBot)
                var sBot = (dBot.toDouble() / ks).roundToInt().coerceIn(effTop, effBot)
                if (sBot <= sTop) sBot = min(effBot, sTop + 1)
                sTop to sBot
            }.toMutableList()
            // Jahit batas agar sBot[i] == sTop[i+1], ujung menutup penuh.
            for (i in sBounds.indices) {
                val (t, b) = sBounds[i]
                val nt = if (i == 0) effTop else sBounds[i - 1].second
                val nb = if (i == sBounds.lastIndex) effBot else b
                sBounds[i] = nt.coerceIn(effTop, effBot) to nb.coerceIn(effTop, effBot)
            }
            // Buang segmen degenerasi (tinggi 0) bila ada.
            val clean = sBounds.filter { (t, b) -> b > t }
            if (clean.isEmpty()) return listOf(Seg(m.uri, order, effTop, effBot, renderedH, wasCut))
            return clean.map { (sTop, sBot) ->
                val h = ((sBot - sTop).toLong() * stripWidth / m.width.coerceAtLeast(1)).toInt().coerceAtLeast(1)
                Seg(m.uri, order, sTop, sBot, h, wasCut)
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
     * v6: peta pasangan indeks-segmen berurutan yang bersambung piksel.
     * Hanya baris-baris SEAM yang dibandingkan (bawah-segmen-a vs
     * atas-segmen-b) via [SeamScan.seamContinues]. Segmen dari halaman yang
     * sama dilewati: urutannya sudah pasti bersambung. Pasangan
     * latar-datar-vs-datar TIDAK dihitung bersambung (margin putih bertemu
     * margin putih bukan alasan menggabung berkas).
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
            val bottom = renderer.edgePatch(a.uri, ma.width, ma.height, a.srcBottom - r, a.srcBottom) ?: continue
            val top = renderer.edgePatch(b.uri, mb.width, mb.height, b.srcTop, b.srcTop + r) ?: continue
            if (!SeamScan.hasContent(bottom.px) && !SeamScan.hasContent(top.px)) continue
            if (SeamScan.seamContinues(bottom.px, bottom.w, bottom.h, top.px, top.w, top.h)) {
                out.add(i to i + 1)
            }
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
        flagReason: String?,
        bannerCut: Boolean = false
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
            bytes = final.length(),
            bannerCut = bannerCut
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
