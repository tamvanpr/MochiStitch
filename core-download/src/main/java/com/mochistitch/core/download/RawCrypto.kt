package com.mochistitch.core.download

import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Kripto untuk sumber terenkripsi (porting crypto.subtle worker):
 * - manwa.me: tiap gambar AES-128-CBC, key = IV = "my2ecret782ecret".
 * - koudaimh: blob `params` base64url, 16 byte pertama = IV, sisanya
 *   ciphertext, key = "5V&RoR%Jf@pJPydF", hasil plaintext JSON.
 *
 * javax.crypto ada di Android maupun JVM (unit-testable).
 */
object RawCrypto {

    const val MANWA_KEY = "my2ecret782ecret"
    const val KOUDAIMH_KEY = "5V&RoR%Jf@pJPydF"

    fun aesCbcDecrypt(key: ByteArray, iv: ByteArray, ciphertext: ByteArray): ByteArray {
        require(key.size == 16) { "Key harus 16 byte (AES-128)." }
        require(iv.size == 16) { "IV harus 16 byte." }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(ciphertext)
    }

    fun aesCbcEncrypt(key: ByteArray, iv: ByteArray, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(plaintext)
    }

    /** Gambar manwa.me: dekripsi penuh satu blob. */
    fun decryptManwaImage(encrypted: ByteArray): ByteArray {
        val k = MANWA_KEY.toByteArray(Charsets.UTF_8)
        return aesCbcDecrypt(k, k, encrypted)
    }

    /**
     * Placeholder 1px CDN koudaimh (HTTP 200 berisi dummy): hash SHA-256
     * yang dikenal dari frontend (`KNOWN_PLACEHOLDER_HASHES`).
     */
    private const val KOUDAIMH_PLACEHOLDER_SHA256 =
        "4ea088b69b1c9e7d0e394e4278b922c0b0988e6a33c2cd796dd7d4c9a6860dfb"

    fun isKoudaimhPlaceholder(bytes: ByteArray): Boolean {
        if (bytes.size >= 5000) return false
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
        val hex = digest.joinToString("") { "%02x".format(it) }
        return hex == KOUDAIMH_PLACEHOLDER_SHA256
    }

    /** Blob `params` koudaimh -> JSON plaintext (base64url, IV = 16 byte pertama). */
    fun decryptKoudaimhParams(blob: String): String {
        var b64 = blob.replace("\\s+".toRegex(), "").replace("-", "+").replace("_", "/")
        b64 += "=".repeat((4 - b64.length % 4) % 4)
        val raw = try {
            java.util.Base64.getDecoder().decode(b64)
        } catch (e: IllegalArgumentException) {
            throw RawApiException("Blob koudaimh bukan base64 valid.")
        }
        if (raw.size <= 16) throw RawApiException("Blob koudaimh terlalu pendek.")
        val plain = aesCbcDecrypt(
            KOUDAIMH_KEY.toByteArray(Charsets.UTF_8),
            raw.copyOfRange(0, 16),
            raw.copyOfRange(16, raw.size)
        )
        return plain.toString(Charsets.UTF_8)
    }
}
