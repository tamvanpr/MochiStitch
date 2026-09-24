package com.mochistitch.core.download

import com.mochistitch.core.common.BannerPolicy

/** Kelompok sumber: ID (fase 1) dan EN (fase berikutnya, struktur siap). */
enum class SourceGroup { ID, EN }

/**
 * Definisi satu sumber unduhan.
 *
 * Pola URL disalin dari worker Trial Fetch (dipakai classifier agar tempelan
 * URL bisa dikenali sebagai series vs chapter TANPA request jaringan).
 */
data class SourceDef(
    val id: String,
    val label: String,
    val group: SourceGroup,
    val chapterRx: List<Regex>,
    val seriesRx: List<Regex>,
    /** Tanpa endpoint search di worker (mis. manwa) — hanya tempel URL. */
    val searchable: Boolean = true,
    /** Strip banner bawaan sumber (null = tidak ada). */
    val banner: BannerPolicy? = null
)

/** Hasil series dari `?action=search`. */
data class SeriesHit(
    val title: String,
    val url: String,
    val cover: String = "",
    val author: String = ""
)

/** Satu chapter dari resolve series. */
data class ChapterHit(
    val id: String,
    val title: String,
    val url: String
)

/** Satu halaman dari resolve chapter (kontrak minimal: page + url). */
data class PageRef(val page: Int, val url: String, val width: Int = 0, val height: Int = 0)

/** Hasil resolve chapter: judul + daftar gambar berurutan. */
data class ChapterPages(
    val source: String,
    val title: String,
    val pages: List<PageRef>,
    /** Gambar banner utuh yang dibuang via metadata (tanpa unduh). */
    val droppedBanners: Int = 0
)

enum class UrlKind { SERIES, CHAPTER, UNKNOWN }

object RawSources {

    val BAOZIMH = SourceDef(
        id = "baozimh", label = "Baozimh", group = SourceGroup.ID,
        chapterRx = listOf(
            Regex("""(?:twmanga\.com|baozimh\.com)/(?:comic/chapter|baozimhapp/comic/chapter)/([^/]+)/([^/?#]+)\.html""")
        ),
        seriesRx = listOf(
            Regex("""^https?://(?:www\.)?(?:baozimh\.com|twmanga\.com)/comic/([^/?#]+)/?(?:[?#].*)?$""", RegexOption.IGNORE_CASE)
        ),
        banner = BannerPolicy()
    )

    val WMANHUA = SourceDef(
        id = "wmanhua", label = "Wmanhua", group = SourceGroup.ID,
        chapterRx = listOf(
            Regex("""^https?://(?:www\.)?wmanhua\.com/chapter/(\d+)-(\d+)\.html""", RegexOption.IGNORE_CASE)
        ),
        seriesRx = listOf(
            Regex("""^https?://(?:www\.)?wmanhua\.com/comic/(\d+)\.html""", RegexOption.IGNORE_CASE)
        )
    )

    val JJABTOON = SourceDef(
        id = "jjabtoon", label = "JJabtoon", group = SourceGroup.ID,
        chapterRx = listOf(Regex("""[^/]*jjabtoon[^/]*/episodes/(\d+)""")),
        seriesRx = listOf(Regex("""[^/]*jjabtoon[^/]*/webtoons/(\d+)"""))
    )

    val JJAPTOON = SourceDef(
        id = "jjaptoon", label = "JJaptoon", group = SourceGroup.ID,
        chapterRx = listOf(Regex("""[^/]*jjaptoon[^/]*/chapters/(\d+)""")),
        seriesRx = listOf(Regex("""[^/]*jjaptoon[^/]*/comics/(\d+)"""))
    )

    val KOUDAIMH = SourceDef(
        id = "koudaimh", label = "Koudaimh", group = SourceGroup.ID,
        chapterRx = listOf(
            Regex("""^(?:https?://)?(?:www\.|m\.)?koudaimh\.com/manhua/([^/]+)/(\d+)\.html""")
        ),
        seriesRx = listOf(
            Regex("""^(?:https?://)?(?:www\.|m\.)?koudaimh\.com/manhua/([^/]+)/?(?:[?#].*)?$""")
        )
    )

