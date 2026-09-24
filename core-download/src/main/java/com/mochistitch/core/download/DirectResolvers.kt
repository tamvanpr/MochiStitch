package com.mochistitch.core.download

// ── Konstanta porting worker (header + kredensial app baozimh) ──────────

const val UA_WIN_DIRECT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
const val UA_ANDROID11_DIRECT =
    "Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"

const val BAOZI_APP_ID = "cn.sts.xiaoyun.ordermeals"
const val BAOZI_DEVICE_CODE = "6ca052067aa9833084daaa6ffeba0913"
const val BAOZI_DEVICE_ID = "RKQ1.201217.002"
const val BAOZI_UA = "baozimh_android/1.0.31/gb/adset"
const val BAOZI_APP_VERSION = "1.0.31"

val COMICK_APIS = listOf("https://api.comick.dev", "https://api.comick.io", "https://api.comick.fun")
const val MANGADEX_API = "https://api.mangadex.org"

/** Mode langsung: resolve chapter/series tanpa worker (porting worker JS). */
class DirectDownloadApi(val source: SourceDef) : DownloadApi {
    override suspend fun chapters(seriesUrl: String, referer: String): List<ChapterHit> =
        DirectResolvers.chapters(source, seriesUrl)

    override suspend fun pages(chapterUrl: String, referer: String): ChapterPages =
        DirectResolvers.pages(source, chapterUrl)
}

/**
 * Resolver langsung per sumber. Tiap sumber = pasangan fungsi:
 * parse MURNI (HTML atau JSON menjadi data, publik agar unit-testable
 * tanpa jaringan) + fetch privat yang mengambil lalu memanggil parse.
 */
object DirectResolvers {

    fun chapters(source: SourceDef, url: String): List<ChapterHit> = when (source.id) {
        "wmanhua" -> wmanhuaSeries(url)
        "jjabtoon" -> jjabtoonSeries(url)
        "jjaptoon" -> jjaptoonSeries(url)
        "koudaimh" -> koudaimhSeries(url)
        "goodtoon" -> goodtoonSeries(url)
        "manwa" -> manwaSeries(url)
        "mangadex" -> mangadexSeries(url)
        "mangapill" -> mangapillSeries(url)
        "comick" -> comickSeries(url)
        "mangageko" -> mangagekoSeries(url)
        "demonic" -> demonicSeries(url)
        "likemanga" -> likemangaSeries(url)
        "mangabats" -> mangabatsSeries(url)
        "xcomic" -> xcomicSeries(url)
        else -> throw RawApiException("Series langsung tak didukung: ${source.id} (pakai worker).")
    }

    fun pages(source: SourceDef, url: String): ChapterPages = when (source.id) {
        "baozimh" -> baozimhPages(url)
        "wmanhua" -> wmanhuaPages(url)
        "jjabtoon" -> jjabtoonPages(url)
        "jjaptoon" -> jjaptoonPages(url)
        "koudaimh" -> koudaimhPages(url)
        "goodtoon" -> goodtoonPages(url)
        "manwa" -> manwaPages(url)
        "mangadex" -> mangadexPages(url)
        "mangapill" -> mangapillPages(url)
        "comick" -> comickPages(url)
        "mangageko" -> mangagekoPages(url)
        "demonic" -> demonicPages(url)
        "likemanga" -> likemangaPages(url)
        "mangabats" -> mangabatsPages(url)
        "xcomic" -> xcomicPages(url)
        else -> throw RawApiException("Sumber langsung tak dikenal: ${source.id}")
    }

    // ── Util ────────────────────────────────────────────────────────

    fun originOf(url: String): String = try {
        val u = java.net.URL(url)
        "${u.protocol}://${u.host}"
    } catch (e: Exception) {
        url
    }

    fun absolutize(raw: String, origin: String): String? {
        var u = raw.replace("\\/", "/").replace("&amp;", "&").replace("\\u0026", "&").trim()
        if (u.isBlank()) return null
        u = when {
            u.startsWith("http://") || u.startsWith("https://") -> u
            u.startsWith("//") -> "https:$u"
            u.startsWith("/") -> origin.trimEnd('/') + u
            else -> return null
        }
        return if (u.startsWith("http://") || u.startsWith("https://")) u else null
    }

    fun titleOf(html: String): String =
        Regex("<title>([^<]+)</title>", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)?.trim() ?: ""

    fun splitTitle(pageTitle: String): Pair<String, String> {
        val parts = pageTitle.split(" - ")
        return if (parts.size >= 2) parts[0].trim() to parts.drop(1).joinToString(" - ").trim()
        else "" to pageTitle
    }

    // ── ID: baozimh ─────────────────────────────────────────────────

    fun baoziHeaders(): Map<String, String> = mapOf(
        "Referer" to "https://appgb.baozimh.com/",
        "app-id" to BAOZI_APP_ID,
        "device-code" to BAOZI_DEVICE_CODE,
        "device-id" to BAOZI_DEVICE_ID,
        "user-agent" to BAOZI_UA,
        "app-version" to BAOZI_APP_VERSION,
        "Accept-Encoding" to "gzip",
        "Connection" to "Keep-Alive"
    )

    private fun baozimhPages(url: String): ChapterPages {
        val m = Regex("""(?:twmanga\.com|baozimh\.com)/(?:comic/chapter|baozimhapp/comic/chapter)/([^/]+)/([^/?#]+)\.html""")
            .find(url) ?: throw RawApiException("URL baozimh tidak valid.")
        val (slug, file) = m.destructured
        // Urutan worker: baozimh.com dulu (sertifikat bzmgapp rusak).
        val hosts = listOf("baozimh.com", "bzmgapp.com").flatMap { d -> (1..3).map { n -> "appgb$n.$d" } }
        var lastErr: Exception? = null
        var html: String? = null
        for (h in hosts) {
            try {
                html = DirectHttp.getText("https://$h/baozimhapp/comic/chapter/$slug/$file.html", baoziHeaders())
                break
            } catch (e: Exception) {
                lastErr = e as? Exception ?: Exception("gagal")
            }
        }
        return parseBaozimhChapter(html ?: throw RawApiException("Semua host app Baozi gagal: ${lastErr?.message}"))
    }

