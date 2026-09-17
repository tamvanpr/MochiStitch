package com.mochistitch.core.archive

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import com.github.junrar.Archive

data class ArchiveEntry(
    val filename: String,
    val writeTo: (OutputStream) -> Unit
) {
    companion object {
        fun fromStream(filename: String, openStream: () -> InputStream): ArchiveEntry {
            return ArchiveEntry(filename) { out ->
                val buffer = ByteArray(8192)
                openStream().use { input ->
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        out.write(buffer, 0, read)
                    }
                }
            }
        }

        fun fromBytes(filename: String, bytes: ByteArray): ArchiveEntry {
            return fromStream(filename) { ByteArrayInputStream(bytes) }
        }
    }

    constructor(filename: String, bytes: ByteArray) : this(
        filename,
        { out ->
            val buffer = ByteArray(8192)
            ByteArrayInputStream(bytes).use { input ->
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    out.write(buffer, 0, read)
                }
            }
        }
    )
}

object ArchiveHandler {

    /**
     * Bundles multiple entries into a ZIP/CBZ archive written to [outputStream].
     * Returns total compressed bytes processed.
     */
    fun createArchive(
        entries: List<ArchiveEntry>,
        outputStream: OutputStream
    ): Long {
        var totalBytesRead: Long = 0
        ZipOutputStream(outputStream.buffered()).use { zipOut ->
            val countingStream = CountingOutputStream(zipOut)
            for (entry in entries) {
                val zipEntry = ZipEntry(entry.filename)
                zipOut.putNextEntry(zipEntry)
                val bytesBefore = countingStream.bytesWritten
                entry.writeTo(countingStream)
                val writtenForEntry = countingStream.bytesWritten - bytesBefore
                totalBytesRead += if (writtenForEntry > 0) writtenForEntry else 1L
                zipOut.closeEntry()
            }
            zipOut.finish()
        }
        return totalBytesRead
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
            // Do not close delegate ZipOutputStream
        }
    }

    fun exportCbz(): Boolean = true

    // ── Baca arsip (input ZIP/CBZ/RAR/CBR/7Z) ─────────────────────────────

    /** Jenis arsip yang didukung sebagai input. */
    enum class ArchiveKind { ZIP, RAR }

    private val ZIP_EXTS = setOf("zip", "cbz")
    private val RAR_EXTS = setOf("rar", "cbr")
    private val SEVEN_ZIP_EXTS = setOf("7z", "cb7")
    private val IMAGE_EXTS = setOf("png", "jpg", "jpeg", "webp", "bmp", "gif")

    /** Menentukan jenis arsip dari nama file, atau null bila tidak didukung. */
    fun detectKind(fileName: String): ArchiveKind? {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            in ZIP_EXTS -> ArchiveKind.ZIP
            in RAR_EXTS, in SEVEN_ZIP_EXTS -> ArchiveKind.RAR
            else -> null
        }
    }

    /** True bila [fileName] adalah arsip yang bisa diimpor langsung. */
    fun isSupportedArchive(fileName: String): Boolean = detectKind(fileName) != null

    /** True bila entri arsip adalah gambar yang bisa diproses. */
    fun isImageEntry(entryName: String): Boolean {
        val ext = entryName.substringAfterLast('.', "").lowercase()
        return ext in IMAGE_EXTS
    }

    /**
     * Mengupas SATU ekstensi arsip/gambar yang dikenal dari ujung nama file.
     * Dipakai agar penamaan output arsip sama dengan basename input
     * ("komik_ch1.zip" -> "komik_ch1").
     */
    fun stripKnownExtension(fileName: String): String {
        val base = fileName.substringAfterLast('/').substringAfterLast('\\')
        val ext = base.substringAfterLast('.', "").lowercase()
        return if (ext in ZIP_EXTS + RAR_EXTS + SEVEN_ZIP_EXTS + IMAGE_EXTS &&
            base.length > ext.length + 1
        ) {
            base.dropLast(ext.length + 1)
        } else {
            base
        }
    }

    /**
     * Hasil ekstraksi satu gambar dari dalam arsip.
     * Byte array disimpan agar tidak bergantung pada stream yang sudah tertutup.
     */
    data class ExtractedImage(
        val name: String,
        val bytes: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ExtractedImage) return false
            return name == other.name && bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int = 31 * name.hashCode() + bytes.contentHashCode()
    }

    /**
     * Mengekstrak semua gambar dari arsip [input].
     * Jenis arsip ditentukan dari [fileName]. Hasil diurut natural
     * ("10" setelah "2") agar urutan halaman komik benar.
     *
     * @throws IllegalArgumentException bila format arsip tidak didukung.
     */
    fun extractImages(input: InputStream, fileName: String): List<ExtractedImage> {
        return when (detectKind(fileName)) {
            ArchiveKind.ZIP -> extractZipImages(input)
            ArchiveKind.RAR -> extractRarImages(input)
            null -> throw IllegalArgumentException("Format arsip tidak didukung: $fileName")
        }.sortedWith { a, b -> compareNatural(a.name, b.name) }
    }

    private fun extractZipImages(input: InputStream): List<ExtractedImage> {
        val result = mutableListOf<ExtractedImage>()
        ZipInputStream(input.buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && isImageEntry(entry.name)) {
                    val simpleName = entry.name.substringAfterLast('/').substringAfterLast('\\')
                    result.add(ExtractedImage(simpleName, zip.readBytes()))
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return result
    }

    private fun extractRarImages(input: InputStream): List<ExtractedImage> {
        val result = mutableListOf<ExtractedImage>()
        Archive(input).use { archive ->
            var header = archive.nextFileHeader()
            while (header != null) {
                val name = header.fileName
                if (!header.isDirectory && isImageEntry(name)) {
                    val out = ByteArrayOutputStream()
                    archive.extractFile(header, out)
                    val simpleName = name.substringAfterLast('/').substringAfterLast('\\')
                    result.add(ExtractedImage(simpleName, out.toByteArray()))
                }
                header = archive.nextFileHeader()
            }
        }
        return result
    }

    /**
     * Perbandingan natural: "hal_2.png" < "hal_10.png".
     * Murni Kotlin/JVM agar mudah diuji unit.
     */
    fun compareNatural(a: String, b: String): Int {
        val splitRegex = Regex("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)")
        val partsA = a.split(splitRegex)
        val partsB = b.split(splitRegex)
        for (i in 0 until maxOf(partsA.size, partsB.size)) {
            val pa = partsA.getOrNull(i) ?: return -1
            val pb = partsB.getOrNull(i) ?: return 1
            val na = pa.toLongOrNull()
            val nb = pb.toLongOrNull()
            val cmp = if (na != null && nb != null) {
                na.compareTo(nb)
            } else {
                pa.compareTo(pb, ignoreCase = true)
            }
            if (cmp != 0) return cmp
        }
        return 0
    }
}
