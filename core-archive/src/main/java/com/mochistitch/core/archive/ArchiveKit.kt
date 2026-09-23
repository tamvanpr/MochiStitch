package com.mochistitch.core.archive

import com.github.junrar.Archive
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Satu berkas untuk dibundel ke arsip. */
data class ArchiveItem(val filename: String, val writeTo: (OutputStream) -> Unit) {
    companion object {
        fun ofBytes(filename: String, bytes: ByteArray): ArchiveItem =
            ArchiveItem(filename) { out -> out.write(bytes) }
    }

    constructor(filename: String, bytes: ByteArray) : this(
        filename,
        { out -> ByteArrayInputStream(bytes).use { it.copyTo(out) } }
    )
}

/** Hasil bongkar satu gambar dari arsip (dalam memori). */
data class UnpackedPage(val name: String, val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UnpackedPage) return false
        return name == other.name && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int = 31 * name.hashCode() + bytes.contentHashCode()
}

/** Hasil bongkar satu gambar ke berkas di disk (hemat memori). */
data class UnpackedFile(val file: File, val name: String)

object ArchiveKit {

    enum class Kind { ZIP, RAR, SEVEN }

    private val ZIP_ENDINGS = setOf("zip", "cbz")
    private val RAR_ENDINGS = setOf("rar", "cbr")
    private val SEVEN_ENDINGS = setOf("7z", "cb7")
    private val PICTURE_ENDINGS = setOf("png", "jpg", "jpeg", "webp", "bmp", "gif")

    fun kindOf(fileName: String): Kind? {
        return when (fileName.substringAfterLast('.', "").lowercase()) {
            in ZIP_ENDINGS -> Kind.ZIP
            in RAR_ENDINGS -> Kind.RAR
            in SEVEN_ENDINGS -> Kind.SEVEN
            else -> null
        }
    }

    fun canOpen(fileName: String): Boolean = kindOf(fileName) != null

    fun isPicture(entryName: String): Boolean =
        entryName.substringAfterLast('.', "").lowercase() in PICTURE_ENDINGS

    /** "komik.zip" -> "komik". */
    fun baseNameOf(fileName: String): String {
        val base = fileName.substringAfterLast('/').substringAfterLast('\\')
        val ext = base.substringAfterLast('.', "").lowercase()
        return if (ext in ZIP_ENDINGS + RAR_ENDINGS + SEVEN_ENDINGS + PICTURE_ENDINGS && base.length > ext.length + 1) {
            base.dropLast(ext.length + 1)
        } else {
            base
        }
    }

    /** AMAN dipakai untuk nama folder/berkas di disk. */
    fun sanitizeName(raw: String): String {
        val cleaned = raw.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().trim('.')
        return cleaned.ifBlank { "halaman" }
    }

    /** Bundel entri menjadi ZIP/CBZ. */
    fun pack(items: List<ArchiveItem>, output: OutputStream): Long {
        var total = 0L
        ZipOutputStream(output.buffered()).use { zip ->
            for (item in items) {
                zip.putNextEntry(ZipEntry(item.filename))
                val meter = MeteredStream(zip)
                item.writeTo(meter)
                total += meter.count
                zip.closeEntry()
            }
            zip.finish()
        }
        return total
    }

    /**
     * Bongkar gambar ke memori, urut natural ("10" setelah "2").
     * Satu arsip penuh muat di RAM — untuk arsip besar pakai [unpackTo].
     */
    fun unpack(input: InputStream, fileName: String): List<UnpackedPage> {
        val out = mutableListOf<UnpackedPage>()
        forEachPage(input, fileName) { name, write ->
            val buf = ByteArrayOutputStream()
            write(buf)
            out.add(UnpackedPage(name, buf.toByteArray()))
        }
        return out.sortedWith { a, b -> naturalOrder(a.name, b.name) }
    }

    /**
     * Bongkar gambar langsung ke [destDir] (streaming, satu halaman
     * dalam memori pada satu waktu). Berkas ditulis berurutan natural
     * dengan awalan nomor 001_, 002_, ... Mengembalikan daftar berkas
     * tersebut beserta nama entri aslinya.
     */
    fun unpackTo(input: InputStream, fileName: String, destDir: File): List<UnpackedFile> {
        destDir.mkdirs()
        val staged = mutableListOf<Pair<String, File>>()
        var i = 0
        forEachPage(input, fileName) { name, write ->
            i++
            val part = File(destDir, "staging_$i.part")
            part.outputStream().use { out -> write(out) }
            staged.add(name to part)
        }
        staged.sortWith { a, b -> naturalOrder(a.first, b.first) }
        return staged.mapIndexed { index, (name, part) ->
            val dest = File(destDir, "%03d_%s".format(index + 1, sanitizeName(name)))
            if (dest.exists()) dest.delete()
            if (!part.renameTo(dest)) {
                part.copyTo(dest, overwrite = true)
                part.delete()
            }
            UnpackedFile(dest, name)
        }
    }

