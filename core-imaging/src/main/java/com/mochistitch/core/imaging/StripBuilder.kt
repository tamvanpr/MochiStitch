package com.mochistitch.core.imaging

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.mochistitch.core.common.BannerPolicy
import com.mochistitch.core.settings.CutStrictness
import com.mochistitch.core.settings.SplitRule
import com.mochistitch.core.settings.StitchSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import kotlin.math.max
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
        val bannerCut: Boolean = false,
        /** Tepi atas segmen ini adalah hasil potongan terencana yang aman. */
        val cutTop: Boolean = false
    )

    /** Hasil rakitan + catatan banner (null = kebijakan banner tak dipakai). */
    data class BuildOutput(val strips: List<BuiltStrip>, val bannerNote: String? = null)

    suspend fun build(
        uris: List<Uri>,
        settings: StitchSettings,
        onProgress: (BuildPhase, Float) -> Unit = { _, _ -> },
        banner: BannerPolicy? = null,
        bannerTemplateBitmaps: List<Bitmap> = emptyList()
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

            // 0) Banner situs: buat BannerPolicy dari settings (hanya sumber
            // yang punya policy — saat ini baozimh) + ketegasan potong dari cfg.
            val policy = if (settings.enableBannerCut) {
                val p = when (settings.cutStrictness) {
                    com.mochistitch.core.settings.CutStrictness.LOOSE    -> Triple(4, 3, 0.97)
                    com.mochistitch.core.settings.CutStrictness.BALANCED -> Triple(8, 3, 0.97)
                    com.mochistitch.core.settings.CutStrictness.STRICT   -> Triple(16, 3, 0.97)
                }
                BannerPolicy(
                    stripPx = 200,
                    minPages = p.first.coerceAtLeast(2),
                    minFrac = p.third
                )
            } else null
            val bannerResult = bannerCrops(measured, policy, bannerTemplateBitmaps)
            val bannerCrops = bannerResult.crops

            // 1) Potong global: seluruh halaman dipindai pada resolusi kecil lalu
            // titik potong Direncanakan di atas profil gabungan, sehingga batas
            // antar-halaman tidak lagi menjadi potongan paksa.
            val globalCuts = if (wantCut) {
                planGlobalCuts(measured, bannerCrops, stripWidth, limit, settings)
            } else GlobalPlan(emptyMap(), emptySet(), 0, measured.size)
            val segs = mutableListOf<Seg>()
            measured.forEachIndexed { i, m ->
                val (cutTop, cutBot) = bannerCrops[i] ?: (0 to 0)
                val effTop = cutTop.coerceIn(0, m.height)
                val effBot = (m.height - cutBot).coerceIn(effTop + 1, m.height)
                segs.addAll(
                    segmentsFromCuts(
                        i, m, stripWidth, effTop, effBot, cutTop > 0 || cutBot > 0,
                        globalCuts.cuts[i].orEmpty()
                    )
                )
            }
            val forcedSeg = globalCuts.forcedPages

            // 2) Pasangan halaman berbeda yang bersambung piksel: tahan satu berkas.
            val byOrder = measured.mapIndexed { i, m -> i to m }.toMap()
            val linked = continuityMap(segs, byOrder)
            // Batas antar-berkas hanya boleh jatuh di tepi potongan
            // terencana (cutTop); tepi batas halaman asli ditahan/ditandai.
            val sheets = segs.mapIndexed { idx, s ->
                PageGrouper.Sheet(order = idx, renderedHeight = s.renderedH, safeBreak = s.cutTop)
            }
            // Batas keras: penahanan tepi tak-terencana tak boleh lebih dari 1,25x batas.
            val hardCap = limit + limit / 4
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
                val anyForced = bundle.sheets.any { sheet -> segs[sheet.order].order in forcedSeg }
                val flagged = bundle.tallSingle || bundle.seamCut || anyForced
                val reason = when {
                    bundle.tallSingle -> "Melebihi batas ${settings.maxStripHeight}px dan tak ada celah aman — dibiarkan utuh, tangani manual"
                    anyForced -> "Sebagian titik potong terpaksa paksa (tak ada celah polos di sekitar batas) — periksa manual"
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
            val planned = globalCuts.cuts.values.sumOf { it.size }
            val forcedPages = globalCuts.forcedPages.size
            val forcedBounds = bundles.count { it.seamCut }
            val cutNote = if (wantCut) {
                "Rencana potong: $planned titik ($forcedPages halaman paksa; " +
                    "pindai ${globalCuts.scannedPages}/${globalCuts.totalPages} halaman) · " +
                    "Berkas: ${bundles.size} (${forcedBounds} batas paksa)."
            } else {
                "Rencana potong: nonaktif (aturan ${settings.splitRule})."
            }
            val note = listOfNotNull(bannerResult.note, cutNote).joinToString(" ")
            Result.success(BuildOutput(strips, note))
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    /** Hasil gerbang banner: crop per indeks + catatan keputusan untuk user. */
    private data class BannerResult(val crops: Map<Int, Pair<Int, Int>>, val note: String?)

    /**
     * Banner -> crop (topCut, bottomCut) per indeks halaman, piksel asli.
     * Lapis template berjalan SELALU (tak butuh sourceId: cocok = banner).
     * Gerbang konsistensi butuh policy (asumsi tinggi + risiko header
     * komik yang berulang). Selalu ada catatan keputusan (diagnostik).
     */
    private fun bannerCrops(
        measured: List<StripRenderer.Measured>,
        policy: BannerPolicy?,
        templateBitmaps: List<Bitmap>
    ): BannerResult {
        if (measured.isEmpty()) return BannerResult(emptyMap(), null)
        if (policy == null && templateBitmaps.isEmpty()) return BannerResult(emptyMap(), null)
        val stripH = (policy?.stripPx ?: 200).coerceIn(1, 400)
        val minH = max(policy?.minPageH ?: 600, stripH * 2 + 100)
        val tallIdx = measured.indices.filter { i -> measured[i].height >= minH }
        if (tallIdx.isEmpty()) {
            return BannerResult(emptyMap(), "Banner: tidak ada halaman cukup tinggi — tidak dicek.")
        }
        fun strip(m: StripRenderer.Measured, top: Boolean): BannerGate.Strip? {
            val h = stripH.coerceIn(1, m.height)
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
        if (tops.isEmpty() && bots.isEmpty()) {
            return BannerResult(emptyMap(), "Banner: strip gagal dibaca — dilewati.")
        }
        // Lapis 1 — template OpenCV (per halaman); fallback NCC murni bila
        // native tak tersedia. Tanpa bitmap template, lapis ini dilewati.
        var viaTemplate = 0
        val strongTop = mutableSetOf<Int>()
        val strongBot = mutableSetOf<Int>()
        val medTop = mutableSetOf<Int>()
        val medBot = mutableSetOf<Int>()
        var ocvUsed = false
        if (templateBitmaps.isNotEmpty()) {
            if (BannerOcv.isAvailable()) {
                ocvUsed = true
                val tmpls = templateBitmaps.mapIndexedNotNull { idx, bmp ->
                    try {
                        BannerOcv.preprocessTemplate("t$idx", bmp)
                    } catch (t: Throwable) {
                        null
                    }
                }
                try {
                    for ((i, s) in tops) {
                        var bmp: Bitmap? = null
                        var region: BannerOcv.Region? = null
                        try {
                            bmp = BannerOcv.bitmapOf(s.px, s.w, s.h)
                            region = BannerOcv.preprocessRegion(bmp)
                            if (region == null) continue
                            val (hit, score, _) = BannerOcv.check(region, tmpls)
                            if (hit) strongTop.add(i)
                            else if (score >= BannerTemplate.MEDIUM) medTop.add(i)
                        } catch (t: Throwable) {
                            // Strip rusak: lewati, jangan gagalkan chapter.
                        } finally {
                            region?.let { BannerOcv.releaseRegion(it) }
                            bmp?.recycle()
                        }
                    }
                    for ((i, s) in bots) {
                        var bmp: Bitmap? = null
                        var region: BannerOcv.Region? = null
                        try {
                            bmp = BannerOcv.bitmapOf(s.px, s.w, s.h)
                            region = BannerOcv.preprocessRegion(bmp)
                            if (region == null) continue
                            val (hit, score, _) = BannerOcv.check(region, tmpls)
                            if (hit) strongBot.add(i)
                            else if (score >= BannerTemplate.MEDIUM) medBot.add(i)
                        } catch (t: Throwable) {
                        } finally {
                            region?.let { BannerOcv.releaseRegion(it) }
                            bmp?.recycle()
                        }
                    }
                } finally {
                    tmpls.forEach { BannerOcv.releaseTemplate(it) }
                }
            } else {
                // Fallback NCC murni: signature dari bitmap template.
                val sigs = templateBitmaps.mapNotNull { bmp ->
                    try {
                        if (bmp.width <= 0 || bmp.height <= 0) return@mapNotNull null
                        val px = IntArray(bmp.width * bmp.height)
                        bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
                        BannerTemplate.Sig(BannerTemplate.downscale(px, bmp.width, bmp.height))
                    } catch (t: Throwable) {
                        null
                    }
                }
                if (sigs.isNotEmpty()) {
                    for ((i, s) in tops) {
                        val g = BannerTemplate.downscale(s.px, s.w, s.h)
                        val sc = BannerTemplate.bestScore(g, sigs)
                        if (sc >= BannerTemplate.STRONG) strongTop.add(i)
                        else if (sc >= BannerTemplate.MEDIUM) medTop.add(i)
                    }
                    for ((i, s) in bots) {
                        val g = BannerTemplate.downscale(s.px, s.w, s.h)
                        val sc = BannerTemplate.bestScore(g, sigs)
                        if (sc >= BannerTemplate.STRONG) strongBot.add(i)
                        else if (sc >= BannerTemplate.MEDIUM) medBot.add(i)
                    }
                }
            }
        }
        // Lapis 2 — gerbang konsistensi (banner belum dikenal; butuh policy).
        val gateTop: Set<Int>
        val gateBot: Set<Int>
        if (policy != null && tops.isNotEmpty() && bots.isNotEmpty()) {
            val topStrips = tops.map { it.second }
            val botStrips = bots.map { it.second }
            val decision = BannerGate.decide(topStrips, botStrips, policy)
            val topIdx = tops.map { it.first }
            val botIdx = bots.map { it.first }
            gateTop = decision.top.mapNotNull { topIdx.getOrNull(it) }.toSet()
            gateBot = decision.bottom.mapNotNull { botIdx.getOrNull(it) }.toSet()
        } else {
            gateTop = emptySet()
            gateBot = emptySet()
        }
        // Komposisi: kuat-template langsung; medium-template + gate setuju.
        val out = mutableMapOf<Int, Pair<Int, Int>>()
        fun addTop(i: Int) {
            out[i] = stripH to (out[i]?.second ?: 0)
        }
        fun addBot(i: Int) {
            out[i] = (out[i]?.first ?: 0) to stripH
        }
        var viaGate = 0
        for (i in strongTop) {
            addTop(i)
            viaTemplate++
        }
        for (i in strongBot) {
            addBot(i)
            viaTemplate++
        }
        for (i in medTop) {
            if (i in gateTop && i !in strongTop) {
                addTop(i)
                viaGate++
            }
        }
        for (i in medBot) {
            if (i in gateBot && i !in strongBot) {
                addBot(i)
                viaGate++
            }
        }
        // Halaman yang template-nya lemah tapi gate mengelompokkannya penuh:
        // ikutkan seluruh kelompok gate (banner baru yang seragam).
        for (i in gateTop) {
            if (i !in strongTop && i !in medTop && tops.any { it.first == i }) {
                addTop(i)
                viaGate++
            }
        }
        for (i in gateBot) {
            if (i !in strongBot && i !in medBot && bots.any { it.first == i }) {
                addBot(i)
                viaGate++
            }
        }
        if (out.isEmpty()) {
            return BannerResult(
                emptyMap(),
                "Banner: tidak cocok template dan strip beda-beda (${tallIdx.size} dicek) — dilewati."
            )
        }
        val t = out.count { it.value.first > 0 }
        val b = out.count { it.value.second > 0 }
        val where = listOf(
            if (t > 0) "atas $t" else null,
            if (b > 0) "bawah $b" else null
        ).filterNotNull().joinToString(" + ")
        val how = listOf(
            if (viaTemplate > 0) "$viaTemplate ${if (ocvUsed) "template-opencv" else "template-ncc"}" else null,
            if (viaGate > 0) "$viaGate konsistensi" else null
        ).filterNotNull().joinToString(" + ")
        return BannerResult(out, "Banner: dicrop $where (${out.size} halaman via $how).")
    }

    /**
     * Rencana potong GLOBAL: pindai semua halaman pada lebar kecil, gabungkan
     * profil barisnya menjadi satu profil panjang, lalu rencanakan titik potong
     * di atas profil gabungan. Batas antar-halaman karena itu bukan lagi
     * potongan paksa — halaman 3 bawah dan halaman 4 atas diperlakukan
     * sebagai satu strip menyambung.
     *
     * Hasil: peta indeks halaman -> daftar y potong (koordinat sumber).
     */
    private data class GlobalPlan(
        val cuts: Map<Int, List<Int>>,
        val forcedPages: Set<Int>,
        val scannedPages: Int = 0,
        val totalPages: Int = 0
    )

    private fun planGlobalCuts(
        measured: List<StripRenderer.Measured>,
        bannerCrops: Map<Int, Pair<Int, Int>>,
        stripWidth: Int,
        limit: Int,
        settings: StitchSettings
    ): GlobalPlan {
        val scanWidth = SCAN_WIDTH
        val edge = when (settings.cutStrictness) {
            CutStrictness.LOOSE -> 30
            CutStrictness.BALANCED -> 24
            CutStrictness.STRICT -> 18
        }
        val range = when (settings.cutStrictness) {
            CutStrictness.LOOSE -> 52
            CutStrictness.BALANCED -> 40
            CutStrictness.STRICT -> 30
        }
        val margin = when (settings.cutStrictness) {
            CutStrictness.LOOSE -> 6
            CutStrictness.BALANCED -> 10
            CutStrictness.STRICT -> 14
        }
        val busy = ArrayList<Boolean>()
        val ink = ArrayList<Int>()
        val keep = ArrayList<Boolean>()
        val structAll = ArrayList<Boolean>()
        val offsets = IntArray(measured.size)
        val windows = arrayOfNulls<Window>(measured.size)
        var scanned = 0
        for ((i, m) in measured.withIndex()) {
            offsets[i] = busy.size
            val small = decodeScan(m.uri, scanWidth) ?: continue
            scanned++
            val profile: RowProfile
            val keepRows: BooleanArray
            try {
                profile = RowScanner.scan(small, edgeThreshold = edge, rangeThreshold = range)
                keepRows = keepOutRows(small)
            } finally {
                try { small.recycle() } catch (t: Throwable) { }
            }
            val (cutTop, cutBot) = bannerCrops[i] ?: (0 to 0)
            val effTop = cutTop.coerceIn(0, m.height)
            val effBot = (m.height - cutBot).coerceIn(effTop + 1, m.height)
            val sh = profile.busy.size
            val sTop = (effTop.toDouble() * sh / m.height).toInt().coerceIn(0, sh - 1)
            val sBot = (effBot.toDouble() * sh / m.height).toInt().coerceIn(sTop + 1, sh)
            windows[i] = Window(m, effTop, effBot, sTop, sBot, sh)
            for (y in sTop until sBot) {
                busy.add(profile.busy[y] || keepRows.getOrElse(y) { false })
                ink.add(profile.ink[y])
                keep.add(keepRows.getOrElse(y) { false })
                structAll.add(profile.structured.getOrElse(y) { false })
            }
        }
        if (busy.isEmpty()) return GlobalPlan(emptyMap(), emptySet(), scanned, measured.size)
        // Zona teks global: kelompok baris terstruktur (kunci pada teks,
        // bukan garis pinggir) + perluasan dinding balon. OR ke busy saja
        // (bukan ke larangan-batal): baris bebas di zona perluasan masih
        // boleh dipotong darurat + ditandai; yang DIBATALKAN hanya
        // potongan paksa tepat di interior terkurung (keepAll).
        val zones = KeepOut.textZones(structAll.toBooleanArray())
        val busyArr = busy.toBooleanArray()
        for (y in busyArr.indices) {
            if (zones[y]) busyArr[y] = true
        }
        val combined = RowProfile(busyArr, ink.toIntArray())
        val keepAll = keep.toBooleanArray()
        val maxLen = (limit.toLong() * scanWidth / stripWidth.coerceAtLeast(1)).toInt().coerceAtLeast(16)
        val plan = CutPlanner.plan(
            profile = combined,
            maxLen = maxLen,
            margin = margin,
            overshoot = (maxLen / 4).coerceAtLeast(8)
        )
        if (plan.isEmpty()) return GlobalPlan(emptyMap(), emptySet(), scanned, measured.size)
        val out = LinkedHashMap<Int, MutableList<Int>>()
        val forced = LinkedHashSet<Int>()
        for (cut in plan) {
            val y = cut.y.coerceIn(0, combined.busy.size - 1)
            val idx = pageIndexAt(offsets, y)
            val w = windows.getOrNull(idx) ?: continue
            // y adalah koordinat GLOBAL profil gabungan; ubah ke lokal
            // halaman dulu sebelum dibandingkan ke jendela pindai.
            val ly = y - offsets[idx]
            if (ly < w.scanTop || ly >= w.scanBot) continue
            // Potongan paksa yang jatuh di zona larangan (dalam balon)
            // DIBATALKAN: halaman dibiarkan utuh/kelebihan tinggi dan
            // ditandai, daripada balon terpotong.
            if (cut.forced && keepAll[y]) {
                forced.add(idx)
                continue
            }
            val local = ly - w.scanTop
            val srcY = (w.effTop + local.toDouble() * (w.effBot - w.effTop) / (w.scanBot - w.scanTop)).toInt()
            val (finalY, badVerify) = verifyCut(measured[idx], srcY, edge, range)
            val list = out.getOrPut(idx) { mutableListOf() }
            val minGap = (64L * (w.effBot - w.effTop) / (w.scanBot - w.scanTop)).toInt().coerceAtLeast(1)
            val last = list.lastOrNull() ?: w.effTop
            if (finalY - last < minGap) continue
            if (finalY >= w.effBot - 1) continue
            list.add(finalY)
            if (cut.forced || badVerify) forced.add(idx)
        }
        return GlobalPlan(out, forced, scanned, measured.size)
    }

    /**
     * Zona larangan (keep-out) skala pindai: daerah terang terkurung =
     * bagian dalam balon. Dihitung pada salinan kecil (~240px) lalu
     * dipetakan ke tinggi profil pindai.
     */
    private fun keepOutRows(small: Bitmap): BooleanArray {
        val sh = small.height
        val none = BooleanArray(sh)
        if (small.width <= 0 || sh <= 0) return none
        return try {
            val kw = 240
            val kh = (sh.toLong() * kw / small.width.coerceAtLeast(1)).toInt().coerceAtLeast(1)
            val tiny = Bitmap.createScaledBitmap(small, kw, kh, false)
            try {
                val px = IntArray(kw * kh)
                tiny.getPixels(px, 0, kw, 0, 0, kw, kh)
                val rows = KeepOut.enclosed(px, kw, kh)
                BooleanArray(sh) { y -> rows.getOrElse((y.toLong() * kh / sh).toInt()) { false } }
            } finally {
                try { if (tiny !== small) tiny.recycle() } catch (t: Throwable) { }
            }
        } catch (t: Throwable) {
            none
        }
    }

    private class Window(
        val measured: StripRenderer.Measured,
        val effTop: Int,
        val effBot: Int,
        val scanTop: Int,
        val scanBot: Int,
        val scanHeight: Int
    )

    private fun pageIndexAt(offsets: IntArray, y: Int): Int {
        var idx = 0
        for (i in offsets.indices) if (offsets[i] <= y) idx = i
        return idx
    }

    /** Decode pindai (lebar ~[SCAN_WIDTH], tanpa filter agar garis tipis awet). */
    private fun decodeScan(uri: Uri, targetWidth: Int): Bitmap? {
        decodeScanDirect(uri, targetWidth)?.let { return it }
        return decodeScanSampled(uri, targetWidth)
    }

    private fun decodeScanDirect(uri: Uri, targetWidth: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetWidth) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val raw = openStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        if (raw.width <= 0) {
            try { raw.recycle() } catch (t: Throwable) { }
            return null
        }
        if (raw.width == targetWidth) return raw
        val h = (raw.height.toLong() * targetWidth / raw.width).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(raw, targetWidth, h, false)
        if (scaled !== raw) {
            try { raw.recycle() } catch (t: Throwable) { }
        }
        return scaled
    }

    /**
     * Cadangan bila decode langsung gagal: pakai decodeSampled teruji
     * (dipakai juga oleh banner + render) lalu skala ke lebar pindai.
     */
    private fun decodeScanSampled(uri: Uri, targetWidth: Int): Bitmap? {
        val raw = try {
            renderer.decodeSampled(uri, maxPixels = 4_000_000L)
        } catch (t: Throwable) {
            null
        } ?: return null
        if (raw.width <= 0 || raw.height <= 0) {
            try { raw.recycle() } catch (t: Throwable) { }
            return null
        }
        if (raw.width == targetWidth) return raw
        return try {
            val h = (raw.height.toLong() * targetWidth / raw.width).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(raw, targetWidth, h, false)
            if (scaled !== raw) {
                try { raw.recycle() } catch (t: Throwable) { }
            }
            scaled
        } catch (t: Throwable) {
            try { raw.recycle() } catch (t: Throwable) { }
            null
        }
    }

    /**
     * Verifikasi resolusi-penuh satu titik potong: pindaian kecil bisa
     * meloloskan garis tipis (ekor balon). Bila baris potong ternyata
     * sibuk, geser ke baris bebas terdekat (±48px); bila tak ada,
     * pertahankan posisi dan tandai gagal verifikasi.
     */
    private fun verifyCut(
        m: StripRenderer.Measured,
        cutY: Int,
        edge: Int,
        range: Int
    ): Pair<Int, Boolean> {
        val half = 64
        val top = (cutY - half).coerceAtLeast(0)
        val bottom = (cutY + half).coerceAtMost(m.height)
        if (bottom - top < 8) return cutY to false
        val patch = try {
            renderer.edgePatch(m.uri, m.width, m.height, top, bottom)
        } catch (t: Throwable) {
            null
        } ?: return cutY to false
        val prof = RowScanner.scanBuffer(patch.px, patch.w, patch.h, edge, range, noisePixels = 3)
        val center = (cutY - top).coerceIn(0, patch.h - 1)
        if (!prof.busy[center]) return cutY to false
        // Geser ke baris bebas terdekat, tapi wajib ada zona bersih
        // ±VERIFY_GUARD di sekitarnya (jangan mendarat di sebelah tinta).
        val radius = 48
        for (d in 1..radius) {
            val dn = center - d
            if (dn - VERIFY_GUARD >= 0 && (dn - VERIFY_GUARD..dn + VERIFY_GUARD).all { !prof.busy[it] }) {
                return (top + dn) to false
            }
            val up = center + d
            if (up + VERIFY_GUARD < patch.h && (up - VERIFY_GUARD..up + VERIFY_GUARD).all { !prof.busy[it] }) {
                return (top + up) to false
            }
        }
        return cutY to true
    }

    /**
     * Bagi satu halaman pada titik potong hasil rencana global. Partisi
     * dijamin eksak: sTop[0]=effTop, sBot[last]=effBot, sBot[i]=sTop[i+1].
     */
    private fun segmentsFromCuts(
        order: Int,
        m: StripRenderer.Measured,
        stripWidth: Int,
        effTop: Int,
        effBot: Int,
        wasCut: Boolean,
        cuts: List<Int>
    ): List<Seg> {
        fun seg(top: Int, bottom: Int, cutTop: Boolean): Seg {
            val h = ((bottom - top).toLong() * stripWidth / m.width.coerceAtLeast(1)).toInt().coerceAtLeast(1)
            return Seg(m.uri, order, top, bottom, h, wasCut, cutTop)
        }
        if (cuts.isEmpty()) return listOf(seg(effTop, effBot, false))
        val bounds = mutableListOf<Pair<Int, Int>>()
        var prev = effTop
        for (c in cuts) {
            val cc = c.coerceIn(effTop + 1, effBot)
            if (cc <= prev) continue
            bounds.add(prev to cc)
            prev = cc
        }
        bounds.add(prev to effBot)
        val clean = bounds.filter { it.second > it.first }
        if (clean.isEmpty()) return listOf(seg(effTop, effBot, false))
        return clean.mapIndexed { idx, (t, b) -> seg(t, b, idx > 0) }
    }

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
            } else if (touchesEdge(bottom, top = false) || touchesEdge(top, top = true)) {
                // Struktur tinta tegak menyentuh batas halaman (garis balon /
                // teks terbelah antar-halaman): tahan satu berkas.
                out.add(i to i + 1)
            }
        }
        return out
    }

    /**
     * True bila ada goresan tinta TEGAK yang menyentuh tepi strip (16 baris
     * tepi): kolom dengan run gelap vertikal >= 10px. Screentone (titik
     * 2-4px) tidak lolos; garis balon/teks yang terpotong tepi lolos.
     */
    private fun touchesEdge(p: StripRenderer.EdgePatch, top: Boolean): Boolean {
        if (p.w <= 0 || p.h < 16) return false
        val stride = (p.w / 200).coerceAtLeast(1)
        var count = 0
        for (x in 0 until p.w step stride) count++
        val sample = IntArray(count)
        var n = 0
        for (x in 0 until p.w step stride) sample[n++] = lumaOf(p.px[(p.h / 2) * p.w + x])
        sample.sort()
        val bg = sample[n / 2]
        val rows = 16
        for (x in 0 until p.w step 2) {
            var run = 0
            for (r in 0 until rows) {
                val y = if (top) r else p.h - 1 - r
                val l = lumaOf(p.px[y * p.w + x])
                if (l < bg - EDGE_TOUCH_DARK || l > bg + EDGE_TOUCH_DARK) {
                    run++
                    if (run >= EDGE_TOUCH_RUN) return true
                } else {
                    run = 0
                }
            }
        }
        return false
    }

    private fun lumaOf(c: Int): Int =
        (((c shr 16) and 0xFF) * 77 + ((c shr 8) and 0xFF) * 150 + (c and 0xFF) * 29) shr 8

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
        const val SCAN_WIDTH = 480
        const val VERIFY_GUARD = 8
        const val EDGE_TOUCH_DARK = 28
        const val EDGE_TOUCH_RUN = 10
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