    val GOODTOON = SourceDef(
        id = "goodtoon", label = "Goodtoon", group = SourceGroup.ID,
        chapterRx = listOf(
            Regex("""[^/]*goodtoon[^/]*/manga/([^/?#]+)/(?:chapter-)?(\d+)/?(?:[?#].*)?$""")
        ),
        seriesRx = listOf(
            Regex("""[^/]*goodtoon[^/]*/manga/([^/?#]+)/?(?:[?#].*)?$""")
        )
    )

    /** Manwa: tanpa search di worker, tapi resolve chapter+series didukung. */
    val MANWA = SourceDef(
        id = "manwa", label = "Manwa", group = SourceGroup.ID,
        chapterRx = listOf(
            Regex("""^https?://(?:www\.)?manwa\.me/chapter/(\d+)""")
        ),
        seriesRx = listOf(
            Regex("""^https?://(?:www\.)?manwa\.me/book/(\d+)""")
        ),
        searchable = false
    )

    // ── Sumber EN (kontrak resolve sama; pola URL dari worker En Trial Fetch). ──

    val MANGADEX = SourceDef(
        id = "mangadex", label = "MangaDex", group = SourceGroup.EN,
        chapterRx = listOf(Regex("""^https?://(?:www\.)?mangadex\.org/chapter/([0-9a-f-]{36})""", RegexOption.IGNORE_CASE)),
        seriesRx = listOf(Regex("""^https?://(?:www\.)?mangadex\.org/title/([0-9a-f-]{36})""", RegexOption.IGNORE_CASE))
    )

    val MANGAPILL = SourceDef(
        id = "mangapill", label = "MangaPill", group = SourceGroup.EN,
        chapterRx = listOf(Regex("""^https?://(?:www\.)?mangapill\.com/chapters/([^/]+)/([^/?#]+)""", RegexOption.IGNORE_CASE)),
        seriesRx = listOf(Regex("""^https?://(?:www\.)?mangapill\.com/manga/(\d+)/([^/?#]+)""", RegexOption.IGNORE_CASE))
    )

    val COMICK = SourceDef(
        id = "comick", label = "Comick", group = SourceGroup.EN,
        chapterRx = listOf(
            Regex("""^https?://(?:api\.)?comick\.(?:io|fun|dev)/chapter/([a-zA-Z0-9-]+)""", RegexOption.IGNORE_CASE),
            Regex("""^https?://(?:www\.)?comick\.(?:io|fun|dev)/comic/[^/]+/([a-zA-Z0-9-]+)-chapter-""", RegexOption.IGNORE_CASE)
        ),
        seriesRx = listOf(Regex("""^https?://(?:www\.)?comick\.(?:io|fun|dev)/comic/([^/?#]+)""", RegexOption.IGNORE_CASE))
    )

    val MANGAGEKO = SourceDef(
        id = "mangageko", label = "MangaGeko", group = SourceGroup.EN,
        chapterRx = listOf(Regex("""^https?://(?:www\.)?(?:mgeko\.cc|mangageko\.cc|mangamob\.com)/reader/[^/]+/([^/?#]+)/?$""", RegexOption.IGNORE_CASE)),
        seriesRx = listOf(Regex("""^https?://(?:www\.)?(?:mgeko\.cc|mangageko\.cc|mangamob\.com)/manga/([^/?#]+)/?$""", RegexOption.IGNORE_CASE))
    )

    val DEMONIC = SourceDef(
        id = "demonic", label = "DemonicScans", group = SourceGroup.EN,
        chapterRx = listOf(
            Regex("""^https?://(?:www\.)?demonicscans\.org/chaptered\.php""", RegexOption.IGNORE_CASE),
            Regex("""^https?://(?:www\.)?demonicscans\.org/title/[^/]+/chapter/([^/?#]+)""", RegexOption.IGNORE_CASE)
        ),
        seriesRx = listOf(Regex("""^https?://(?:www\.)?demonicscans\.org/manga/([^/?#]+)/?$""", RegexOption.IGNORE_CASE))
    )

