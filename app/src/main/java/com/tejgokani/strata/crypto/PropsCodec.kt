package com.tejgokani.strata.crypto

import com.tejgokani.strata.core.CanonicalCodec
import com.tejgokani.strata.core.CanonicalValue
import com.tejgokani.strata.core.asLong
import com.tejgokani.strata.core.asMap
import com.tejgokani.strata.core.asString
import java.util.Base64

/**
 * Every chunk a chunk is self-describing (plan §6.2): the metadata needed to reconstruct the
 * manifest entry for a chunk is stored, ENCRYPTED, in that chunk's Google Drive `appProperties`.
 * The union of every chunk's properties across every connected account is therefore a complete,
 * distributed, always-consistent index — recovery does not depend on the local Room database or
 * even the appDataFolder manifest replicas surviving.
 *
 * Google sees only opaque base64 text; only the holder of the passphrase (or recovery key) can
 * decrypt it, via [KeyVault.appPropsKey].
 *
 * Drive limits properties to a small number of short key/value pairs, so the encrypted blob is
 * base64'd and split across several keys ("d0", "d1", ...) plus a "dn" count key.
 */
data class ChunkMetadata(
    val poolId: String,
    val fileUuid: String,
    val chunkIndex: Int,
    val chunkCount: Int,
    val plainSize: Long,
    val plaintextSha256Hex: String,
    val ciphertextMd5Hex: String,
    val wrappedDekB64: String,
    val nonceB64: String,
    val manifestEpoch: Long,
    /**
     * The WHOLE FILE's plaintext SHA-256 (not this chunk's). Redundantly stored on every chunk
     * so RecoveryEngine can populate an adopted FileRecord's integrity hash without downloading
     * and decrypting any content — the same "self-describing chunk" reasoning that already
     * justifies redundantly storing wrappedDek on every chunk.
     */
    val fileWholeSha256Hex: String,
)

object PropsCodec {
    // Conservative vs Drive's ~124-byte combined key+value limit per appProperties entry.
    private const val MAX_VALUE_CHARS = 100
    private const val KEY_PREFIX = "d"
    private const val COUNT_KEY = "dn"
    // Stay comfortably under Drive's 30-properties-per-file cap.
    private const val MAX_PARTS = 27

    private fun toCanonical(m: ChunkMetadata): CanonicalValue = CanonicalValue.map(
        "poolId" to CanonicalValue.of(m.poolId),
        "fileUuid" to CanonicalValue.of(m.fileUuid),
        "chunkIndex" to CanonicalValue.of(m.chunkIndex),
        "chunkCount" to CanonicalValue.of(m.chunkCount),
        "plainSize" to CanonicalValue.of(m.plainSize),
        "plaintextSha256" to CanonicalValue.of(m.plaintextSha256Hex),
        "ciphertextMd5" to CanonicalValue.of(m.ciphertextMd5Hex),
        "wrappedDek" to CanonicalValue.of(m.wrappedDekB64),
        "nonce" to CanonicalValue.of(m.nonceB64),
        "manifestEpoch" to CanonicalValue.of(m.manifestEpoch),
        "fileSha256" to CanonicalValue.of(m.fileWholeSha256Hex),
    )

    private fun fromCanonical(v: CanonicalValue): ChunkMetadata {
        val map = v.asMap()
        return ChunkMetadata(
            poolId = map.getValue("poolId").asString(),
            fileUuid = map.getValue("fileUuid").asString(),
            chunkIndex = map.getValue("chunkIndex").asLong().toInt(),
            chunkCount = map.getValue("chunkCount").asLong().toInt(),
            plainSize = map.getValue("plainSize").asLong(),
            plaintextSha256Hex = map.getValue("plaintextSha256").asString(),
            ciphertextMd5Hex = map.getValue("ciphertextMd5").asString(),
            wrappedDekB64 = map.getValue("wrappedDek").asString(),
            nonceB64 = map.getValue("nonce").asString(),
            manifestEpoch = map.getValue("manifestEpoch").asLong(),
            fileWholeSha256Hex = map.getValue("fileSha256").asString(),
        )
    }

    /** @throws IllegalArgumentException if the encoded blob would need more parts than Drive allows. */
    fun encodeForDrive(vault: KeyVault, metadata: ChunkMetadata): Map<String, String> {
        val canonical = CanonicalCodec.encode(toCanonical(metadata))
        val sealed = vault.sealAppProperties(canonical)
        val b64 = Base64.getEncoder().withoutPadding().encodeToString(sealed)
        val parts = b64.chunked(MAX_VALUE_CHARS)
        require(parts.size <= MAX_PARTS) {
            "chunk metadata too large for Drive appProperties (${parts.size} parts, max $MAX_PARTS)"
        }
        val out = LinkedHashMap<String, String>()
        out[COUNT_KEY] = parts.size.toString()
        parts.forEachIndexed { i, part -> out["$KEY_PREFIX$i"] = part }
        return out
    }

    /**
     * Reassembles and decrypts metadata from a Drive file's `appProperties`.
     * Returns null if the properties are incomplete, foreign, or fail to decrypt — callers
     * (ScrubEngine) treat that as "undecodable orphan", never as "definitely garbage": see the
     * admissibility rule in plan §6.5.
     */
    fun decodeFromDrive(vault: KeyVault, properties: Map<String, String>?): ChunkMetadata? {
        if (properties == null) return null
        val n = properties[COUNT_KEY]?.toIntOrNull() ?: return null
        if (n <= 0 || n > MAX_PARTS) return null
        val sb = StringBuilder()
        for (i in 0 until n) {
            sb.append(properties["$KEY_PREFIX$i"] ?: return null)
        }
        return try {
            val sealed = Base64.getDecoder().decode(sb.toString())
            val canonical = vault.openAppProperties(sealed)
            fromCanonical(CanonicalCodec.decode(canonical))
        } catch (e: Exception) {
            null
        }
    }
}
