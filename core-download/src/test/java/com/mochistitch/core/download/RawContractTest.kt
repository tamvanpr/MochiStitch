package com.mochistitch.core.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RawContractTest {

    @Test
    fun testParsePagesMinimal() {
        val body = """{"source":"baozimh","chapter_id":"abc","chapter_title":"Ch. 1Test",
            |"total_images":2,"images":[{"page":1,"url":"https://cdn/x/1.jpg"},{"page":2,"url":"https://cdn/x/2.jpg"}]}""".trimMargin()
        val got = RawContract.parsePages(body)
        assertEquals("baozimh", got.source)
        assertEquals("Ch. 1Test", got.title)
        assertEquals(2, got.pages.size)
        assertEquals("https://cdn/x/1.jpg", got.pages[0].url)
    }

    @Test
    fun testParsePagesSortsAndSkipsBlank() {
        val body = """{"source":"jjaptoon","total_images":3,
            |"images":[{"page":3,"url":"https://c/3.jpg"},{"page":1,"url":""},{"page":2,"url":"https://c/2.jpg"}]}""".trimMargin()
        val got = RawContract.parsePages(body)
        assertEquals(2, got.pages.size)
        assertEquals(2, got.pages[0].page)
        assertEquals(3, got.pages[1].page)
    }

    @Test
    fun testParsePagesErrorThrows() {
        try {
            RawContract.parsePages("""{"error":"Host not allowed","hostname":"x"}""")
            fail("harus melempar")
        } catch (e: RawApiException) {
            assertTrue(e.message!!.contains("Host not allowed"))
        }
    }

    @Test
    fun testParseChapters() {
        val body = """{"source":"baozimh","chapters":[
            |{"chapter_id":"a","chapter_title":"Ch. 1","url":"https://www.baozimh.com/comic/chapter/x/1.html"},
            |{"chapter_id":"b","chapter_title":"Ch. 2","url":"https://www.baozimh.com/comic/chapter/x/2.html"}]}""".trimMargin()
        val got = RawContract.parseChapters(body)
        assertEquals(2, got.size)
        assertEquals("Ch. 1", got[0].title)
    }

    @Test
    fun testParseSearchIgnoresExtras() {
        val body = """{"source":"wmanhua","query":"solo","total":1,"v":1,"extra":[1,2],
            |"results":[{"title":"Solo","url":"https://www.wmanhua.com/comic/5.html","cover":"https://c/cover.jpg","author":"A","junk":true}]}""".trimMargin()
        val got = RawContract.parseSearch(body)
        assertEquals(1, got.size)
        assertEquals("Solo", got[0].title)
        assertEquals("https://c/cover.jpg", got[0].cover)
    }

    @Test
    fun testClassifyChapter() {
        val cases = mapOf(
            "https://www.baozimh.com/comic/chapter/naruto/001.html" to "baozimh",
            "https://www.wmanhua.com/chapter/123-1.html" to "wmanhua",
            "https://jjabtoon.xyz/episodes/456" to "jjabtoon",
            "https://www.jjaptoon008.com/chapters/789" to "jjaptoon",
            "https://www.koudaimh.com/manhua/abc/12.html" to "koudaimh",
            "https://goodtoon.top/manga/solo/chapter-3/" to "goodtoon",
            "https://www.manwa.me/chapter/99" to "manwa"
        )
        for ((url, id) in cases) {
            val (src, kind) = RawSources.classify(url)
            assertEquals(url, id, src?.id)
            assertEquals(url, UrlKind.CHAPTER, kind)
        }
    }

    @Test
    fun testClassifySeries() {
        val cases = mapOf(
            "https://www.baozimh.com/comic/naruto/" to "baozimh",
            "https://www.wmanhua.com/comic/123.html" to "wmanhua",
            "https://jjabtoon.xyz/webtoons/456" to "jjabtoon",
            "https://m.koudaimh.com/manhua/abc/" to "koudaimh",
            "https://www.manwa.me/book/77" to "manwa"
        )
        for ((url, id) in cases) {
            val (src, kind) = RawSources.classify(url)
            assertEquals(url, id, src?.id)
            assertEquals(url, UrlKind.SERIES, kind)
        }
    }

    @Test
    fun testClassifyUnknown() {
        val (src, kind) = RawSources.classify("https://example.com/baca/123")
        assertEquals(null, src)
        assertEquals(UrlKind.UNKNOWN, kind)
    }

    @Test
    fun testResolveUrlShape() {
        val api = WorkerDownloadApi("https://worker.example.workers.dev")
        val u = api.resolveUrl("https://www.baozimh.com/comic/x/", "https://www.baozimh.com/")
        assertTrue(u.startsWith("https://worker.example.workers.dev/?url="))
        assertTrue(u.contains("referer="))
    }
}
