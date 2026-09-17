package com.mochistitch.core.archive

import com.github.junrar.Archive
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
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

/** Hasil bongkar satu gambar dari arsip. */
data class UnpackedPage(val name: String, val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UnpackedPage) return false
        return name == other.name && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int = 31 * name.hashCode() + bytes.contentHashCode()
}

object ArchiveKit {

    enum class Kind { ZIP, RAR }

    private val ZIP_ENDINGS = setOf("zip", "cbz")
    private val RAR_ENDINGS = setOf("rar", "cbr", "7z", "cb7")
    private val PICTURE_ENDINGS = setOf("png", "jpg", "jpeg", "webp", "bmp", "gif")

    fun kindOf(fileName: String): Kind? {
        return when (fileName.substringAfterLast('.', "").lowercase()) {
            in ZIP_ENDINGS -> Kind.ZIP
            in RAR_ENDINGS -> Kind.RAR
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
        return if (ext in ZIP_ENDINGS + RAR_ENDINGS + PICTURE_ENDINGS && base.length > ext.length + 1) {
            base.dropLast(ext.length + 1)
        } else {
            base
        }
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

    /** Bongkar gambar dari arsip, urut natural ("10" setelah "2"). */
    fun unpack(input: InputStream, fileName: String): List<UnpackedPage> {
        return when (kindOf(fileName)) {
            Kind.ZIP -> unpackZip(input)
            Kind.RAR -> unpackRar(input)
            null -> throw IllegalArgumentException("Arsip tidak didukung: $fileName")
        }.sortedWith { a, b -> naturalOrder(a.name, b.name) }
    }

    private fun unpackZip(input: InputStream): List<UnpackedPage> {
        val out = mutableListOf<UnpackedPage>()
        ZipInputStream(input.buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && isPicture(entry.name)) {
                    out.add(UnpackedPage(entry.name.substringAfterLast('/').substringAfterLast('\\'), zip.readBytes()))
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return out
    }

    private fun unpackRar(input: InputStream): List<UnpackedPage> {
        val out = mutableListOf<UnpackedPage>()
        Archive(input).use { archive ->
            var header = archive.nextFileHeader()
            while (header != null) {
                val name = header.fileName
                if (!header.isDirectory && isPicture(name)) {
                    val buf = ByteArrayOutputStream()
                    archive.extractFile(header, buf)
                    out.add(UnpackedPage(name.substringAfterLast('/').substringAfterLast('\\'), buf.toByteArray()))
                }
                header = archive.nextFileHeader()
            }
        }
        return out
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
