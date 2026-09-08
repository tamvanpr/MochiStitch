package com.mochistitch.core.archive

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

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
}
