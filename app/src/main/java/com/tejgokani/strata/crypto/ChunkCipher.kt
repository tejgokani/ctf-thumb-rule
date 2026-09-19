package com.tejgokani.strata.crypto

import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM with a fresh random 96-bit nonce on every encryption call — including retries.
 *
 * Plan §6.2 nonce discipline: a nonce must NEVER be derived from content or index. Re-encrypting
 * changed plaintext under the same (key, nonce) pair is a silent, total break of GCM
 * confidentiality and authenticity. SecureRandom nonces make (key, nonce) reuse astronomically
 * unlikely, and every ciphertext records its own nonce so decryption never has to guess.
 *
 * Wire layout: nonce(12) || ciphertext || tag(16)  — Cipher.doFinal() for GCM already appends
 * the tag to the ciphertext, so this is exactly `nonce + cipher.doFinal(plaintext)`.
 */
object ChunkCipher {
    const val KEY_LENGTH_BYTES = 32
    const val NONCE_LENGTH_BYTES = 12
    const val TAG_LENGTH_BITS = 128

    private val secureRandom = SecureRandom()

    class AuthenticationFailedException(cause: Throwable) :
        Exception("Ciphertext failed authentication (tampered, corrupted, or wrong key)", cause)

    fun generateKey(): ByteArray = ByteArray(KEY_LENGTH_BYTES).also { secureRandom.nextBytes(it) }

    private fun newNonce(): ByteArray = ByteArray(NONCE_LENGTH_BYTES).also { secureRandom.nextBytes(it) }

    fun encrypt(key: ByteArray, plaintext: ByteArray, aad: ByteArray? = null): ByteArray {
        require(key.size == KEY_LENGTH_BYTES) { "key must be $KEY_LENGTH_BYTES bytes" }
        val nonce = newNonce()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_LENGTH_BITS, nonce))
        aad?.let { cipher.updateAAD(it) }
        val ciphertextAndTag = cipher.doFinal(plaintext)
        return nonce + ciphertextAndTag
    }

    /** @throws AuthenticationFailedException if the tag does not verify (tamper/corruption/wrong key). */
    fun decrypt(key: ByteArray, blob: ByteArray, aad: ByteArray? = null): ByteArray {
        require(key.size == KEY_LENGTH_BYTES) { "key must be $KEY_LENGTH_BYTES bytes" }
        require(blob.size >= NONCE_LENGTH_BYTES + TAG_LENGTH_BITS / 8) { "blob too short to be valid" }
        val nonce = blob.copyOfRange(0, NONCE_LENGTH_BYTES)
        val ciphertextAndTag = blob.copyOfRange(NONCE_LENGTH_BYTES, blob.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_LENGTH_BITS, nonce))
        aad?.let { cipher.updateAAD(it) }
        return try {
            cipher.doFinal(ciphertextAndTag)
        } catch (e: GeneralSecurityException) {
            throw AuthenticationFailedException(e)
        }
    }
}
