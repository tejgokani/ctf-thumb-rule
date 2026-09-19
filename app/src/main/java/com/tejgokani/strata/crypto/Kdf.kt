package com.tejgokani.strata.crypto

import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Key derivation. PBKDF2-HMAC-SHA256 rather than Argon2id (see plan §3 — Argon2 needs a native
 * lib, which is an NDK/ABI risk in a one-pass build). Iteration count is stored alongside the
 * salt so it can be upgraded later without breaking existing vaults.
 */
object Kdf {
    const val DEFAULT_ITERATIONS = 600_000
    const val KEY_LENGTH_BITS = 256
    const val SALT_LENGTH_BYTES = 16

    private val secureRandom = SecureRandom()

    fun randomSalt(): ByteArray = ByteArray(SALT_LENGTH_BYTES).also { secureRandom.nextBytes(it) }

    /** Derives the 32-byte master key from a user passphrase. Caller should zero [passphrase] after use. */
    fun deriveMasterKey(passphrase: CharArray, salt: ByteArray, iterations: Int = DEFAULT_ITERATIONS): ByteArray {
        val spec = PBEKeySpec(passphrase, salt, iterations, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val key = factory.generateSecret(spec)
        val encoded = key.encoded
        spec.clearPassword()
        return encoded
    }

    /**
     * HKDF-SHA256 (RFC 5869). Used to derive domain-separated subkeys from the master key — e.g.
     * a "wrap file DEKs" key and a "seal appProperties" key are different keys derived from the
     * same master secret, so a compromise of one purpose's key material doesn't help against
     * another. This is a hygiene measure only; it is unrelated to (and does not reintroduce)
     * content-derived/convergent keys, which the plan explicitly rejects for chunk encryption.
     */
    fun hkdfSha256(ikm: ByteArray, info: ByteArray, length: Int = 32): ByteArray {
        val salt = ByteArray(32) // zero salt is acceptable per RFC 5869 when ikm is already high-entropy
        val extractMac = Mac.getInstance("HmacSHA256")
        extractMac.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = extractMac.doFinal(ikm)

        val expandMac = Mac.getInstance("HmacSHA256")
        expandMac.init(SecretKeySpec(prk, "HmacSHA256"))
        val n = (length + 31) / 32
        var t = ByteArray(0)
        val okm = ByteArray(n * 32)
        var offset = 0
        for (i in 1..n) {
            expandMac.reset()
            expandMac.update(t)
            expandMac.update(info)
            expandMac.update(i.toByte())
            t = expandMac.doFinal()
            System.arraycopy(t, 0, okm, offset, t.size)
            offset += t.size
        }
        return okm.copyOf(length)
    }

    // Domain-separation labels — never reuse a label across purposes.
    val INFO_DEK_WRAP = "strata-dek-wrap-v1".toByteArray()
    val INFO_APP_PROPS = "strata-appprops-v1".toByteArray()
    val INFO_MANIFEST = "strata-manifest-v1".toByteArray()
}
