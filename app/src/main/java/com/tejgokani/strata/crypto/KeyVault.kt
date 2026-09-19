package com.tejgokani.strata.crypto

/**
 * Orchestrates the key hierarchy described in plan §6.2:
 *
 *   passphrase --PBKDF2--> masterKey
 *                            |-- HKDF --> dekWrapKey    (wraps per-file DEKs)
 *                            |-- HKDF --> appPropsKey    (seals appProperties metadata blobs)
 *                            `-- HKDF --> manifestKey     (seals manifest WAL/base snapshots)
 *
 * Domain separation via HKDF means a compromise of one derived key's *usage context* doesn't
 * directly expose the others, even though they all trace back to the same master secret.
 *
 * [masterKey] itself is only ever held in memory for the process lifetime; on Android it is
 * additionally cached (wrapped) in the Keystore for biometric quick-unlock as a convenience —
 * see auth/ — never persisted in plaintext anywhere.
 */
class KeyVault private constructor(private val masterKey: ByteArray) {

    val dekWrapKey: ByteArray by lazy { Kdf.hkdfSha256(masterKey, Kdf.INFO_DEK_WRAP) }
    val appPropsKey: ByteArray by lazy { Kdf.hkdfSha256(masterKey, Kdf.INFO_APP_PROPS) }
    val manifestKey: ByteArray by lazy { Kdf.hkdfSha256(masterKey, Kdf.INFO_MANIFEST) }

    fun generateFileDek(): ByteArray = ChunkCipher.generateKey()
    fun wrapDek(dek: ByteArray): ByteArray = ChunkCipher.encrypt(dekWrapKey, dek)
    fun unwrapDek(wrapped: ByteArray): ByteArray = ChunkCipher.decrypt(dekWrapKey, wrapped)

    fun encryptChunk(dek: ByteArray, plaintext: ByteArray): ByteArray = ChunkCipher.encrypt(dek, plaintext)
    fun decryptChunk(dek: ByteArray, blob: ByteArray): ByteArray = ChunkCipher.decrypt(dek, blob)

    fun sealAppProperties(plaintext: ByteArray): ByteArray = ChunkCipher.encrypt(appPropsKey, plaintext)
    fun openAppProperties(blob: ByteArray): ByteArray = ChunkCipher.decrypt(appPropsKey, blob)

    fun sealManifestBytes(plaintext: ByteArray): ByteArray = ChunkCipher.encrypt(manifestKey, plaintext)
    fun openManifestBytes(blob: ByteArray): ByteArray = ChunkCipher.decrypt(manifestKey, blob)

    fun exportRecoveryKey(): String = RecoveryKey.export(masterKey)

    /** Constant-time-ish comparison to detect "is this the same vault" without exposing the key. */
    fun fingerprint(): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(masterKey)
        return com.tejgokani.strata.core.Hex.encode(digest).take(16)
    }

    companion object {
        fun fromPassphrase(passphrase: CharArray, salt: ByteArray, iterations: Int = Kdf.DEFAULT_ITERATIONS): KeyVault =
            KeyVault(Kdf.deriveMasterKey(passphrase, salt, iterations))

        fun fromRecoveryKey(code: String): KeyVault? =
            RecoveryKey.parse(code).masterKey?.let { KeyVault(it) }

        /** Test/internal use only — production code should derive via [fromPassphrase] or [fromRecoveryKey]. */
        fun fromMasterKeyForTesting(masterKey: ByteArray): KeyVault = KeyVault(masterKey)
    }
}