    /**
     * Jalankan [consume] untuk tiap gambar di arsip. [consume] menerima
     * nama entri (basename) dan penulis yang menyalin isi entri ke
     * OutputStream — konsumen wajib menghabisi penulis sebelum kembali.
     */
    private fun forEachPage(
        input: InputStream,
        fileName: String,
        consume: (name: String, write: (OutputStream) -> Unit) -> Unit
    ) {
        when (kindOf(fileName)) {
            Kind.ZIP -> forEachZip(input, consume)
            Kind.RAR -> forEachRar(input, consume)
            Kind.SEVEN -> forEachSeven(input, consume)
            null -> throw IllegalArgumentException("Arsip tidak didukung: $fileName")
        }
    }

    private fun forEachZip(
        input: InputStream,
        consume: (name: String, write: (OutputStream) -> Unit) -> Unit
    ) {
        ZipInputStream(input.buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && isPicture(entry.name)) {
                    val name = entry.name.substringAfterLast('/').substringAfterLast('\\')
                    consume(name) { out -> zip.copyTo(out) }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    private fun forEachRar(
        input: InputStream,
        consume: (name: String, write: (OutputStream) -> Unit) -> Unit
    ) {
        Archive(input).use { archive ->
            var header = archive.nextFileHeader()
            while (header != null) {
                val full = header.fileName
                if (!header.isDirectory && isPicture(full)) {
                    // Junrar hanya mengekstrak ke OutputStream: satu halaman
                    // dalam memori pada satu waktu, bukan seluruh arsip.
                    val buf = ByteArrayOutputStream()
                    archive.extractFile(header, buf)
                    val name = full.substringAfterLast('/').substringAfterLast('\\')
                    val bytes = buf.toByteArray()
                    consume(name) { out -> out.write(bytes) }
                }
                header = archive.nextFileHeader()
            }
        }
    }

    private fun forEachSeven(
        input: InputStream,
        consume: (name: String, write: (OutputStream) -> Unit) -> Unit
    ) {
        // SevenZFile butuh akses acak: salin ke berkas sementara dulu.
        val tmp = File.createTempFile("mochi_7z", ".7z")
        try {
            tmp.outputStream().use { out -> input.copyTo(out) }
            java.io.FileInputStream(tmp).use { fis ->
                SevenZFile(fis.channel).use { seven ->
                    var entry = seven.getNextEntry()
                    while (entry != null) {
                        if (!entry.isDirectory && isPicture(entry.name)) {
                            val name = entry.name.substringAfterLast('/').substringAfterLast('\\')
                            consume(name) { out -> seven.copyEntryTo(out) }
                        }
                        entry = seven.getNextEntry()
                    }
                }
            }
        } finally {
            try { tmp.delete() } catch (t: Throwable) { }
        }
    }

    /** Salin isi entri 7z yang sedang aktif sampai habis. */
    private fun SevenZFile.copyEntryTo(out: OutputStream) {
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = read(buf, 0, buf.size)
            if (n <= 0) break
            out.write(buf, 0, n)
        }
    }

    fun naturalOrder(a: String, b: String): Int {
        val splitter = Regex("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)")
        val left = a.split(splitter)
        val right = b.split(splitter)
        for (i in 0 until maxOf(left.size, right.size)) {
            val x = left.getOrNull(i) ?: return -1
            val y = right.getOrNull(i) ?: return 1
            val nx = x.toLongOrNull()
            val ny = y.toLongOrNull()
            val cmp = if (nx != null && ny != null) nx.compareTo(ny) else x.compareTo(y, ignoreCase = true)
            if (cmp != 0) return cmp
        }
        return 0
    }

    private class MeteredStream(private val inner: OutputStream) : OutputStream() {
        var count: Long = 0
            private set

        override fun write(b: Int) {
            inner.write(b)
            count++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            inner.write(b, off, len)
            count += len
        }

        override fun flush() {
            inner.flush()
        }

        override fun close() {
            // Milik ZipOutputStream induk.
        }
    }
}
