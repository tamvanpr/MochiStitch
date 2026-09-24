package com.mochistitch.core.download

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Kontrak klien unduhan mentah (fase 1: resolve + unduh, tanpa search-UI).
 * Implementasi bicara ke worker Trial Fetch dengan 3 bentuk JSON yang sama
 * untuk sumber ID maupun EN.
 */
interface DownloadApi {
    suspend fun chapters(seriesUrl: String, referer: String = ""): List<ChapterHit>
    suspend fun pages(chapterUrl: String, referer: String = ""): ChapterPages
}

/** Implementasi HTTP via HttpURLConnection (tanpa dependensi baru). */
class WorkerDownloadApi(
    private val baseUrl: String,
    private val timeoutMs: Int = 20_000
) : DownloadApi {

    override suspend fun chapters(seriesUrl: String, referer: String): List<ChapterHit> =
        withContext(Dispatchers.IO) {
            RawContract.parseChapters(get(resolveUrl(seriesUrl, referer)))
        }

    override suspend fun pages(chapterUrl: String, referer: String): ChapterPages =
        withContext(Dispatchers.IO) {
            RawContract.parsePages(get(resolveUrl(chapterUrl, referer)))
        }

    /** `?url=<target>&referer=` — sama persis dengan buildProxyUrl frontend. */
    fun resolveUrl(targetUrl: String, referer: String = ""): String {
        val b = StringBuilder(baseUrl.trimEnd('/')).append("/?url=").append(enc(targetUrl))
        if (referer.isNotBlank()) b.append("&referer=").append(enc(referer))
        return b.toString()
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun get(url: String): String {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "MochiStitch/6")
        }
        try {
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else (c.errorStream ?: c.inputStream)
            val body = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (code !in 200..299) {
                val msg = runCatching { RawContract.checkError(RawJson.parse(body)) }.exceptionOrNull()?.message
                throw RawApiException(msg ?: "Worker HTTP $code")
            }
            return body
        } catch (e: RawApiException) {
            throw e
        } catch (e: IOException) {
            throw RawApiException("Jaringan: ${e.message}")
        } finally {
            c.disconnect()
        }
    }
}

/** Kemajuan unduhan: halaman selesai / total / nama berkas terakhir. */
data class FetchProgress(val done: Int, val total: Int, val lastName: String = "")

/**
 * Pengunduh bytes gambar langsung dari CDN (bukan via worker-proxy agar
 * tidak menghabiskan rate limit worker 120 req/5 mnt).
 *
 * - Streaming ke berkas (satu halaman dalam memori pada satu waktu).
 * - Konkurensi dibatasi [parallel] (default 3, sopan ke sumber).
 * - Tiap halaman di-retry [retries] kali; yang tetap gagal dilaporkan
 *   pemanggil (bukan menggagalkan semuanya).
 */
class PageDownloader(
    private val parallel: Int = 3,
    private val retries: Int = 2,
    private val timeoutMs: Int = 25_000,
    private val maxBytes: Long = 25L * 1024 * 1024
) {
    data class Outcome(val ok: List<File>, val failed: List<String>)

    suspend fun fetchAll(
        pages: List<PageRef>,
        destDir: File,
        headersFor: (pageUrl: String) -> Map<String, String> = { emptyMap() },
        onProgress: (FetchProgress) -> Unit = {}
    ): Outcome = withContext(Dispatchers.IO) {
        destDir.mkdirs()
        val sem = Semaphore(parallel.coerceAtLeast(1))
        val ok = mutableListOf<File>()
        val failed = mutableListOf<String>()
        var done = 0
        val jobs = pages.map { page ->
            async { sem.withPermit { downloadOne(page, destDir, headersFor(page.url)) } }
        }
        jobs.forEach { d ->
            val (file, name) = d.await()
            done++
            if (file != null) {
                synchronized(ok) { ok.add(file) }
                onProgress(FetchProgress(done, pages.size, file.name))
            } else {
                synchronized(failed) { failed.add(name) }
                onProgress(FetchProgress(done, pages.size, name))
            }
        }
        // Urutan natural sesuai nomor halaman.
        Outcome(ok.sortedBy { it.name }, failed.toList())
    }

    private fun downloadOne(page: PageRef, dir: File, headers: Map<String, String>): Pair<File?, String> {
        val ext = guessExt(page.url)
        val name = "%03d_dl%s".format(page.page, ext)
        val dest = File(dir, name)
        var attempt = 0
        var lastErr: Exception? = null
        while (attempt <= retries) {
            try {
                val c = (URL(page.url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = timeoutMs
                    readTimeout = timeoutMs
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36")
                    setRequestProperty("Accept", "image/*,*/*;q=0.8")
                    headers.forEach { (k, v) -> setRequestProperty(k, v) }
                    instanceFollowRedirects = true
                }
                try {
                    if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
                    val tmp = File(dir, "$name.part")
                    var bytes = 0L
                    c.inputStream.use { inp ->
                        tmp.outputStream().use { out ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                val n = inp.read(buf)
                                if (n <= 0) break
                                bytes += n
                                if (bytes > maxBytes) throw IOException("Gambar > ${maxBytes / 1024 / 1024}MB")
                                out.write(buf, 0, n)
                            }
                        }
                    }
                    if (dest.exists()) dest.delete()
                    if (!tmp.renameTo(dest)) {
                        tmp.copyTo(dest, overwrite = true)
                        tmp.delete()
                    }
                    return dest to name
                } finally {
                    c.disconnect()
                }
            } catch (e: Exception) {
                lastErr = e as? Exception ?: IOException("gagal")
                attempt++
            }
        }
        return null to (lastErr?.message?.let { "$name ($it)" } ?: name)
    }

    private fun guessExt(url: String): String {
        val path = url.substringBefore('?').substringBefore('#').lowercase()
        return when {
            path.endsWith(".png") -> ".png"
            path.endsWith(".webp") -> ".webp"
            path.endsWith(".gif") -> ".gif"
            path.endsWith(".bmp") -> ".bmp"
            else -> ".jpg"
        }
    }
}
