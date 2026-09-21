package com.tejgokani.strata.engine

import com.tejgokani.strata.core.CanonicalCodec
import com.tejgokani.strata.core.CanonicalValue
import com.tejgokani.strata.core.StrataError
import com.tejgokani.strata.core.StrataResult
import com.tejgokani.strata.core.asBool
import com.tejgokani.strata.core.asLong
import com.tejgokani.strata.core.asMap
import com.tejgokani.strata.core.asString
import com.tejgokani.strata.crypto.KeyVault
import com.tejgokani.strata.drive.StorageBackend

/**
 * Replicates a LOG, not a snapshot (plan §6.6). `appDataFolder` is not a safe harbour on its own
 * — a user can wipe it from Drive Settings — so it is one of three redundant recovery sources
 * alongside chunk `appProperties` (PropsCodec) and the local Room cache.
 *
 * WAL segments are immutable and uniquely named per (deviceId, seq), so merging replicas is a
 * set union and is idempotent: collect every segment found across every account, apply all ops
 * a base snapshot doesn't already cover, done. No torn writes, no partial-snapshot merging.
 */
enum class ManifestOpKind { ACCOUNT_UPSERT, FILE_UPSERT, CHUNK_UPSERT, PLACEMENT_UPSERT }

data class ManifestOp(
    val deviceId: String,
    val seq: Long, // per-device monotonic; also the last-writer-wins version for merge ordering
    val kind: ManifestOpKind,
    val payload: CanonicalValue,
)

data class ManifestSnapshot(
    val epoch: Long,
    /** Highest seq from each device already folded into this snapshot — recovery skips WAL ops at or below it. */
    val watermarksByDevice: Map<String, Long>,
    val accounts: List<AccountRecord>,
    val files: List<FileRecord>,
    val chunks: List<ChunkRecord>,
    val placements: List<PlacementRecord>,
)

private object ManifestCodec {
    fun encodeAccount(a: AccountRecord) = CanonicalValue.map(
        "id" to CanonicalValue.of(a.id), "email" to CanonicalValue.of(a.email),
        "quotaLimitBytes" to CanonicalValue.of(a.quotaLimitBytes),
        "quotaUsageBytes" to CanonicalValue.of(a.quotaUsageBytes),
        "quotaUsageInDriveTrashBytes" to CanonicalValue.of(a.quotaUsageInDriveTrashBytes),
        "reservedBytes" to CanonicalValue.of(a.reservedBytes),
        "authState" to CanonicalValue.of(a.authState.name),
        "circuitState" to CanonicalValue.of(a.circuitState.name),
        "circuitOpenUntilMs" to CanonicalValue.of(a.circuitOpenUntilMs),
        "dailyUploadedBytes" to CanonicalValue.of(a.dailyUploadedBytes),
        "dailyBudgetResetAtMs" to CanonicalValue.of(a.dailyBudgetResetAtMs),
        "lastFullEnumerationMs" to CanonicalValue.of(a.lastFullEnumerationMs),
        "lastQuotaRefreshMs" to CanonicalValue.of(a.lastQuotaRefreshMs),
    )

    fun decodeAccount(v: CanonicalValue): AccountRecord {
        val m = v.asMap()
        return AccountRecord(
            id = m.getValue("id").asString(), email = m.getValue("email").asString(),
            quotaLimitBytes = m.getValue("quotaLimitBytes").asLong(),
            quotaUsageBytes = m.getValue("quotaUsageBytes").asLong(),
            quotaUsageInDriveTrashBytes = m.getValue("quotaUsageInDriveTrashBytes").asLong(),
            reservedBytes = m.getValue("reservedBytes").asLong(),
            authState = AuthState.valueOf(m.getValue("authState").asString()),
            circuitState = CircuitState.valueOf(m.getValue("circuitState").asString()),
            circuitOpenUntilMs = m.getValue("circuitOpenUntilMs").asLong(),
            dailyUploadedBytes = m.getValue("dailyUploadedBytes").asLong(),
            dailyBudgetResetAtMs = m.getValue("dailyBudgetResetAtMs").asLong(),
            lastFullEnumerationMs = m.getValue("lastFullEnumerationMs").asLong(),
            lastQuotaRefreshMs = m.getValue("lastQuotaRefreshMs").asLong(),
        )
    }