    fun parseBaozimhChapter(html: String): ChapterPages {
        val pageTitle = titleOf(html)
        val tParts = pageTitle.split(" - ")
        val tags = Regex("""<img\b[^>]*\bclass="comic-contain__item"[^>]*>""", RegexOption.IGNORE_CASE)
            .findAll(html).map { it.value }.toList()
        val images = tags.mapNotNull { tag ->
            val raw = Regex("""data-src="([^"]+)"""", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(1) ?: return@mapNotNull null
            val idx = Regex("""data-index="(\d+)"""", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(1)?.toIntOrNull()
            val w = Regex("""data-w="(\d+)"""", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val h = Regex("""data-h="(\d+)"""", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            Triple(idx, rewriteBzcdnUrl(raw), w to h)
        }.sortedBy { it.first ?: Int.MAX_VALUE }
            .mapIndexed { i, (_, src, wh) -> PageRef(page = i + 1, url = src, width = wh.first, height = wh.second) }
        if (images.isEmpty()) throw RawApiException("Tidak ada gambar di chapter baozimh (markup berubah?).")
        return finishPages("baozimh", tParts.getOrElse(1) { "" }.trim(), tParts.getOrElse(0) { "" }.trim(), images)
    }

    /**
     * Gambar s.baozicdn.com/s1.baozicdn.com (dan bzcdn.net) ber-watermark;
     * static-tw.baozimh.com melayani path yang sama TANPA watermark.
     * Porting rewriteBzcdnUrl frontend.
     */
    fun rewriteBzcdnUrl(url: String): String {
        val m = Regex("""^https?://[\w-]+\.(?:baozicdn\.com|bzcdn\.net)/(.+)$""").find(url.trim())
        return if (m != null) "https://static-tw.baozimh.com/${m.groupValues[1]}" else url
    }

    // ── ID: wmanhua ─────────────────────────────────────────────────

    private fun wmanhuaPages(url: String): ChapterPages {
        val html = DirectHttp.getText(
            url,
            DirectHttp.htmlHeaders("https://www.wmanhua.com/", lang = "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7")
        )
        return parseWmanhuaChapter(html)
    }

    fun parseWmanhuaChapter(html: String): ChapterPages {
        val num = Regex("""var\s+num\s*=\s*eval\(\s*["'](\d+)["']\s*\)""").find(html)?.groupValues?.get(1)?.toIntOrNull()
            ?: throw RawApiException("Variabel num wmanhua tidak ketemu (markup berubah?).")
        var base = Regex("""var\s+pasd\s*=\s*["']([^"']+)["']""").find(html)?.groupValues?.get(1)
            ?: throw RawApiException("Variabel pasd wmanhua tidak ketemu.")
        if (!base.endsWith("/")) base += "/"
        if (num <= 0) throw RawApiException("Jumlah halaman wmanhua tidak valid: $num")
        val rawTitle = (Regex("""<title>\s*([\s\S]*?)\s*</title>""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
            ?: "").replace(Regex("""\s*\|\s*W漫画\s*$""", RegexOption.IGNORE_CASE), "").replace(Regex("""\s+"""), " ").trim()
        val first = rawTitle.split(" - ").getOrElse(0) { "" }.trim()
        val sp = first.indexOf(" ")
        val (comic, chap) = if (sp > -1) first.substring(sp + 1).trim() to first.substring(0, sp).trim() else "" to first
        return finishPages("wmanhua", comic, chap, (1..num).map { i -> PageRef(page = i, url = "$base$i.webp") })
    }

    private fun wmanhuaSeries(url: String): List<ChapterHit> {
        val comicId = Regex("""wmanhua\.com/comic/(\d+)\.html""", RegexOption.IGNORE_CASE).find(url)?.groupValues?.get(1)
            ?: throw RawApiException("URL series wmanhua tidak valid.")
        val body = DirectHttp.postJson(
            "https://www.wmanhua.com/comic/$comicId",
            mapOf(
                "User-Agent" to DIRECT_UA_MOBILE,
                "Content-Type" to "application/json",
                "Accept" to "application/json, text/plain, */*",
                "Referer" to url
            ),
            "{}"
        )
        return parseWmanhuaSeries(body)
    }

    fun parseWmanhuaSeries(body: String): List<ChapterHit> {
        val root = RawJson.parse(body)
        if (root.int("code", -1) != 0) throw RawApiException("API series wmanhua menolak (code ${root.int("code", -1)}).")
        val arr = root.asObj()?.get("data")?.arr("chapters")
            ?: throw RawApiException("Respons series wmanhua berubah bentuk.")
        return arr.mapNotNull { item ->
            val id = item.int("id")
            val contentId = item.int("contentId")
            val name = item.str("chapterName")
            if (id == 0 || contentId == 0) null
            else ChapterHit(id = id.toString(), title = name.ifBlank { "Chapter $id" }, url = "https://www.wmanhua.com/chapter/$contentId-$id.html")
        }
    }

    // ── ID: jjabtoon (JSON API) ─────────────────────────────────────

    fun jjabtoonApiHeaders(origin: String): Map<String, String> = mapOf(
        "Accept" to "application/json, text/plain, */*",
        "User-Agent" to UA_WIN_DIRECT,
        "Referer" to "$origin/"
    )

    private fun jjabtoonPages(url: String): ChapterPages {
        val epId = Regex("""jjabtoon[^/]*/episodes/(\d+)""").find(url)?.groupValues?.get(1)
            ?: throw RawApiException("URL episode jjabtoon tidak valid.")
        val origin = originOf(url)
        val candidates = listOf("$origin/api/episodes/$epId", "https://jjabtoon001.com/api/episodes/$epId")
        var lastErr: Exception? = null
        var body: String? = null
        for (api in candidates) {
            try {
                body = DirectHttp.getText(api, jjabtoonApiHeaders(originOf(api)), 12_000)
                // Validasi cepat bentuk respons sebelum dipakai.
                parseJjabtoonChapter(body)
                break
            } catch (e: Exception) {
                lastErr = e
                body = null
            }
        }
        return parseJjabtoonChapter(body ?: throw RawApiException("Episode jjabtoon gagal: ${lastErr?.message}"))
    }

    fun parseJjabtoonChapter(body: String): ChapterPages {
        val root = RawJson.parse(body)
        val d = root.asObj()?.get("data")?.asObj()
        if (!root.bool("success") || d == null) throw RawApiException("Respons episode jjabtoon invalid.")
        val title = d["title"]?.asStr()
            ?: ((d["episodeNo"] as? JVal.Num)?.v?.toInt()?.let { "${it}화" } ?: "Episode")
        val images = (d["images"] as? JVal.Arr)?.items.orEmpty()
            .mapNotNull { item ->
                val u = item.str("url")
                if (u.isBlank()) null else ((item.asObj()?.get("sortOrder") as? JVal.Num)?.v?.toInt() to u)
            }
            .sortedBy { it.first ?: Int.MAX_VALUE }
            .mapIndexed { i, (_, u) -> PageRef(page = i + 1, url = u) }
        if (images.isEmpty()) throw RawApiException("Episode jjabtoon kosong (API berubah?).")
        return finishPages("jjabtoon", "", title, images)
    }

    private fun jjabtoonSeries(url: String): List<ChapterHit> {
        val webtoonId = Regex("""jjabtoon[^/]*/webtoons/(\d+)""").find(url)?.groupValues?.get(1)
            ?: throw RawApiException("URL series jjabtoon tidak valid.")
        val origin = originOf(url)
        val meta = DirectHttp.getText("$origin/api/webtoons/$webtoonId", jjabtoonApiHeaders(origin))
        val eps = DirectHttp.getText("$origin/api/webtoons/$webtoonId/episodes", jjabtoonApiHeaders(origin))
        return parseJjabtoonSeries(meta, eps, origin)
    }

    fun parseJjabtoonSeries(metaBody: String, epsBody: String, origin: String): List<ChapterHit> {
        RawJson.parse(metaBody).asObj()?.get("data")?.asObj()
            ?: throw RawApiException("Metadata jjabtoon berubah bentuk.")
        val arr = (RawJson.parse(epsBody).asObj()?.get("data") as? JVal.Arr)?.items
            ?: throw RawApiException("Daftar episode jjabtoon berubah bentuk.")
        return arr.mapNotNull { item ->
            val id = item.str("id")
            if (id.isBlank()) null
            else {
                val epNo = (item.asObj()?.get("episodeNo") as? JVal.Num)?.v?.toInt()
                ChapterHit(id = id, title = item.str("title").ifBlank { epNo?.let { "${it}화" } ?: "Episode $id" }, url = "$origin/episodes/$id")
            }
        }
    }

    // ── ID: jjaptoon (HTML) ─────────────────────────────────────────

    fun jjaptoonHtmlHeaders(pageUrl: String): Map<String, String> = mapOf(
        "User-Agent" to UA_WIN_DIRECT,
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language" to "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7",
        "Referer" to originOf(pageUrl) + "/"
    )

    private fun jjaptoonPages(url: String): ChapterPages {
        val chId = Regex("""jjaptoon[^/]*/chapters/(\d+)""").find(url)?.groupValues?.get(1)
            ?: throw RawApiException("URL chapter jjaptoon tidak valid.")
        val candidates = listOf(url, "https://www.jjaptoon008.com/chapters/$chId", "https://jjaptoon008.com/chapters/$chId")
        var lastErr: Exception? = null
        var html: String? = null
        for (u in candidates) {
            try {
                html = DirectHttp.getText(u, jjaptoonHtmlHeaders(u), 12_000)
                break
            } catch (e: Exception) {
                lastErr = e
            }
        }
        return parseJjaptoonChapter(html ?: throw RawApiException("Chapter jjaptoon gagal: ${lastErr?.message}"))
    }

    fun parseJjaptoonChapter(html: String): ChapterPages {
        val (comic, chap) = splitTitle(titleOf(html))
        var idx = 0
        val images = Regex("""<img\b[^>]*>""", RegexOption.IGNORE_CASE).findAll(html).mapNotNull { tagM ->
            val tag = tagM.value
            val src = Regex("""\bsrc="([^"]+)"""", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(1) ?: return@mapNotNull null
            val alt = Regex("""\balt="([^"]*)"""", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(1) ?: ""
            if (!Regex("""\s\d+$""").containsMatchIn(alt.trim())) return@mapNotNull null
            PageRef(page = ++idx, url = src)
        }.toList()
        if (images.isEmpty()) throw RawApiException("Tidak ada gambar jjaptoon (markup berubah?).")
        return finishPages("jjaptoon", comic, chap, images)
    }

    private fun jjaptoonSeries(url: String): List<ChapterHit> {
        Regex("""jjaptoon[^/]*/comics/(\d+)""").find(url)
            ?: throw RawApiException("URL series jjaptoon tidak valid.")
        return parseJjaptoonSeries(DirectHttp.getText(url, jjaptoonHtmlHeaders(url)), originOf(url))
    }

    fun parseJjaptoonSeries(html: String, origin: String): List<ChapterHit> {
        val re = Regex("""href="/chapters/(\d+)"[^>]*data-chapter-list-id="\d+"[\s\S]*?<p class="truncate text-sm font-black text-zinc-100">([^<]+)</p>""")
        val out = re.findAll(html).map { m ->
            ChapterHit(id = m.groupValues[1], title = m.groupValues[2].trim(), url = "$origin/chapters/${m.groupValues[1]}")
        }.toList()
        if (out.isEmpty()) throw RawApiException("Daftar chapter jjaptoon kosong (markup berubah?).")
        return out
    }

    // ── ID: goodtoon (Madara) ───────────────────────────────────────

    private fun goodtoonPages(url: String): ChapterPages {
        Regex("""goodtoon[^/]*/manga/([^/?#]+)/(?:chapter-)?(\d+)/?(?:[?#].*)?$""").find(url)
            ?: throw RawApiException("URL chapter goodtoon tidak valid.")
        return parseGoodtoonChapter(DirectHttp.getText(url, jjaptoonHtmlHeaders(url)))
    }

    fun parseGoodtoonChapter(html: String): ChapterPages {
        val (comic, chap) = splitTitle(titleOf(html))
        val tags = Regex("""<img\b[^>]*class="[^"]*\bwp-manga-chapter-img\b[^"]*"[^>]*>""", RegexOption.IGNORE_CASE)
            .findAll(html).map { it.value }.toList()
        val images = tags.mapNotNull { tag ->
            Regex("""\bdata-src="([^"]+)"""", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(1)
        }.mapIndexed { i, u -> PageRef(page = i + 1, url = u) }
        if (images.isEmpty()) throw RawApiException("Tidak ada gambar goodtoon (lazyload berubah?).")
        return finishPages("goodtoon", comic, chap, images)
    }

    private fun goodtoonSeries(url: String): List<ChapterHit> {
        val slug = Regex("""goodtoon[^/]*/manga/([^/?#]+)/?(?:[?#].*)?$""").find(url)?.groupValues?.get(1)
            ?: throw RawApiException("URL series goodtoon tidak valid.")
        val origin = originOf(url)
        val frag = DirectHttp.postJson(
            "$origin/manga/$slug/ajax/chapters/?t=1",
            mapOf(
                "Accept" to "*/*",
                "X-Requested-With" to "XMLHttpRequest",
                "User-Agent" to UA_WIN_DIRECT,
                "Referer" to url
            ),
            ""
        )
        return parseGoodtoonSeries(frag, origin)
    }

    fun parseGoodtoonSeries(frag: String, origin: String): List<ChapterHit> {
        val out = mutableListOf<ChapterHit>()
        Regex("""<a href="([^"]+)">\s*(?:<span class="up-badge-inline">UP</span>)?([^<]*)</a>""").findAll(frag).forEach { m ->
            val href = m.groupValues[1]
            val id = Regex("""/(?:chapter-)?(\d+)/?(?:[?#].*)?$""").find(href)?.groupValues?.get(1) ?: return@forEach
            val abs = absolutize(href, origin) ?: return@forEach
            out.add(ChapterHit(id = id, title = m.groupValues[2].trim().ifBlank { "Chapter $id" }, url = abs))
        }
        if (out.isEmpty()) throw RawApiException("Daftar chapter goodtoon kosong (ajax berubah?).")
        return out
    }

    // ── ID: manwa (HTML + gambar AES) ───────────────────────────────

    fun manwaHeaders(): Map<String, String> = mapOf(
        "User-Agent" to UA_ANDROID11_DIRECT,
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Referer" to "https://manwa.me/"
    )

    private fun manwaPages(url: String): ChapterPages {
        Regex("""manwa\.me/chapter/(\d+)""").find(url)
            ?: throw RawApiException("URL chapter manwa tidak valid.")
        return parseManwaChapter(DirectHttp.getText(url, manwaHeaders()))
    }

    fun parseManwaChapter(html: String): ChapterPages {
        val (comic, chap) = splitTitle(titleOf(html))
        val tags = Regex("""<img\b[^>]*\bclass="[^"]*content-img[^"]*lazy_img[^"]*"[^>]*>""", RegexOption.IGNORE_CASE)
            .findAll(html).map { it.value }.toList()
        val images = tags.mapNotNull { tag ->
            Regex("""\bdata-r-src="([^"]+)"""", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(1)
        }.mapIndexed { i, u -> PageRef(page = i + 1, url = u) }
        if (images.isEmpty()) throw RawApiException("Tidak ada gambar manwa (markup berubah?).")
        return finishPages("manwa", comic, chap, images)
    }

    private fun manwaSeries(url: String): List<ChapterHit> {
        Regex("""manwa\.me/book/(\d+)""").find(url)
            ?: throw RawApiException("URL series manwa tidak valid.")
        return parseManwaSeries(DirectHttp.getText(url, manwaHeaders()))
    }

    fun parseManwaSeries(html: String): List<ChapterHit> {
        val out = mutableListOf<ChapterHit>()
        Regex("""<a href="/chapter/(\d+)" title="([^"]+)"\s*class="chapteritem\s*">""").findAll(html).forEach { m ->
            out.add(ChapterHit(id = m.groupValues[1], title = m.groupValues[2].trim(), url = "https://manwa.me/chapter/${m.groupValues[1]}"))
        }
        out.reverse()
        if (out.isEmpty()) throw RawApiException("Daftar chapter manwa kosong.")
        return out
    }

    // ── ID: koudaimh (params AES) ───────────────────────────────────

    fun koudaimhHeaders(): Map<String, String> = DirectHttp.htmlHeaders("https://m.koudaimh.com/")

    private fun koudaimhPages(url: String): ChapterPages {
        Regex("""koudaimh\.com/manhua/([^/]+)/(\d+)\.html""").find(url)
            ?: throw RawApiException("URL chapter koudaimh tidak valid.")
        val html = DirectHttp.getText(url, koudaimhHeaders())
        val blob = Regex("""params\s*=\s*['"]([^'"]+)""").find(html)?.groupValues?.get(1)
            ?: throw RawApiException("Blob params koudaimh tidak ketemu (markup berubah?).")
        return parseKoudaimhChapter(RawCrypto.decryptKoudaimhParams(blob), originOf(url))
    }

    fun parseKoudaimhChapter(json: String, origin: String): ChapterPages {
        val data = RawJson.parse(json)
        val rawArr = listOf("chapter_images", "chapterImages", "images", "image_list")
            .firstNotNullOfOrNull { k -> data.asObj()?.get(k) as? JVal.Arr }?.items.orEmpty()
        val images = rawArr.mapIndexedNotNull { i, item ->
            val rawUrl = when (item) {
                is JVal.Str -> item.v
                else -> item.str("url").ifBlank { item.str("src").ifBlank { item.str("image_url").ifBlank { item.str("imageUrl") } } }
            }
            absolutize(rawUrl, origin)?.let { PageRef(page = i + 1, url = it) }
        }
        if (images.isEmpty()) throw RawApiException("Data chapter koudaimh kosong.")
        return finishPages("koudaimh", data.str("comic_name"), data.str("chapter_title"), images)
    }

    private fun koudaimhSeries(url: String): List<ChapterHit> {
        val slug = Regex("""koudaimh\.com/manhua/([^/]+)/?(?:[?#].*)?$""").find(url)?.groupValues?.get(1)
            ?: throw RawApiException("URL series koudaimh tidak valid.")
        return parseKoudaimhSeries(DirectHttp.getText(url, koudaimhHeaders()), slug)
    }

    fun parseKoudaimhSeries(html: String, slug: String): List<ChapterHit> {
        val linkRe = Regex(
            """<a[^>]+href="(?:https?://(?:www\.|m\.)?koudaimh\.com)?/manhua/${Regex.escape(slug)}/(\d+)\.html"[^>]*>\s*([\s\S]*?)\s*</a>""",
            RegexOption.IGNORE_CASE
        )
        val seen = mutableSetOf<String>()
        val out = mutableListOf<ChapterHit>()
        linkRe.findAll(html).forEach { m ->
            val id = m.groupValues[1]
            if (seen.add(id)) {
                val t = m.groupValues[2].replace(Regex("<[^>]+>"), "").replace(Regex("""\s+"""), " ").trim()
                out.add(ChapterHit(id = id, title = t.ifBlank { "Chapter $id" }, url = "https://m.koudaimh.com/manhua/$slug/$id.html"))
            }
        }
        out.sortByDescending { it.id.toIntOrNull() ?: 0 }
        if (out.isEmpty()) throw RawApiException("Daftar chapter koudaimh kosong.")
        return out
    }

    // ── EN: mangadex ────────────────────────────────────────────────

    private fun mangadexPages(url: String): ChapterPages {
        val chId = Regex("""mangadex\.org/chapter/([0-9a-f-]{36})""", RegexOption.IGNORE_CASE).find(url)?.groupValues?.get(1)
            ?: throw RawApiException("URL chapter MangaDex tidak valid.")
        val chapBody = DirectHttp.getText("$MANGADEX_API/chapter/$chId?includes%5B%5D=manga", DirectHttp.apiHeaders())
        val serverBody = DirectHttp.getText("$MANGADEX_API/at-home/server/$chId", DirectHttp.apiHeaders())
        val mangaBody = try {
            val mangaId = RawJson.parse(chapBody).asObj()?.get("data")?.asObj()
                ?.let { (it["relationships"] as? JVal.Arr)?.items?.firstOrNull { r -> r.asObj()?.get("type")?.asStr() == "manga" } }
                ?.asObj()?.get("id")?.asStr() ?: ""
            if (mangaId.isBlank()) "" else DirectHttp.getText("$MANGADEX_API/manga/$mangaId", DirectHttp.apiHeaders())
        } catch (e: Exception) {
            ""
        }
        return parseMangadexChapter(chapBody, serverBody, mangaBody)
    }

    fun parseMangadexChapter(chapBody: String, serverBody: String, mangaBody: String): ChapterPages {
        val chap = RawJson.parse(chapBody).asObj()?.get("data")?.asObj()
            ?: throw RawApiException("Respons chapter MangaDex invalid.")
        var comic = ""
        if (mangaBody.isNotBlank()) {
            try {
                val titles = RawJson.parse(mangaBody).asObj()?.get("data")?.asObj()?.get("attributes")?.asObj()?.get("title")?.asObj()
                comic = titles?.get("en")?.asStr() ?: titles?.values?.firstOrNull()?.asStr() ?: ""
            } catch (e: Exception) { /* judul opsional */ }
        }
        val server = RawJson.parse(serverBody)
        val ch = server.asObj()?.get("chapter")?.asObj() ?: throw RawApiException("At-home MangaDex tanpa hash.")
        val hash = ch["hash"]?.asStr() ?: throw RawApiException("At-home MangaDex tanpa hash.")
        val base = server.str("baseUrl")
        val files = (ch["data"] as? JVal.Arr)?.items?.mapNotNull { it.asStr() }.orEmpty()
        if (files.isEmpty()) throw RawApiException("Chapter MangaDex kosong.")
        val attrs = chap["attributes"]?.asObj()
        val chapTitle = attrs?.get("title")?.asStr() ?: "Chapter ${attrs?.get("chapter")?.asStr() ?: "?"}"
        return finishPages("mangadex", comic, chapTitle, files.mapIndexed { i, fn -> PageRef(page = i + 1, url = "$base/data/$hash/$fn") })
    }

    private fun mangadexSeries(url: String): List<ChapterHit> {
        val mangaId = Regex("""mangadex\.org/title/([0-9a-f-]{36})""", RegexOption.IGNORE_CASE).find(url)?.groupValues?.get(1)
            ?: throw RawApiException("URL series MangaDex tidak valid.")
        var offset = 0
        val bodies = mutableListOf<String>()
        repeat(10) {
            val body = DirectHttp.getText(
                "$MANGADEX_API/manga/$mangaId/feed?limit=100&offset=$offset&order%5Bvolume%5D=desc&order%5Bchapter%5D=desc&translatedLanguage%5B%5D=en&includes%5B%5D=scanlation_group",
                DirectHttp.apiHeaders()
            )
            bodies.add(body)
            val root = RawJson.parse(body)
            val batch = ((root.asObj()?.get("data") as? JVal.Arr)?.items.orEmpty()).size
            offset += 100
            if (root.int("total") <= offset || batch < 100) return@repeat
        }
        return parseMangadexSeries(bodies)
    }

    fun parseMangadexSeries(bodies: List<String>): List<ChapterHit> {
        val out = mutableListOf<ChapterHit>()
        bodies.forEach { body ->
            val batch = (RawJson.parse(body).asObj()?.get("data") as? JVal.Arr)?.items.orEmpty()
            batch.forEach { item ->
                val o = item.asObj() ?: return@forEach
                val id = o["id"]?.asStr() ?: return@forEach
                val a = o["attributes"]?.asObj()
                val t = a?.get("title")?.asStr()
                val num = a?.get("chapter")?.asStr() ?: ""
                out.add(ChapterHit(id = id, title = if (!t.isNullOrBlank()) "Ch. $num $t".trim() else "Chapter ${num.ifBlank { "?" }}", url = "https://mangadex.org/chapter/$id"))
            }
        }
        if (out.isEmpty()) throw RawApiException("Series MangaDex kosong.")
        return out
    }

    // ── EN: mangapill ───────────────────────────────────────────────

    private fun mangapillPages(url: String): ChapterPages {
        Regex("""mangapill\.com/chapters/([^/]+)/([^/?#]+)""", RegexOption.IGNORE_CASE).find(url)
            ?: throw RawApiException("URL chapter MangaPill tidak valid.")
        return parseMangapillChapter(DirectHttp.getText(url, DirectHttp.htmlHeaders("https://mangapill.com/")))
    }

    fun parseMangapillChapter(html: String): ChapterPages {
        var idx = 0
        val images = Regex("""<img[^>]*class="js-page"[^>]*data-src="([^"]+)"""", RegexOption.IGNORE_CASE)
            .findAll(html).map { PageRef(page = ++idx, url = it.groupValues[1]) }.toList()
        if (images.isEmpty()) throw RawApiException("Tidak ada gambar MangaPill (markup berubah?).")
        return finishPages("mangapill", "", titleOf(html), images)
    }

    private fun mangapillSeries(url: String): List<ChapterHit> {
        val m = Regex("""mangapill\.com/manga/(\d+)/([^/?#]+)""", RegexOption.IGNORE_CASE).find(url)
            ?: throw RawApiException("URL series MangaPill tidak valid.")
        return parseMangapillSeries(
            DirectHttp.getText("https://mangapill.com/manga/${m.groupValues[1]}/${m.groupValues[2]}", DirectHttp.htmlHeaders("https://mangapill.com/"))
        )
    }

    fun parseMangapillSeries(html: String): List<ChapterHit> {
        val out = mutableListOf<ChapterHit>()
        val seen = mutableSetOf<String>()
        Regex("""<a href="/chapters/([^"]+)"[^>]*>([^<]+)</a>""", RegexOption.IGNORE_CASE).findAll(html).forEach { a ->
            val cid = a.groupValues[1]
            val title = a.groupValues[2].trim()
            if (title.isBlank() || !seen.add(cid)) return@forEach
            out.add(ChapterHit(id = cid, title = title, url = "https://mangapill.com/chapters/$cid"))
        }
        if (out.isEmpty()) throw RawApiException("Daftar chapter MangaPill kosong.")
        return out
    }

    // ── EN: comick ──────────────────────────────────────────────────

    private fun comickPages(url: String): ChapterPages {
        val hid = Regex("""(?:api\.)?comick\.(?:io|fun|dev)/chapter/([a-zA-Z0-9-]+)""", RegexOption.IGNORE_CASE).find(url)?.groupValues?.get(1)
            ?: Regex("""comick\.(?:io|fun|dev)/comic/[^/]+/([a-zA-Z0-9-]+)-chapter-""", RegexOption.IGNORE_CASE).find(url)?.groupValues?.get(1)
            ?: throw RawApiException("URL chapter Comick tidak valid.")
        var lastErr: Exception? = null
        for (base in COMICK_APIS) {
            try {
                return parseComickChapter(DirectHttp.getText("$base/chapter/$hid", DirectHttp.apiHeaders("https://comick.io/")))
            } catch (e: Exception) {
                lastErr = e
            }
        }
        throw RawApiException("Chapter Comick gagal: ${lastErr?.message}")
    }

    fun parseComickChapter(body: String): ChapterPages {
        val root = RawJson.parse(body)
        val chap = root.asObj()?.get("chapter")?.asObj() ?: root.asObj() ?: throw RawApiException("Respons Comick asing.")
        val imgs = (chap["md_images"] as? JVal.Arr)?.items.orEmpty().mapNotNull { it.asObj()?.get("b2key")?.asStr() }
        if (imgs.isEmpty()) throw RawApiException("Chapter Comick kosong.")
        val title = chap["title"]?.asStr() ?: "Chapter ${chap["chap"]?.asStr() ?: ""}"
        return finishPages("comick", "", title.trim(), imgs.mapIndexed { i, k -> PageRef(page = i + 1, url = "https://meo.comick.pictures/$k") })
    }

    private fun comickSeries(url: String): List<ChapterHit> {
        val slug = Regex("""comick\.(?:io|fun|dev)/comic/([^/?#]+)""", RegexOption.IGNORE_CASE).find(url)?.groupValues?.get(1)
            ?: throw RawApiException("URL series Comick tidak valid.")
        var lastErr: Exception? = null
        for (base in COMICK_APIS) {
            try {
                val comicBody = DirectHttp.getText("$base/comic/$slug", DirectHttp.apiHeaders("https://comick.io/"))
                val hid = RawJson.parse(comicBody).asObj()
                    ?.let { it["comic"]?.asObj() ?: it }?.get("hid")?.asStr()
                    ?: throw RawApiException("Comick tanpa hid.")
                val pages = mutableListOf<String>()
                for (page in 1..10) {
                    val body = DirectHttp.getText("$base/comic/$hid/chapters?lang=en&limit=100&page=$page", DirectHttp.apiHeaders("https://comick.io/"))
                    pages.add(body)
                    val n = ((RawJson.parse(body).asObj()?.get("chapters") as? JVal.Arr)?.items.orEmpty()).size
                    if (n < 100) break
                }
                return parseComickSeries(pages, base)
            } catch (e: Exception) {
                lastErr = e
            }
        }
        throw RawApiException("Series Comick gagal: ${lastErr?.message}")
    }

    fun parseComickSeries(bodies: List<String>, base: String): List<ChapterHit> {
        val out = mutableListOf<ChapterHit>()
        bodies.forEach { body ->
            val batch = (RawJson.parse(body).asObj()?.get("chapters") as? JVal.Arr)?.items.orEmpty()
            batch.forEach { item ->
                val o = item.asObj() ?: return@forEach
                val chHid = o["hid"]?.asStr() ?: return@forEach
                val t = o["title"]?.asStr()
                val num = o["chap"]?.asStr() ?: ""
                out.add(ChapterHit(id = chHid, title = if (!t.isNullOrBlank()) "Ch. $num $t".trim() else "Chapter ${num.ifBlank { "?" }}", url = "$base/chapter/$chHid"))
            }
        }
        if (out.isEmpty()) throw RawApiException("Series Comick kosong.")
        return out
    }

    // ── EN: mangageko ───────────────────────────────────────────────

    private fun mangagekoPages(url: String): ChapterPages {
        Regex("""(?:mgeko\.cc|mangageko\.cc|mangamob\.com)/reader/[^/]+/([^/?#]+)/?$""", RegexOption.IGNORE_CASE).find(url)
            ?: throw RawApiException("URL chapter MangaGeko tidak valid.")
        return parseMangagekoChapter(DirectHttp.getText(url, DirectHttp.htmlHeaders("https://www.mgeko.cc/")))
    }

    fun parseMangagekoChapter(html: String): ChapterPages {
        val images = Regex("""<img[^>]*\bid="image-\d+"[^>]*>""", RegexOption.IGNORE_CASE).findAll(html).mapNotNull { tagM ->
            val src = Regex("""\bsrc="([^"]+)"""", RegexOption.IGNORE_CASE).find(tagM.value)?.groupValues?.get(1)?.trim() ?: return@mapNotNull null
            if (!src.startsWith("http://") && !src.startsWith("https://")) return@mapNotNull null
            if (src.contains("logo_200x200") || src.contains("loading_api_transparent")) return@mapNotNull null
            src
        }.toList().mapIndexed { i, u -> PageRef(page = i + 1, url = u) }
        if (images.isEmpty()) throw RawApiException("Tidak ada gambar MangaGeko (lazy-load JS?).")
        return finishPages("mangageko", "", titleOf(html), images)
    }

    private fun mangagekoSeries(url: String): List<ChapterHit> {
        val slug = Regex("""(?:mgeko\.cc|mangageko\.cc|mangamob\.com)/manga/([^/?#]+)/?$""", RegexOption.IGNORE_CASE).find(url)?.groupValues?.get(1)
            ?: throw RawApiException("URL series MangaGeko tidak valid.")
        val headers = DirectHttp.htmlHeaders("https://www.mgeko.cc/")
        var chapters = parseMgekoReaderLinks(
            runCatching { DirectHttp.getText("https://www.mgeko.cc/manga/$slug/all-chapters/", headers) }.getOrNull() ?: ""
        )
        if (chapters.isEmpty()) {
            chapters = parseMgekoReaderLinks(DirectHttp.getText(url, headers))
        }
        if (chapters.isEmpty()) throw RawApiException("Daftar chapter MangaGeko kosong (proteksi Cloudflare?).")
        return chapters
    }

    fun parseMgekoReaderLinks(html: String): List<ChapterHit> {
        if (html.isBlank()) return emptyList()
        val out = mutableListOf<ChapterHit>()
        val seen = mutableSetOf<String>()
        Regex("""<a[^>]*href="(/reader/[^"]+)"[^>]*>([\s\S]{0,600}?)</a>""", RegexOption.IGNORE_CASE).findAll(html).forEach { m ->
            val u = "https://www.mgeko.cc${m.groupValues[1]}"
            if (!seen.add(u)) return@forEach
            val text = m.groupValues[2].replace(Regex("<[^>]+>"), " ").replace(Regex("""\s+"""), " ").trim()
            var token = (text.split(" ").getOrElse(0) { "" })
            if (token.endsWith("-eng-li", ignoreCase = true)) token = token.dropLast(7)
            val label = token.ifBlank { u.split("/").filter { it.isNotBlank() }.lastOrNull() ?: u }.replace("-", " ")
            out.add(ChapterHit(id = u, title = "Chapter $label", url = u))
            if (out.size > 2000) return out
        }
        return out
    }

    // ── EN: demonic ─────────────────────────────────────────────────

    private fun demonicPages(url: String): ChapterPages {
        return parseDemonicChapter(DirectHttp.getText(url, DirectHttp.htmlHeaders("https://demonicscans.org/")))
    }

    fun parseDemonicChapter(html: String): ChapterPages {
        val seen = mutableSetOf<String>()
        val images = Regex("""<img[^>]*class="[^"]*\bimgholder\b[^"]*"[^>]*src="([^"]+)"""", RegexOption.IGNORE_CASE)
            .findAll(html).mapNotNull { m ->
                val src = m.groupValues[1].trim()
                if (!src.startsWith("http") || src.contains("free_ads") || !seen.add(src)) return@mapNotNull null
                src
            }.toList().mapIndexed { i, u -> PageRef(page = i + 1, url = u) }
        if (images.isEmpty()) throw RawApiException("Tidak ada gambar DemonicScans.")
        return finishPages("demonic", "", titleOf(html), images)
    }

    private fun demonicSeries(url: String): List<ChapterHit> {
        return parseDemonicSeries(DirectHttp.getText(url, DirectHttp.htmlHeaders("https://demonicscans.org/")))
    }

    fun parseDemonicSeries(html: String): List<ChapterHit> {
        val out = mutableListOf<ChapterHit>()
        val seen = mutableSetOf<String>()
        Regex("""<a[^>]*href="(/chaptered\.php\?manga=\d+&chapter=[^"]+)"[^>]*class="[^"]*\bchplinks\b[^"]*"[^>]*title="([^"]*)"""", RegexOption.IGNORE_CASE)
            .findAll(html).forEach { m ->
                val u = "https://demonicscans.org" + m.groupValues[1].replace("&amp;", "&")
                if (!seen.add(u)) return@forEach
                out.add(ChapterHit(id = u, title = m.groupValues[2].trim().ifBlank { u }, url = u))
                if (out.size > 1000) return out
            }
        if (out.isEmpty()) throw RawApiException("Daftar chapter DemonicScans kosong.")
        return out
    }

    // ── EN: likemanga ───────────────────────────────────────────────

    private fun likemangaPages(url: String): ChapterPages {
        Regex("""likemanga\.ink/[a-z0-9][^/]*-\d+/(chapter-[^/?#]+)/?$""", RegexOption.IGNORE_CASE).find(url)
            ?: throw RawApiException("URL chapter LikeManga tidak valid.")
        return parseLikemangaChapter(DirectHttp.getText(url, DirectHttp.htmlHeaders("https://likemanga.ink/")))
    }

    fun parseLikemangaChapter(html: String): ChapterPages {
        val images = Regex("""<img[^>]*data-index="\d+"[^>]*src="(https://[^"]+)"[^>]*>""", RegexOption.IGNORE_CASE)
            .findAll(html).map { it.groupValues[1] }.toList()
            .mapIndexed { i, u -> PageRef(page = i + 1, url = u) }
        if (images.isEmpty()) throw RawApiException("Tidak ada gambar LikeManga.")
        return finishPages("likemanga", "", titleOf(html), images)
    }

    private fun likemangaSeries(url: String): List<ChapterHit> {
        val slugId = Regex("""likemanga\.ink/([a-z0-9][^/]*-\d+)/?$""", RegexOption.IGNORE_CASE).find(url)?.groupValues?.get(1)
            ?: throw RawApiException("URL series LikeManga tidak valid.")
        return parseLikemangaSeries(DirectHttp.getText(url, DirectHttp.htmlHeaders("https://likemanga.ink/")), slugId)
    }

    fun parseLikemangaSeries(html: String, slugId: String): List<ChapterHit> {
        val linkRe = Regex("""<a[^>]*href="(/\Q$slugId\E/(chapter-[^"]+))/"[^>]*>([^<]*)<""", RegexOption.IGNORE_CASE)
        val out = mutableListOf<ChapterHit>()
        val seen = mutableSetOf<String>()
        linkRe.findAll(html).forEach { m ->
            val u = "https://likemanga.ink${m.groupValues[1]}/"
            if (!seen.add(u)) return@forEach
            out.add(ChapterHit(id = m.groupValues[2], title = m.groupValues[3].trim().ifBlank { m.groupValues[2] }, url = u))
            if (out.size > 1000) return out
        }
        if (out.isEmpty()) throw RawApiException("Daftar chapter LikeManga kosong.")
        return out
    }

    // ── EN: mangabats ───────────────────────────────────────────────

    private fun mangabatsPages(url: String): ChapterPages {
        if (url.split("/").filter { it.isNotBlank() }.size < 4) throw RawApiException("URL chapter MangaBats tidak valid.")
        val html = DirectHttp.getText(url, DirectHttp.htmlHeaders("https://www.mangabats.com/"))
        return parseMangabatsChapter(html)
    }

    fun parseMangabatsChapter(html: String): ChapterPages {
        val start = html.indexOf("container-chapter-reader")
        val scope = if (start >= 0) html.substring(start, minOf(start + 400000, html.length)) else html
        val seen = mutableSetOf<String>()
        val images = Regex("""<img[^>]*src='(https://[^']+)'[^>]*>""", RegexOption.IGNORE_CASE)
            .findAll(scope).mapNotNull { m -> if (seen.add(m.groupValues[1])) m.groupValues[1] else null }
            .toList().mapIndexed { i, u -> PageRef(page = i + 1, url = u) }
        if (images.isEmpty()) throw RawApiException("Tidak ada gambar MangaBats.")
        return finishPages("mangabats", "", titleOf(html), images)
    }

    private fun mangabatsSeries(url: String): List<ChapterHit> {
        val slug = Regex("""mangabats\.com/manga/([^/?#]+)/?$""", RegexOption.IGNORE_CASE).find(url)?.groupValues?.get(1)
            ?: throw RawApiException("URL series MangaBats tidak valid.")
        return parseMangabatsSeries(
            DirectHttp.getText(
                "https://www.mangabats.com/api/manga/$slug/chapters",
                DirectHttp.apiHeaders("https://www.mangabats.com/")
            ),
            slug
        )
    }

    fun parseMangabatsSeries(body: String, slug: String): List<ChapterHit> {
        val root = RawJson.parse(body)
        val arr = (root.asObj()?.get("data")?.asObj()?.get("chapters") as? JVal.Arr)?.items
            ?: (root.asObj()?.get("chapters") as? JVal.Arr)?.items.orEmpty()
        return arr.mapNotNull { item ->
            val o = item.asObj() ?: return@mapNotNull null
            val cslug = o["chapter_slug"]?.asStr() ?: return@mapNotNull null
            val name = o["chapter_name"]?.asStr()
            val num = (o["chapter_num"] as? JVal.Num)?.v?.toInt()?.toString()
                ?: o["chapter_num"]?.asStr() ?: "?"
            ChapterHit(id = cslug, title = name?.ifBlank { "Chapter $num" } ?: "Chapter $num", url = "https://www.mangabats.com/manga/$slug/$cslug")
        }.ifEmpty { throw RawApiException("Daftar chapter MangaBats kosong.") }
    }

    // ── EN: xcomic ──────────────────────────────────────────────────

    private fun xcomicPages(url: String): ChapterPages {
        Regex("""xcomic\.me/chapter/([^/?#]+)/?$""", RegexOption.IGNORE_CASE).find(url)
            ?: throw RawApiException("URL chapter XComic tidak valid.")
        return parseXcomicChapter(DirectHttp.getText(url, DirectHttp.htmlHeaders("https://xcomic.me/")))
    }

    fun parseXcomicChapter(html: String): ChapterPages {
        val start = html.indexOf("\"imageUrls\"")
        val scope = if (start >= 0) html.substring(start, minOf(start + 500000, html.length)) else html
        val seen = mutableSetOf<String>()
        var images = Regex(""""(https://i\d+\.img\d+\.org/[^"]+)"""").findAll(scope)
            .mapNotNull { m -> if (seen.add(m.groupValues[1])) m.groupValues[1] else null }
            .toList().mapIndexed { i, u -> PageRef(page = i + 1, url = u) }
        if (images.isEmpty()) throw RawApiException("Tidak ada gambar XComic (Qwik berubah?).")
        if (images.size > 2000) images = images.take(2000)
        return finishPages("xcomic", "", titleOf(html), images)
    }

    private fun xcomicSeries(url: String): List<ChapterHit> {
        Regex("""xcomic\.me/source/([^/?#]+)/?$""", RegexOption.IGNORE_CASE).find(url)
            ?: throw RawApiException("URL series XComic tidak valid.")
        return parseXcomicSeries(DirectHttp.getText(url, DirectHttp.htmlHeaders("https://xcomic.me/")))
    }

    fun parseXcomicSeries(html: String): List<ChapterHit> {
        val out = mutableListOf<ChapterHit>()
        val seen = mutableSetOf<String>()
        Regex("""<a[^>]*href="(/chapter/[^"]+)"[^>]*>([\s\S]{0,300}?)</a>""", RegexOption.IGNORE_CASE).findAll(html).forEach { m ->
            val u = "https://xcomic.me${m.groupValues[1]}"
            val title = m.groupValues[2].replace(Regex("<[^>]+>"), " ").replace(Regex("""\s+"""), " ").trim()
            if (!title.contains("chapter", ignoreCase = true) || !seen.add(u)) return@forEach
            out.add(ChapterHit(id = u, title = title, url = u))
            if (out.size > 2000) return out
        }
        if (out.isEmpty()) throw RawApiException("Daftar chapter XComic kosong.")
        return out
    }

    // ── Finalisasi: saring banner utuh via metadata bila ada ─────────

    fun finishPages(source: String, comicTitle: String, chapterTitle: String, images: List<PageRef>): ChapterPages {
        if (images.isEmpty()) throw RawApiException("Chapter $source kosong.")
        val kept = images.filterNot { RawContract.isFullBanner(it) }
        val pages = if (kept.isNotEmpty()) kept else images
        val title = chapterTitle.ifBlank { comicTitle.ifBlank { "Unduhan" } }
        return ChapterPages(source = source, title = title, pages = pages, droppedBanners = images.size - pages.size)
    }
}
