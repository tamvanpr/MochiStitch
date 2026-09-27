package com.mochistitch.core.imaging

import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.TimeUnit

/**
 * Lapisan pelengkap ML Kit (ai_studio_code §2 opsi C): congkak piksel
 * bisa buta terhadap teks kecil/pudar/font aneh di dalam balon berekor
 * — pengenal teks menemukannya langsung sebagai bounding box, lepas dari
 * garis pinggir balon.
 *
 * Hanya dipakai pada ROI verifikasi (bukan seluruh halaman). Gagal-aman:
 * tanpa Play Services / model belum terunduh / galat apa pun -> null
 * (pipeline lama berjalan seperti biasa).
 */
object TextGuard {
    /**
     * Baris-baris ROI yang mengandung teks (sudah termasuk [padding]).
     * null = tak tersedia (perlakukan sebagai "tak ada info").
     */
    fun textRows(bitmap: Bitmap, padding: Int = 6): BooleanArray? {
        if (bitmap.width <= 0 || bitmap.height <= 0) return null
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val out = BooleanArray(bitmap.height)
            val options = listOf(
                TextRecognizerOptions.DEFAULT_OPTIONS,
                ChineseTextRecognizerOptions.Builder().build()
            )
            for (opt in options) {
                val client = TextRecognition.getClient(opt)
                try {
                    val result = Tasks.await(client.process(image), 15, TimeUnit.SECONDS)
                    for (block in result.textBlocks) {
                        for (line in block.lines) {
                            val box = line.boundingBox ?: continue
                            val top = (box.top - padding).coerceAtLeast(0)
                            val bottom = (box.bottom + padding).coerceAtMost(bitmap.height - 1)
                            for (y in top..bottom) out[y] = true
                        }
                    }
                } finally {
                    try {
                        client.close()
                    } catch (t: Throwable) {
                    }
                }
            }
            out
        } catch (t: Throwable) {
            null
        }
    }
}
