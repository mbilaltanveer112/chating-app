package com.example.security

import android.util.Base64
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class HashedSecret(
    val hash: String,
    val salt: String
)

object PinCryptoUtils {
    private const val PBKDF2_ITERATIONS = 100_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16
    private const val ALGO_SHA256 = "PBKDF2WithHmacSHA256"
    private const val ALGO_SHA1 = "PBKDF2WithHmacSHA1"
    private const val PREFIX_SHA256 = "pbkdf2_sha256:"
    private const val PREFIX_SHA1 = "pbkdf2_sha1:"

    private val random = SecureRandom()

    fun isValidPinFormat(pin: String): Boolean =
        pin.length == 6 && pin.all { it.isDigit() }

    fun hashSecret(secret: String): HashedSecret {
        val saltBytes = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val saltB64 = encodeBase64(saltBytes)
        val normalized = secret.trim()
        val (prefix, derived) = deriveKey(normalized, saltBytes, preferSha256 = true)
        return HashedSecret(
            hash = prefix + encodeBase64(derived),
            salt = saltB64
        )
    }

    fun verifySecret(candidate: String, storedHash: String, storedSalt: String): Boolean {
        if (storedHash.isBlank() || storedSalt.isBlank()) return false
        val saltBytes = runCatching { decodeBase64(storedSalt) }.getOrNull() ?: return false
        val normalized = candidate.trim()
        val useSha256 = !storedHash.startsWith(PREFIX_SHA1)
        val expectedBytes = runCatching {
            val rawB64 = storedHash
                .removePrefix(PREFIX_SHA256)
                .removePrefix(PREFIX_SHA1)
            decodeBase64(rawB64)
        }.getOrNull() ?: return false

        val (_, actualBytes) = deriveKey(normalized, saltBytes, preferSha256 = useSha256)
        return MessageDigest.isEqual(expectedBytes, actualBytes)
    }

    fun generateRecoveryCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val chars = CharArray(12) { alphabet[random.nextInt(alphabet.length)] }
        return "${chars.sliceArray(0..3).concatToString()}-${chars.sliceArray(4..7).concatToString()}-${chars.sliceArray(8..11).concatToString()}"
    }

    private fun deriveKey(secret: String, salt: ByteArray, preferSha256: Boolean): Pair<String, ByteArray> {
        val chars = secret.toCharArray()
        val spec = PBEKeySpec(chars, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        try {
            if (preferSha256) {
                try {
                    val factory = SecretKeyFactory.getInstance(ALGO_SHA256)
                    return PREFIX_SHA256 to factory.generateSecret(spec).encoded
                } catch (_: NoSuchAlgorithmException) {
                    // Fallback for API 24-25 runtimes without PBKDF2WithHmacSHA256
                }
            }
            val factory = SecretKeyFactory.getInstance(ALGO_SHA1)
            return PREFIX_SHA1 to factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
            chars.fill('\u0000')
        }
    }

    private fun encodeBase64(bytes: ByteArray): String =
        java.util.Base64.getEncoder().encodeToString(bytes)

    private fun decodeBase64(value: String): ByteArray =
        try {
            java.util.Base64.getDecoder().decode(value)
        } catch (_: IllegalArgumentException) {
            Base64.decode(value, Base64.NO_WRAP)
        }
}
