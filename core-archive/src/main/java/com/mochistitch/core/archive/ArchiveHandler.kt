package com.mochistitch.core.archive

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class ArchiveEntry(
    val filename: String,
    val openStream: () -> InputStream
) {
    constructor(filename: String, bytes: ByteArray) : this(filename, { ByteArrayInputStream(bytes) })
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
            val buffer = ByteArray(8192)
            for (entry in entries) {
                val zipEntry = ZipEntry(entry.filename)
                zipOut.putNextEntry(zipEntry)

                entry.openStream().use { input ->
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        zipOut.write(buffer, 0, read)
                        totalBytesRead += read
                    }
                }
                zipOut.closeEntry()
            }
            zipOut.finish()
        }
        return totalBytesRead
    }

    fun exportCbz(): Boolean = true
}