    fun encodeFile(f: FileRecord) = CanonicalValue.map(
        "id" to CanonicalValue.of(f.id), "name" to CanonicalValue.of(f.name),
        "mimeType" to CanonicalValue.of(f.mimeType), "sizeBytes" to CanonicalValue.of(f.sizeBytes),
        "plaintextSha256Hex" to CanonicalValue.of(f.plaintextSha256Hex),
        "wrappedDekB64" to CanonicalValue.of(f.wrappedDekB64),
        "chunkCount" to CanonicalValue.of(f.chunkCount), "mirrored" to CanonicalValue.of(f.mirrored),
        "status" to CanonicalValue.of(f.status.name), "createdMs" to CanonicalValue.of(f.createdMs),
    )

    fun decodeFile(v: CanonicalValue): FileRecord {
        val m = v.asMap()
        return FileRecord(
            id = m.getValue("id").asString(), name = m.getValue("name").asString(),
            mimeType = m.getValue("mimeType").asString(), sizeBytes = m.getValue("sizeBytes").asLong(),
            plaintextSha256Hex = m.getValue("plaintextSha256Hex").asString(),
            wrappedDekB64 = m.getValue("wrappedDekB64").asString(),
            chunkCount = m.getValue("chunkCount").asLong().toInt(),
            mirrored = m.getValue("mirrored").asBool(),
            status = FileStatus.valueOf(m.getValue("status").asString()),
            createdMs = m.getValue("createdMs").asLong(),
        )
    }

    fun encodeChunk(c: ChunkRecord) = CanonicalValue.map(
        "id" to CanonicalValue.of(c.id), "fileId" to CanonicalValue.of(c.fileId),
        "index" to CanonicalValue.of(c.index), "plainSizeBytes" to CanonicalValue.of(c.plainSizeBytes),
        "plaintextSha256Hex" to CanonicalValue.of(c.plaintextSha256Hex),
    )

    fun decodeChunk(v: CanonicalValue): ChunkRecord {
        val m = v.asMap()
        return ChunkRecord(
            id = m.getValue("id").asString(), fileId = m.getValue("fileId").asString(),
            index = m.getValue("index").asLong().toInt(), plainSizeBytes = m.getValue("plainSizeBytes").asLong(),
            plaintextSha256Hex = m.getValue("plaintextSha256Hex").asString(),
        )
    }

    fun encodePlacement(p: PlacementRecord) = CanonicalValue.map(
        "id" to CanonicalValue.of(p.id), "chunkId" to CanonicalValue.of(p.chunkId),
        "accountId" to CanonicalValue.of(p.accountId),
        "driveFileId" to CanonicalValue.of(p.driveFileId ?: ""),
        "nonceB64" to CanonicalValue.of(p.nonceB64), "cipherMd5Hex" to CanonicalValue.of(p.cipherMd5Hex ?: ""),
        "sessionUri" to CanonicalValue.of(p.sessionUri ?: ""), "isMirror" to CanonicalValue.of(p.isMirror),
        "state" to CanonicalValue.of(p.state.name), "manifestEpoch" to CanonicalValue.of(p.manifestEpoch),
        "lastVerifiedAtMs" to CanonicalValue.of(p.lastVerifiedAtMs),
    )

    fun decodePlacement(v: CanonicalValue): PlacementRecord {
        val m = v.asMap()
        return PlacementRecord(
            id = m.getValue("id").asString(), chunkId = m.getValue("chunkId").asString(),
            accountId = m.getValue("accountId").asString(),
            driveFileId = m.getValue("driveFileId").asString().ifEmpty { null },
            nonceB64 = m.getValue("nonceB64").asString(),
            cipherMd5Hex = m.getValue("cipherMd5Hex").asString().ifEmpty { null },
            sessionUri = m.getValue("sessionUri").asString().ifEmpty { null },
            isMirror = m.getValue("isMirror").asBool(),
            state = PlacementState.valueOf(m.getValue("state").asString()),
            manifestEpoch = m.getValue("manifestEpoch").asLong(),
            lastVerifiedAtMs = m.getValue("lastVerifiedAtMs").asLong(),
        )
    }

    fun encodeOp(op: ManifestOp): CanonicalValue = CanonicalValue.map(
        "deviceId" to CanonicalValue.of(op.deviceId), "seq" to CanonicalValue.of(op.seq),
        "kind" to CanonicalValue.of(op.kind.name), "payload" to op.payload,
    )

