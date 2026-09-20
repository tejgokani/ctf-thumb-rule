package com.tejgokani.strata.crypto

import java.security.MessageDigest

/**
 * Exports/imports the master key as a human-transcribable recovery code.
 *
 * Format: BASE32(masterKey) ++ BASE32(checksum) grouped into 4-character blocks separated by
 * dashes, e.g. "ABCD-EFGH-...". The checksum is the first 4 bytes of SHA-256(masterKey), which
 * lets [parse] detect a mistyped code before the user ever tries to use it against real data —
 * far better than silently deriving a wrong key and discovering it later.
 */
object RecoveryKey {
    private const val CHECKSUM_BYTES = 4

    data class ParseResult(val masterKey: ByteArray?, val checksumValid: Boolean)

    fun export(masterKey: ByteArray): String {
        val checksum = sha256(masterKey).copyOf(CHECKSUM_BYTES)
        val raw = Base32.encode(masterKey + checksum)
        return raw.chunked(4).joinToString("-")
    }

    fun parse(code: String): ParseResult {
        val raw = try {
            Base32.decode(code)
        } catch (e: IllegalArgumentException) {
            return ParseResult(null, checksumValid = false)
        }
        if (raw.size < ChunkCipher.KEY_LENGTH_BYTES + CHECKSUM_BYTES) {
            return ParseResult(null, checksumValid = false)
        }
        val key = raw.copyOf(ChunkCipher.KEY_LENGTH_BYTES)
        val suppliedChecksum = raw.copyOfRange(ChunkCipher.KEY_LENGTH_BYTES, raw.size)
        val expectedChecksum = sha256(key).copyOf(CHECKSUM_BYTES)
        val valid = suppliedChecksum.contentEquals(expectedChecksum)
        return ParseResult(if (valid) key else null, valid)
    }

    private fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)
}
