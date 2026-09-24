package com.mochistitch.core.download

/** Galat API worker (HTTP non-2xx atau body `{"error":...}`). */
class RawApiException(message: String) : Exception(message)

// ── Parser JSON minimal (tanpa dependensi; org.json tak layak diuji di JVM
// karena android.jar unit-test mengembalikan default). Mendukung objek,
// array, string (escape umum + \uXXXX), angka, true/false/null. ──────────

sealed interface JVal {
    data class Obj(val map: Map<String, JVal>) : JVal
    data class Arr(val items: List<JVal>) : JVal
    data class Str(val v: String) : JVal
    data class Num(val v: Double) : JVal
    data object True : JVal
    data object False : JVal
    data object Null : JVal
}

fun JVal.obj(key: String): JVal.Obj? = (this as? JVal.Obj)?.map?.get(key) as? JVal.Obj
fun JVal.arr(key: String): List<JVal>? = ((this as? JVal.Obj)?.map?.get(key) as? JVal.Arr)?.items
fun JVal.str(key: String, default: String = ""): String {
    val v = (this as? JVal.Obj)?.map?.get(key) ?: return default
    return when (v) {
        is JVal.Str -> v.v
        is JVal.Num -> if (v.v % 1.0 == 0.0) v.v.toLong().toString() else v.v.toString()
        is JVal.True -> "true"
        is JVal.False -> "false"
        else -> default
    }
}
fun JVal.int(key: String, default: Int = 0): Int {
    val v = (this as? JVal.Obj)?.map?.get(key) ?: return default
    return when (v) {
        is JVal.Num -> v.v.toInt()
        is JVal.Str -> v.v.toIntOrNull() ?: default
        else -> default
    }
}

object RawJson {
    fun parse(text: String): JVal {
        val p = Parser(text)
        val v = p.value()
        p.ws()
        if (!p.end()) throw RawApiException("JSON berlebih setelah nilai utama.")
        return v
    }

    private class Parser(val s: String) {
        var i = 0
        fun end(): Boolean = i >= s.length
        fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun err(msg: String): Nothing = throw RawApiException("JSON tidak valid: $msg (pos $i)")

        fun value(): JVal {
            ws()
            if (end()) err("akhir tak terduga")
            return when (s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> JVal.Str(str())
                't' -> lit("true", JVal.True)
                'f' -> lit("false", JVal.False)
                'n' -> lit("null", JVal.Null)
                '-', in '0'..'9' -> num()
                else -> err("karakter '${s[i]}'")
            }
        }

        fun lit(word: String, v: JVal): JVal {
            if (!s.startsWith(word, i)) err("literal '$word'")
            i += word.length
            return v
        }

        fun obj(): JVal.Obj {
            i++ // {
            val m = LinkedHashMap<String, JVal>()
            ws()
            if (!end() && s[i] == '}') { i++; return JVal.Obj(m) }
            while (true) {
                ws()
                if (end() || s[i] != '"') err("kunci string")
                val k = str()
                ws()
                if (end() || s[i] != ':') err("':'")
                i++
                m[k] = value()
                ws()
                if (end()) err("objek tak tertutup")
                if (s[i] == ',') { i++; continue }
                if (s[i] == '}') { i++; break }
                err("',' atau '}'")
            }
            return JVal.Obj(m)
        }

        fun arr(): JVal.Arr {
            i++ // [
            val out = mutableListOf<JVal>()
            ws()
            if (!end() && s[i] == ']') { i++; return JVal.Arr(out) }
            while (true) {
                out.add(value())
                ws()
                if (end()) err("array tak tertutup")
                if (s[i] == ',') { i++; continue }
                if (s[i] == ']') { i++; break }
                err("',' atau ']'")
            }
            return JVal.Arr(out)
        }

        fun str(): String {
            i++ // "
            val b = StringBuilder()
            while (true) {
                if (i >= s.length) err("string tak tertutup")
                val c = s[i++]
                if (c == '"') break
                if (c == '\\') {
                    if (i >= s.length) err("escape gantung")
                    when (val e = s[i++]) {
                        '"', '\\', '/' -> b.append(e)
                        'b' -> b.append('\b')
                        'f' -> b.append('\u000C')
                        'n' -> b.append('\n')
                        'r' -> b.append('\r')
                        't' -> b.append('\t')
                        'u' -> {
                            if (i + 4 > s.length) err("\\u pendek")
                            b.append(s.substring(i, i + 4).toIntOrNull(16)?.toChar() ?: err("\\u"))
                            i += 4
                        }
                        else -> err("escape '\\$e'")
                    }
                } else b.append(c)
            }
            return b.toString()
        }

        fun num(): JVal.Num {
            val st = i
            if (!end() && s[i] == '-') i++
            while (!end() && (s[i].isDigit() || s[i] == '.' || s[i] == 'e' || s[i] == 'E' || s[i] == '+' || s[i] == '-')) i++
            return JVal.Num(s.substring(st, i).toDoubleOrNull() ?: err("angka"))
        }
    }
}

// ── Bentuk kontrak worker (toleran: field hilang = default, field asing
// diabaikan; body {"error"} selalu jadi RawApiException). ────────────────

object RawContract {

    fun checkError(root: JVal): JVal {
        val err = (root as? JVal.Obj)?.map?.get("error")
        if (err is JVal.Str && err.v.isNotBlank()) {
            val detail = root.str("detail")
            throw RawApiException(if (detail.isBlank()) err.v else "${err.v}: $detail")
        }
        return root
    }

    fun parseSearch(body: String): List<SeriesHit> {
        val root = checkError(RawJson.parse(body))
        return root.arr("results").orEmpty().mapNotNull { item ->
            val url = item.str("url")
            if (url.isBlank()) null
            else SeriesHit(
                title = item.str("title", url),
                url = url,
                cover = item.str("cover"),
                author = item.str("author")
            )
        }
    }

    fun parseChapters(body: String): List<ChapterHit> {
        val root = checkError(RawJson.parse(body))
        return root.arr("chapters").orEmpty().mapNotNull { item ->
            val url = item.str("url")
            if (url.isBlank()) null
            else ChapterHit(
                id = item.str("chapter_id", url),
                title = item.str("chapter_title").ifBlank { item.str("title", url) },
                url = url
            )
        }
    }

    fun parsePages(body: String): ChapterPages {
        val root = checkError(RawJson.parse(body))
        val items = root.arr("images").orEmpty()
        val pages = items.mapIndexedNotNull { idx, item ->
            val url = item.str("url")
            if (url.isBlank()) null
            else PageRef(page = item.int("page", idx + 1), url = url)
        }.sortedBy { it.page }
        val title = root.str("chapter_title").ifBlank {
            root.str("comic_title").ifBlank { root.str("page_title", "Unduhan") }
        }
        return ChapterPages(source = root.str("source"), title = title, pages = pages)
    }
}
