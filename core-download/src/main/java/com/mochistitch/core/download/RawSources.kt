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

    /** Fase 1: sumber ID. EN menyusul (kontrak JSON identik). */
    val ALL_ID: List<SourceDef> = listOf(BAOZIMH, WMANHUA, JJABTOON, KOUDAIMH, JJAPTOON, GOODTOON, MANWA)

    fun byId(id: String): SourceDef? = ALL_ID.firstOrNull { it.id == id }

    /**
     * Kenali tempelan URL sebagai (sumber, SERIES/CHAPTER). Chapter diperiksa
     * dulu karena pola series Goodtoon/Koudaimh juga cocok di URL chapter.
     */
    fun classify(rawUrl: String): Pair<SourceDef?, UrlKind> {
        val url = rawUrl.trim()
        if (url.isEmpty()) return null to UrlKind.UNKNOWN
        for (s in ALL_ID) {
            if (s.chapterRx.any { it.containsMatchIn(url) }) return s to UrlKind.CHAPTER
        }
        for (s in ALL_ID) {
            if (s.seriesRx.any { it.containsMatchIn(url) }) return s to UrlKind.SERIES
        }
        return null to UrlKind.UNKNOWN
    }
}