    val LIKEMANGA = SourceDef(
        id = "likemanga", label = "LikeManga", group = SourceGroup.EN,
        chapterRx = listOf(Regex("""^https?://(?:www\.)?likemanga\.ink/[a-z0-9][^/]*-\d+/(chapter-[^/?#]+)/?$""", RegexOption.IGNORE_CASE)),
        seriesRx = listOf(Regex("""^https?://(?:www\.)?likemanga\.ink/([a-z0-9][^/]*-\d+)/?$""", RegexOption.IGNORE_CASE))
    )

    val MANGABATS = SourceDef(
        id = "mangabats", label = "MangaBats", group = SourceGroup.EN,
        chapterRx = listOf(Regex("""^https?://(?:www\.)?mangabats\.com/manga/[^/]+/([^/?#]+)/?$""", RegexOption.IGNORE_CASE)),
        seriesRx = listOf(Regex("""^https?://(?:www\.)?mangabats\.com/manga/([^/?#]+)/?$""", RegexOption.IGNORE_CASE)),
        searchable = false
    )

    val XCOMIC = SourceDef(
        id = "xcomic", label = "XComic", group = SourceGroup.EN,
        chapterRx = listOf(Regex("""^https?://(?:www\.)?xcomic\.me/chapter/([^/?#]+)/?$""", RegexOption.IGNORE_CASE)),
        seriesRx = listOf(Regex("""^https?://(?:www\.)?xcomic\.me/source/([^/?#]+)/?$""", RegexOption.IGNORE_CASE))
    )

    val ALL_EN: List<SourceDef> = listOf(MANGADEX, MANGAPILL, COMICK, MANGAGEKO, DEMONIC, LIKEMANGA, MANGABATS, XCOMIC)

    /** Fase 1: sumber ID. */
    val ALL_ID: List<SourceDef> = listOf(BAOZIMH, WMANHUA, JJABTOON, KOUDAIMH, JJAPTOON, GOODTOON, MANWA)

    /** Semua sumber yang dikenal (ID + EN). */
    val ALL: List<SourceDef> = ALL_ID + ALL_EN

    fun byId(id: String): SourceDef? = ALL_ID.firstOrNull { it.id == id } ?: ALL_EN.firstOrNull { it.id == id }

    /**
     * Header unduhan gambar per sumber. Default Referer = halaman chapter
     * (lolos proteksi hotlink). Koudaimh MEMAKSA tanpa Referer/Origin untuk
     * CDN-nya (referrerpolicy no-referrer di situs asli; mengirim Referer
     * justru berisiko 403) — sama seperti worker.
     */
    fun imageHeaders(sourceId: String, imageUrl: String, chapterUrl: String): Map<String, String> {
        if (sourceId == KOUDAIMH.id) {
            val host = imageUrl.substringAfter("://").substringBefore("/").lowercase()
            val bare = host.removePrefix("www.")
            if (bare == "shimolife.com" || bare.endsWith(".shimolife.com") ||
                bare == "koudaimg.com" || bare.endsWith(".koudaimg.com") ||
                bare == "koudaimh.com" || bare.endsWith(".koudaimh.com")
            ) {
                return mapOf("Accept-Language" to "zh-CN,zh;q=0.9,en;q=0.8")
            }
        }
        return mapOf("Referer" to chapterUrl)
    }

    /**
     * Kenali tempelan URL sebagai (sumber, SERIES/CHAPTER). Chapter diperiksa
     * dulu karena pola series Goodtoon/Koudaimh juga cocok di URL chapter.
     */
    fun classify(rawUrl: String): Pair<SourceDef?, UrlKind> {
        val url = rawUrl.trim()
        if (url.isEmpty()) return null to UrlKind.UNKNOWN
        for (s in ALL) {
            if (s.chapterRx.any { it.containsMatchIn(url) }) return s to UrlKind.CHAPTER
        }
        for (s in ALL) {
            if (s.seriesRx.any { it.containsMatchIn(url) }) return s to UrlKind.SERIES
        }
        return null to UrlKind.UNKNOWN
    }
}