    fun decodeOp(v: CanonicalValue): ManifestOp {
        val m = v.asMap()
        return ManifestOp(
            deviceId = m.getValue("deviceId").asString(), seq = m.getValue("seq").asLong(),
            kind = ManifestOpKind.valueOf(m.getValue("kind").asString()), payload = m.getValue("payload"),
        )
    }

    fun encodeWalSegment(vault: KeyVault, ops: List<ManifestOp>): ByteArray {
        val canonical = CanonicalCodec.encode(CanonicalValue.List_(ops.map { encodeOp(it) }))
        return vault.sealManifestBytes(canonical)
    }

    fun decodeWalSegment(vault: KeyVault, bytes: ByteArray): List<ManifestOp> {
        val canonical = CanonicalCodec.decode(vault.openManifestBytes(bytes))
        return (canonical as CanonicalValue.List_).v.map { decodeOp(it) }
    }

    fun encodeSnapshot(vault: KeyVault, s: ManifestSnapshot): ByteArray {
        val canonical = CanonicalValue.map(
            "epoch" to CanonicalValue.of(s.epoch),
            "watermarks" to CanonicalValue.Map_(s.watermarksByDevice.mapValues { CanonicalValue.of(it.value) }),
            "accounts" to CanonicalValue.List_(s.accounts.map { encodeAccount(it) }),
            "files" to CanonicalValue.List_(s.files.map { encodeFile(it) }),
            "chunks" to CanonicalValue.List_(s.chunks.map { encodeChunk(it) }),
            "placements" to CanonicalValue.List_(s.placements.map { encodePlacement(it) }),
        )
        return vault.sealManifestBytes(CanonicalCodec.encode(canonical))
    }

    fun decodeSnapshot(vault: KeyVault, bytes: ByteArray): ManifestSnapshot {
        val m = CanonicalCodec.decode(vault.openManifestBytes(bytes)).asMap()
        return ManifestSnapshot(
            epoch = m.getValue("epoch").asLong(),
            watermarksByDevice = m.getValue("watermarks").asMap().mapValues { it.value.asLong() },
            accounts = (m.getValue("accounts") as CanonicalValue.List_).v.map { decodeAccount(it) },
            files = (m.getValue("files") as CanonicalValue.List_).v.map { decodeFile(it) },
            chunks = (m.getValue("chunks") as CanonicalValue.List_).v.map { decodeChunk(it) },
            placements = (m.getValue("placements") as CanonicalValue.List_).v.map { decodePlacement(it) },
        )
    }
}

