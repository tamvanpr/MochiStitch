package com.mochistitch.core.download

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Header UA standar untuk resolve langsung (sama seperti worker). */
const val DIRECT_UA_MOBILE =
    "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
const val DIRECT_UA_DESKTOP =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"

/**
 * HTTP langsung tanpa dependensi (porting safeFetch worker versi ringkas:
 * timeout, redirect mengikuti HttpURLConnection, HTTP non-2xx jadi
 * RawApiException).
 */
object DirectHttp {

    fun getText(url: String, headers: Map<String, String> = emptyMap(), timeoutMs: Int = 20_000): String {
        val c = open(url, "GET", headers, timeoutMs)
        try {
            val code = c.responseCode
            if (code !in 200..299) throw RawApiException("HTTP $code dari $url")
            return streamText(c, code)
        } catch (e: RawApiException) {
            throw e
        } catch (e: IOException) {
            throw RawApiException("Jaringan: ${e.message}")
        } finally {
            c.disconnect()
        }
    }

    fun postJson(url: String, headers: Map<String, String> = emptyMap(), body: String = "{}", timeoutMs: Int = 20_000): String {
        val c = open(url, "POST", headers, timeoutMs)
        try {
            c.doOutput = true
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            if (code !in 200..299) throw RawApiException("HTTP $code dari $url")
            return streamText(c, code)
        } catch (e: RawApiException) {
            throw e
        } catch (e: IOException) {
            throw RawApiException("Jaringan: ${e.message}")
        } finally {
            c.disconnect()
        }
    }

    fun getBytes(url: String, headers: Map<String, String> = emptyMap(), timeoutMs: Int = 25_000, maxBytes: Long = 25L * 1024 * 1024): ByteArray {
        val c = open(url, "GET", headers, timeoutMs)
        try {
            val code = c.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            val out = java.io.ByteArrayOutputStream()
            var total = 0L
            c.inputStream.use { inp ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = inp.read(buf)
                    if (n <= 0) break
                    total += n
                    if (total > maxBytes) throw IOException("Respons > ${maxBytes / 1024 / 1024}MB")
                    out.write(buf, 0, n)
                }
            }
            return out.toByteArray()
        } finally {
            c.disconnect()
        }
    }

    private fun open(url: String, method: String, headers: Map<String, String>, timeoutMs: Int): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = timeoutMs
        c.readTimeout = timeoutMs
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", DIRECT_UA_MOBILE)
        headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        return c
    }

    private fun streamText(c: HttpURLConnection, code: Int): String {
        val stream = if (code in 200..299) c.inputStream else (c.errorStream ?: c.inputStream)
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    fun htmlHeaders(referer: String, desktop: Boolean = false, lang: String? = null): Map<String, String> {
        val m = mutableMapOf(
            "User-Agent" to if (desktop) DIRECT_UA_DESKTOP else DIRECT_UA_MOBILE,
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Referer" to referer
        )
        if (lang != null) m["Accept-Language"] = lang
        return m
    }

    fun apiHeaders(referer: String? = null): Map<String, String> {
        val m = mutableMapOf(
            "User-Agent" to DIRECT_UA_MOBILE,
            "Accept" to "application/json, text/plain, */*"
        )
        if (referer != null) m["Referer"] = referer
        return m
    }
}
