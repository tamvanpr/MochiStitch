package com.mochistitch.core.imaging

import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.TimeUnit

/**
 * Lapisan pelengkap ML Kit (ai_studio_code §2 opsi C): congkak piksel
 * bisa buta terhadap teks kecil/pudar/font aneh di dalam balon berekor
 * — pengenal teks menemukannya langsung sebagai bounding box, lepas dari
 * garis pinggir balon.
 *
 * Baris-baris teks DIKELOMPOKKAN menjadi blok (sebaris paragraf balon):
 * garis potong di tengah balon yang kosong — tepat di antara dua baris
 * kalimat — sama salahnya dengan memotong kalimatnya. Kelompok dibentuk
 * dari garis yang bertumpuk horizontal dengan celah vertikal wajar;
 * garis tunggal dapat padding setinggi hurufnya (dinding balon).
 *
 * Hanya dipakai pada ROI verifikasi (bukan seluruh halaman). Gagal-aman:
 * tanpa Play Services / model belum terunduh / galat apa pun -> null
 * (pipeline lama berjalan seperti biasa).
 */
object TextGuard {
    private data class TL(val top: Int, val bottom: Int, val left: Int, val right: Int)

    /**
     * Baris-baris ROI yang mengandung teks ATAU berada di dalam blok teks,
     * plus jumlah model yang BERHASIL berjalan (0 = ML mati total:
     * tanpa Play Services / model belum terunduh / timeout).
     * Pasangan (null, 0) = tak ada info.
     */
    fun textRows(bitmap: Bitmap): Pair<BooleanArray?, Int> {
        if (bitmap.width <= 0 || bitmap.height <= 0) return null to 0
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val lines = mutableListOf<TL>()
            val options = listOf(
                TextRecognizerOptions.DEFAULT_OPTIONS,
                ChineseTextRecognizerOptions.Builder().build(),
                JapaneseTextRecognizerOptions.Builder().build(),
                KoreanTextRecognizerOptions.Builder().build()
            )
            var modelsOk = 0
            for (opt in options) {
                val client = TextRecognition.getClient(opt)
                try {
                    val result = Tasks.await(client.process(image), ML_TIMEOUT_SEC, TimeUnit.SECONDS)
                    modelsOk++
                    for (block in result.textBlocks) {
                        for (line in block.lines) {
                            val box = line.boundingBox ?: continue
                            lines.add(
                                TL(
                                    box.top.coerceIn(0, bitmap.height - 1),
                                    box.bottom.coerceIn(0, bitmap.height - 1),
                                    box.left.coerceIn(0, bitmap.width - 1),
                                    box.right.coerceIn(0, bitmap.width - 1)
                                )
                            )
                        }
                    }
                } catch (t: Throwable) {
                    // Model ini tak jalan di HP ini — lanjut ke model lain.
                } finally {
                    try {
                        client.close()
                    } catch (t: Throwable) {
                    }
                }
            }
            val out = BooleanArray(bitmap.height)
            if (modelsOk == 0) return null to 0
            if (lines.isEmpty()) return out to modelsOk
            lines.sortBy { it.top }
            var gTop = lines[0].top
            var gBot = lines[0].bottom
            var gLeft = lines[0].left
            var gRight = lines[0].right
            var gMaxH = (lines[0].bottom - lines[0].top).coerceAtLeast(1)
            var gCount = 1
            fun flush() {
                val pad = if (gCount == 1) {
                    gMaxH.coerceIn(SINGLE_MIN_PAD, SINGLE_MAX_PAD)
                } else {
                    GROUP_PAD
                }
                val top = (gTop - pad).coerceAtLeast(0)
                val bottom = (gBot + pad).coerceAtMost(bitmap.height - 1)
                for (y in top..bottom) out[y] = true
            }
            for (i in 1 until lines.size) {
                val line = lines[i]
                val curH = (line.bottom - line.top).coerceAtLeast(1)
                val gap = line.top - gBot
                val overlap = minOf(gRight, line.right) - maxOf(gLeft, line.left)
                val minW = minOf(gRight - gLeft, line.right - line.left).coerceAtLeast(1)
                if (gap <= (2.5 * maxOf(gMaxH, curH)).toInt() && overlap * 1.0 / minW > 0.3) {
                    gBot = maxOf(gBot, line.bottom)
                    gLeft = minOf(gLeft, line.left)
                    gRight = maxOf(gRight, line.right)
                    if (curH > gMaxH) gMaxH = curH
                    gCount++
                } else {
                    flush()
                    gTop = line.top
                    gBot = line.bottom
                    gLeft = line.left
                    gRight = line.right
                    gMaxH = curH
                    gCount = 1
                }
            }
            flush()
            out to modelsOk
        } catch (t: Throwable) {
            null to 0
        }
    }

    private const val ML_TIMEOUT_SEC = 8L
    private const val SINGLE_MIN_PAD = 8
    private const val SINGLE_MAX_PAD = 40
    private const val GROUP_PAD = 10
}