class ManifestReplicator(
    private val backend: StorageBackend,
    private val vault: KeyVault,
    private val deviceId: String,
) {
    private fun walName(seqStart: Long) = "pool/wal-$deviceId-$seqStart.bin"
    private fun baseName(epoch: Long) = "pool/base-$epoch.bin"

    /** Writes one WAL segment to every listed account; succeeds once a quorum (>N/2) accepts it. */
    suspend fun flushWal(accountIds: List<String>, ops: List<ManifestOp>, seqStart: Long): StrataResult<Unit> {
        if (ops.isEmpty()) return StrataResult.Ok(Unit)
        val bytes = ManifestCodec.encodeWalSegment(vault, ops)
        val name = walName(seqStart)
        var successes = 0
        for (accountId in accountIds) {
            if (backend.writeAppDataFile(accountId, name, bytes) is StrataResult.Ok) successes++
        }
        val quorum = (accountIds.size / 2) + 1
        return if (successes >= quorum) StrataResult.Ok(Unit)
        else StrataResult.Err(StrataError.Fatal("manifest WAL quorum not reached ($successes/${accountIds.size})"))
    }

    suspend fun writeBase(accountIds: List<String>, snapshot: ManifestSnapshot): StrataResult<Unit> {
        val bytes = ManifestCodec.encodeSnapshot(vault, snapshot)
        val name = baseName(snapshot.epoch)
        var successes = 0
        for (accountId in accountIds) {
            if (backend.writeAppDataFile(accountId, name, bytes) is StrataResult.Ok) successes++
        }
        val quorum = (accountIds.size / 2) + 1
        return if (successes >= quorum) StrataResult.Ok(Unit)
        else StrataResult.Err(StrataError.Fatal("manifest base quorum not reached ($successes/${accountIds.size})"))
    }

    /**
     * Reads every replica reachable across [accountIds] and returns the merged view: highest
     * base snapshot found (by epoch) plus the union of every WAL segment across every account,
     * applied in ascending per-op seq order (last-writer-wins per entity id).
     */
    suspend fun recoverMerged(accountIds: List<String>): StrataResult<ManifestSnapshot> {
        var bestBase: ManifestSnapshot? = null
        val allWalBytes = LinkedHashMap<String, ByteArray>() // keyed by filename -> dedup across accounts for free
        val allBaseCandidates = LinkedHashMap<String, ByteArray>()

        for (accountId in accountIds) {
            val listing = backend.listAppDataFiles(accountId).getOrNull() ?: continue
            for (entry in listing) {
                if (!entry.name.startsWith("pool/")) continue
                when {
                    entry.name.startsWith("pool/wal-") -> {
                        if (!allWalBytes.containsKey(entry.name)) {
                            backend.readAppDataFile(accountId, entry.name).getOrNull()?.let {
                                allWalBytes[entry.name] = it
                            }
                        }
                    }
                    entry.name.startsWith("pool/base-") -> {
                        if (!allBaseCandidates.containsKey(entry.name)) {
                            backend.readAppDataFile(accountId, entry.name).getOrNull()?.let {
                                allBaseCandidates[entry.name] = it
                            }
                        }
                    }
                }
            }
        }

        for ((_, bytes) in allBaseCandidates) {
            val snap = try { ManifestCodec.decodeSnapshot(vault, bytes) } catch (e: Exception) { null } ?: continue
            if (bestBase == null || snap.epoch > bestBase!!.epoch) bestBase = snap
        }

        val base = bestBase ?: ManifestSnapshot(0, emptyMap(), emptyList(), emptyList(), emptyList(), emptyList())

        val ops = allWalBytes.values.flatMap {
            try { ManifestCodec.decodeWalSegment(vault, it) } catch (e: Exception) { emptyList() }
        }.filter { op -> op.seq > (base.watermarksByDevice[op.deviceId] ?: -1L) }
            .sortedBy { it.seq } // per-device monotonic seq also serves as the merge version

        val accounts = LinkedHashMap(base.accounts.associateBy { it.id })
        val files = LinkedHashMap(base.files.associateBy { it.id })
        val chunks = LinkedHashMap(base.chunks.associateBy { it.id })
        val placements = LinkedHashMap(base.placements.associateBy { it.id })
        val newWatermarks = base.watermarksByDevice.toMutableMap()

        for (op in ops) {
            when (op.kind) {
                ManifestOpKind.ACCOUNT_UPSERT -> ManifestCodec.decodeAccount(op.payload).let { accounts[it.id] = it }
                ManifestOpKind.FILE_UPSERT -> ManifestCodec.decodeFile(op.payload).let { files[it.id] = it }
                ManifestOpKind.CHUNK_UPSERT -> ManifestCodec.decodeChunk(op.payload).let { chunks[it.id] = it }
                ManifestOpKind.PLACEMENT_UPSERT -> ManifestCodec.decodePlacement(op.payload).let { placements[it.id] = it }
            }
            newWatermarks[op.deviceId] = maxOf(newWatermarks[op.deviceId] ?: -1L, op.seq)
        }

        return StrataResult.Ok(
            ManifestSnapshot(base.epoch, newWatermarks, accounts.values.toList(), files.values.toList(), chunks.values.toList(), placements.values.toList())
        )
    }

    companion object {
        fun accountOp(deviceId: String, seq: Long, a: AccountRecord) = ManifestOp(deviceId, seq, ManifestOpKind.ACCOUNT_UPSERT, ManifestCodec.encodeAccount(a))
        fun fileOp(deviceId: String, seq: Long, f: FileRecord) = ManifestOp(deviceId, seq, ManifestOpKind.FILE_UPSERT, ManifestCodec.encodeFile(f))
        fun chunkOp(deviceId: String, seq: Long, c: ChunkRecord) = ManifestOp(deviceId, seq, ManifestOpKind.CHUNK_UPSERT, ManifestCodec.encodeChunk(c))
        fun placementOp(deviceId: String, seq: Long, p: PlacementRecord) = ManifestOp(deviceId, seq, ManifestOpKind.PLACEMENT_UPSERT, ManifestCodec.encodePlacement(p))
    }
}
