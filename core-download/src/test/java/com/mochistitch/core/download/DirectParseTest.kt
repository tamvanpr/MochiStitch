package com.mochistitch.core.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectParseTest {

    // ── ID ──────────────────────────────────────────────────────

    @Test
    fun testBaozimhChapter() {
        val html = """<html><head><title>Ch.1 - KomikX</title></head><body>
            |<img class="comic-contain__item" data-index="0" data-src="https://cdn/a.jpg" data-w="800" data-h="1200">
            |<img class="comic-contain__item" data-index="1" data-src="https://cdn/b.jpg" data-w="800" data-h="200">
            |</body></html>""".trimMargin()
        val got = DirectResolvers.parseBaozimhChapter(html)
        // Banner utuh 800x200 ikut tersaring oleh finishPages.
        assertEquals(1, got.pages.size)
        assertEquals(1, got.droppedBanners)
        assertEquals("https://cdn/a.jpg", got.pages[0].url)
        assertEquals(800, got.pages[0].width)
    }

    @Test
    fun testWmanhuaChapter() {
        val html = """<html><head><title>12 KomikY - W</title></head><body><script>
            |var num = eval("2"); var pasd = "https://cdnw/ch1";
            |</script></body></html>""".trimMargin()
        val got = DirectResolvers.parseWmanhuaChapter(html)
        assertEquals(2, got.pages.size)
        assertEquals("https://cdnw/ch1/1.webp", got.pages[0].url)
        assertEquals("https://cdnw/ch1/2.webp", got.pages[1].url)
    }

    @Test
    fun testWmanhuaSeries() {
        val body = """{"code":0,"data":{"chapters":[
            |{"contentId":11,"id":101,"chapterName":"Ch 1"},
            |{"contentId":11,"id":102,"chapterName":"Ch 2"}]}}""".trimMargin()
        val got = DirectResolvers.parseWmanhuaSeries(body)
        assertEquals(2, got.size)
        assertEquals("https://www.wmanhua.com/chapter/11-101.html", got[0].url)
    }

    @Test
    fun testJjabtoonChapter() {
        val body = """{"success":true,"data":{"title":"EP 5","episodeNo":5,"webtoonId":9,
            |"images":[{"url":"https://cdn/2.jpg","sortOrder":2},{"url":"https://cdn/1.jpg","sortOrder":1}]}}""".trimMargin()
        val got = DirectResolvers.parseJjabtoonChapter(body)
        assertEquals("https://cdn/1.jpg", got.pages[0].url)
        assertEquals("https://cdn/2.jpg", got.pages[1].url)
    }

    @Test
    fun testJjaptoonChapter() {
        val html = """<html><head><title>Komik - 10화</title></head><body>
            |<img src="https://logo.png" alt="logo">
            |<img src="https://cdn/1.jpg" alt="Komik 10화 1">
            |<img src="https://cdn/2.jpg" alt="Komik 10화 2">
            |</body></html>""".trimMargin()
        val got = DirectResolvers.parseJjaptoonChapter(html)
        assertEquals(2, got.pages.size)
        assertEquals("https://cdn/1.jpg", got.pages[0].url)
    }

    @Test
    fun testGoodtoonChapter() {
        val html = """<html><head><title>Komik - Ch 3</title></head><body>
            |<img class="x wp-manga-chapter-img y" data-src="https://img.goodtoon/1.jpg" src="lazy.gif">
            |<img class="x wp-manga-chapter-img y" data-src="https://img.goodtoon/2.jpg" src="lazy.gif">
            |</body></html>""".trimMargin()
        val got = DirectResolvers.parseGoodtoonChapter(html)
        assertEquals(2, got.pages.size)
        assertEquals("https://img.goodtoon/2.jpg", got.pages[1].url)
    }

    @Test
    fun testManwaChapter() {
        val html = """<html><head><title>Komik - Ch 7</title></head><body>
            |<img class="content-img lazy_img" data-r-src="https://mwappimgs.cc/a.webp">
            |<img class="content-img lazy_img" data-r-src="https://mwappimgs.cc/b.webp">
            |</body></html>""".trimMargin()
        val got = DirectResolvers.parseManwaChapter(html)
        assertEquals(2, got.pages.size)
    }

    @Test
    fun testManwaSeriesReversed() {
        val html = """<a href="/chapter/1" title=" Ch 1 " class="chapteritem ">x</a>
            |<a href="/chapter/2" title=" Ch 2 " class="chapteritem ">x</a>""".trimMargin()
        val got = DirectResolvers.parseManwaSeries(html)
        assertEquals("2", got[0].id)
        assertEquals("1", got[1].id)
    }

    @Test
    fun testKoudaimhChapterRoundtrip() {
        // Enkripsi JSON lalu dekripsi via jalur produksi.
        val json = """{"comic_name":"K","chapter_title":"C 9","chapter_images":["https://cdn/shimo/1.jpg",{"url":"https://cdn/shimo/2.jpg?x=1&y=2"}]}"""
        val key = RawCrypto.KOUDAIMH_KEY.toByteArray(Charsets.UTF_8)
        val iv = ByteArray(16) { i -> (i * 7).toByte() }
        val ct = RawCrypto.aesCbcEncrypt(key, iv, json.toByteArray(Charsets.UTF_8))
        val blob = java.util.Base64.getEncoder().encodeToString(iv + ct)
            .replace("+", "-").replace("/", "_").replace("=", "")
        val plain = RawCrypto.decryptKoudaimhParams(blob)
        assertEquals(json, plain)
        val got = DirectResolvers.parseKoudaimhChapter(plain, "https://m.koudaimh.com")
        assertEquals(2, got.pages.size)
        assertEquals("https://cdn/shimo/1.jpg", got.pages[0].url)
        assertEquals("https://cdn/shimo/2.jpg?x=1&y=2", got.pages[1].url)
    }

    @Test
    fun testManwaImageRoundtrip() {
        val fake = ByteArray(64) { i -> i.toByte() }
        val k = RawCrypto.MANWA_KEY.toByteArray(Charsets.UTF_8)
        val iv = ByteArray(16) { i -> (i + 3).toByte() }
        val enc = RawCrypto.aesCbcEncrypt(k, iv, fake)
        // Jalur produksi memakai IV = key: verifikasi vektor tetap itu.
        val enc2 = RawCrypto.aesCbcEncrypt(k, k, fake)
        assertEquals(fake.toList(), RawCrypto.decryptManwaImage(enc2).toList())
        assertEquals(64, enc.size)
    }

    // ── EN ──────────────────────────────────────────────────────

    @Test
    fun testMangadexChapter() {
        val chap = """{"data":{"id":"cuuid","attributes":{"title":"The Fight","chapter":"12"},
            |"relationships":[{"type":"manga","id":"muuid"}]}}""".trimMargin()
        val server = """{"baseUrl":"https://uploads.mdex","chapter":{"hash":"abc","data":["a.jpg","b.jpg"]}}""".trimMargin()
        val manga = """{"data":{"attributes":{"title":{"en":"Solo"}}}}""".trimMargin()
        val got = DirectResolvers.parseMangadexChapter(chap, server, manga)
        assertEquals("The Fight", got.title)
        assertEquals("https://uploads.mdex/data/abc/a.jpg", got.pages[0].url)
        assertEquals(2, got.pages.size)
    }

    @Test
    fun testMangapillChapter() {
        val html = """<img class="js-page" data-src="https://cdn/1.jpg"><img class="js-page" data-src="https://cdn/2.jpg">"""
        val got = DirectResolvers.parseMangapillChapter(html)
        assertEquals(2, got.pages.size)
        assertEquals("mangapill", got.source)
    }

    @Test
    fun testComickChapter() {
        val body = """{"chapter":{"title":"Ch 4","chap":4,"md_images":[{"b2key":"/a/1.jpg"},{"b2key":"/a/2.jpg"}]}}""".trimMargin()
        val got = DirectResolvers.parseComickChapter(body)
        assertEquals("https://meo.comick.pictures//a/1.jpg", got.pages[0].url)
    }

    @Test
    fun testMangagekoChapter() {
        val html = """<img id="image-1" src="https://imgsrv5.com/a.jpg">
            |<img id="image-2" src="https://imgsrv5.com/b.jpg">
            |<img id="x" src="https://x.com/logo_200x200.png">""".trimMargin()
        val got = DirectResolvers.parseMangagekoChapter(html)
        assertEquals(2, got.pages.size)
    }

    @Test
    fun testDemonicChapter() {
        val html = """<img class="x imgholder y" src="https://cdn.demoniclibs.com/1.jpg">
            |<img class="x imgholder y" src="https://x.com/free_ads.jpg">""".trimMargin()
        val got = DirectResolvers.parseDemonicChapter(html)
        assertEquals(1, got.pages.size)
    }

    @Test
    fun testLikemangaChapter() {
        val html = """<img data-index="0" src="https://like.mgread.io/1.jpg" alt="c">"""
        val got = DirectResolvers.parseLikemangaChapter(html)
        assertEquals("https://like.mgread.io/1.jpg", got.pages[0].url)
    }

    @Test
    fun testMangabatsChapter() {
        val html = """<div class="container-chapter-reader"><img src='https://storage.waitst.com/a/1.webp'></div>"""
        val got = DirectResolvers.parseMangabatsChapter(html)
        assertEquals("https://storage.waitst.com/a/1.webp", got.pages[0].url)
    }

    @Test
    fun testXcomicChapter() {
        val html = """... "imageUrls",4,[0,"https://i01.img01.org/a.jpg",0,"https://i01.img01.org/b.jpg"] ..."""
        val got = DirectResolvers.parseXcomicChapter(html)
        assertEquals(2, got.pages.size)
    }

    @Test
    fun testXcomicSeries() {
        val html = """<a href="/chapter/abc"><span>Chapter 12</span></a><a href="/chapter/abc"><span>Chapter 12</span></a>"""
        val got = DirectResolvers.parseXcomicSeries(html)
        assertEquals(1, got.size)
        assertEquals("https://xcomic.me/chapter/abc", got[0].url)
    }

    @Test
    fun testKoudaimhSeries() {
        val html = """<a href="/manhua/solo/2.html"> Ch 2 </a><a href="/manhua/solo/1.html">Ch 1</a><a href="/manhua/solo/2.html">Ch 2</a>"""
        val got = DirectResolvers.parseKoudaimhSeries(html, "solo")
        assertEquals(2, got.size)
        assertEquals("2", got[0].id) // terbaru dulu
    }

    // ── Classifier EN ───────────────────────────────────────────

    @Test
    fun testClassifyEn() {
        val cases = mapOf(
            "https://mangadex.org/chapter/12345678-1234-1234-1234-123456789012" to ("mangadex" to UrlKind.CHAPTER),
            "https://mangadex.org/title/12345678-1234-1234-1234-123456789012" to ("mangadex" to UrlKind.SERIES),
            "https://mangapill.com/chapters/5/solo-leveling" to ("mangapill" to UrlKind.CHAPTER),
            "https://comick.io/comic/solo-leveling" to ("comick" to UrlKind.SERIES),
            "https://api.comick.dev/chapter/abc123" to ("comick" to UrlKind.CHAPTER),
            "https://www.mgeko.cc/reader/en/solo-chapter-1-eng-li/" to ("mangageko" to UrlKind.CHAPTER),
            "https://demonicscans.org/manga/solo" to ("demonic" to UrlKind.SERIES),
            "https://likemanga.ink/solo-1/" to ("likemanga" to UrlKind.SERIES),
            "https://www.mangabats.com/manga/solo" to ("mangabats" to UrlKind.SERIES),
            "https://www.mangabats.com/manga/solo/ch-1" to ("mangabats" to UrlKind.CHAPTER),
            "https://xcomic.me/chapter/abc" to ("xcomic" to UrlKind.CHAPTER),
            "https://xcomic.me/source/xyz" to ("xcomic" to UrlKind.SERIES)
        )
        for ((url, want) in cases) {
            val (src, kind) = RawSources.classify(url)
            assertEquals(url, want.first, src?.id)
            assertEquals(url, want.second, kind)
        }
    }

    @Test
    fun testImageHeadersKoudaimh() {
        val noRef = RawSources.imageHeaders("koudaimh", "https://abc.shimolife.com/x.jpg", "https://m.koudaimh.com/manhua/a/1.html")
        assertTrue(noRef.none { it.key.equals("Referer", ignoreCase = true) })
        val ref = RawSources.imageHeaders("baozimh", "https://cdn/x.jpg", "https://www.baozimh.com/comic/chapter/a/1.html")
        assertEquals("https://www.baozimh.com/comic/chapter/a/1.html", ref["Referer"])
    }
}
