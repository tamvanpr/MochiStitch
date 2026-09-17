package com.mochistitch.core.archive

import com.github.junrar.Archive
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Tulis + baca arsip komik (ZIP/CBZ/RAR/CBR/7Z).
 * Murni JVM — bisa diuji unit penuh.
 */
data class ArchiveEntry(
    val filename: String,
    val writeTo: (OutputStream) -> Unit
) {
    companion object {
        fun fromBytes(filename: String, bytes: ByteArray): ArchiveEntry =
            ArchiveEntry(filename) { out -> out.write(bytes) }
    }

    constructor(filename: String, bytes: ByteArray) : this(
        filename,
        { out ->
            ByteArrayInputStream(bytes).use { input -> input.copyTo(out) }
        }
    )
}

object ArchiveHandler {

    enum class ArchiveKind { ZIP, RAR }

    private val ZIP_EXTS = setOf("zip", "cbz")
    private val RAR_EXTS = setOf("rar", "cbr", "7z", "cb7")
    private val IMAGE_EXTS = setOf("png", "jpg", "jpeg", "webp", "bmp", "gif")

    fun detectKind(fileName: String): ArchiveKind? {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            in ZIP_EXTS -> ArchiveKind.ZIP
            in RAR_EXTS -> ArchiveKind.RAR
            else -> null
        }
    }

    fun isSupportedArchive(fileName: String): Boolean = detectKind(fileName) != null

    fun isImageEntry(entryName: String): Boolean =
        entryName.substringAfterLast('.', "").lowercase() in IMAGE_EXTS

    /** Kupas satu ekstensi arsip/gambar yang dikenal ("a.zip" -> "a"). */
    fun stripKnownExtension(fileName: String): String {
        val base = fileName.substringAfterLast('/').substringAfterLast('\\')
        val ext = base.substringAfterLast('.', "").lowercase()
        return if (ext in ZIP_EXTS + RAR_EXTS + IMAGE_EXTS && base.length > ext.length + 1) {
            base.dropLast(ext.length + 1)
        } else {
            base
        }
    }

    data class ExtractedImage(val name: String, val bytes: ByteArray) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ExtractedImage) return false
            return name == other.name && bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int = 31 * name.hashCode() + bytes.contentHashCode()
    }

    /** Tulis daftar entri menjadi arsip ZIP/CBZ. Mengembalikan byte terproses. */
    fun createArchive(entries: List<ArchiveEntry>, outputStream: OutputStream): Long {
        var total = 0L
        ZipOutputStream(outputStream.buffered()).use { zipOut ->
            for (entry in entries) {
                zipOut.putNextEntry(ZipEntry(entry.filename))
                val counter = CountingOutputStream(zipOut)
                entry.writeTo(counter)
                total += counter.bytesWritten
                zipOut.closeEntry()
            }
            zipOut.finish()
        }
        return total
    }

    /**
     * Ekstrak semua gambar dari arsip, diurut natural ("10" setelah "2").
     * @throws IllegalArgumentException bila format tidak didukung.
     */
    fun extractImages(input: InputStream, fileName: String): List<ExtractedImage> {
        return when (detectKind(fileName)) {
            ArchiveKind.ZIP -> extractZip(input)
            ArchiveKind.RAR -> extractRar(input)
            null -> throw IllegalArgumentException("Format arsip tidak didukung: $fileName")
        }.sortedWith { a, b -> compareNatural(a.name, b.name) }
    }

    private fun extractZip(input: InputStream): List<ExtractedImage> {
        val result = mutableListOf<ExtractedImage>()
        ZipInputStream(input.buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && isImageEntry(entry.name)) {
                    val simple = entry.name.substringAfterLast('/').substringAfterLast('\\')
                    result.add(ExtractedImage(simple, zip.readBytes()))
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return result
    }

    private fun extractRar(input: InputStream): List<ExtractedImage> {
        val result = mutableListOf<ExtractedImage>()
        Archive(input).use { archive ->
            var header = archive.nextFileHeader()
            while (header != null) {
                val name = header.fileName
                if (!header.isDirectory && isImageEntry(name)) {
                    val out = ByteArrayOutputStream()
                    archive.extractFile(header, out)
                    result.add(ExtractedImage(name.substringAfterLast('/').substringAfterLast('\\'), out.toByteArray()))
                }
                header = archive.nextFileHeader()
            }
        }
        return result
    }

    /** Perbandingan natural: "hal_2" < "hal_10". */
    fun compareNatural(a: String, b: String): Int {
        val split = Regex("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)")
        val pa = a.split(split)
        val pb = b.split(split)
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrNull(i) ?: return -1
            val y = pb.getOrNull(i) ?: return 1
            val nx = x.toLongOrNull()
            val ny = y.toLongOrNull()
            val cmp = if (nx != null && ny != null) nx.compareTo(ny) else x.compareTo(y, ignoreCase = true)
            if (cmp != 0) return cmp
        }
        return 0
    }

    private class CountingOutputStream(private val delegate: OutputStream) : OutputStream() {
        var bytesWritten: Long = 0
            private set

        override fun write(b: Int) {
            delegate.write(b)
            bytesWritten++
        }

        override fun write(b: ByteArray) {
            delegate.write(b)
            bytesWritten += b.size
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            delegate.write(b, off, len)
            bytesWritten += len
        }

        override fun flush() {
            delegate.flush()
        }

        override fun close() {
            // Jangan tutup ZipOutputStream induk.
        }
    }
}
