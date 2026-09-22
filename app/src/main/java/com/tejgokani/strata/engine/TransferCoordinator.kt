package com.tejgokani.strata.engine

import com.tejgokani.strata.core.CanonicalValue
import com.tejgokani.strata.core.Ids
import com.tejgokani.strata.core.StrataClock
import com.tejgokani.strata.core.StrataError
import com.tejgokani.strata.core.StrataResult
import com.tejgokani.strata.crypto.ChunkMetadata
import com.tejgokani.strata.crypto.KeyVault
import com.tejgokani.strata.crypto.PropsCodec
import com.tejgokani.strata.drive.Backoff
import com.tejgokani.strata.drive.CircuitBreaker
import com.tejgokani.strata.drive.TokenBucket
import com.tejgokani.strata.drive.StorageBackend
import com.tejgokani.strata.ledger.Ledger
import com.tejgokani.strata.ledger.LedgerEventType
import java.security.MessageDigest
import java.util.Base64

/**
 * Orchestrates the write path end-to-end (plan §6.1/§6.9): plan the stripe, reserve capacity,
 * encrypt and upload each chunk with verify-after-write, self-describing appProperties, and a
 * per-device WAL flush on completion. STRATA never deletes the caller's local source (plan §1) —
 * this class only ever reads from [com.tejgokani.strata.engine.ByteSource].
 *
 * Only [PlacementState.VERIFIED] counts as durable. Verify-after-write is deliberately checked
 * against the server-reported checksum rather than trusted from the upload response, because the
 * upload path (truncated streams, retry bugs) is where real-world corruption actually enters.
 */
class TransferCoordinator(
    private val backend: StorageBackend,
    private val vault: KeyVault,
    private val ledger: Ledger,
    private val accounts: AccountRepository,
    private val files: FileRepository,
    private val chunks: ChunkRepository,
    private val placements: PlacementRepository,
    private val reservations: ReservationRepository,
    private val manifestReplicator: ManifestReplicator,
    private val seqAllocator: ManifestSeqAllocator,
    private val clock: StrataClock,
    private val deviceId: String,
    private val poolId: String,
    private val chunkSizeBytes: Long = Chunker.DEFAULT_CHUNK_SIZE_BYTES,
    private val maxAttemptsPerChunk: Int = 6,
    /** Exposed for tests — production leaves this at Backoff's real-world default (1000ms). */
    private val retryBaseDelayMs: Long = 1000,
) {
    private val breakers = mutableMapOf<String, CircuitBreaker>()
    private val buckets = mutableMapOf<String, TokenBucket>()

    private fun breakerFor(accountId: String) = breakers.getOrPut(accountId) { CircuitBreaker(clock) }
    private fun bucketFor(accountId: String) = buckets.getOrPut(accountId) { TokenBucket(10.0, 5.0, clock) }

    data class UploadRequest(val name: String, val mimeType: String, val source: ByteSource, val mirror: Boolean = false)

    suspend fun uploadFile(request: UploadRequest): StrataResult<FileRecord> {
        val fileId = Ids.newId()
        val walOps = mutableListOf<ManifestOp>()
        val reservationIds = mutableListOf<String>()

        val wholeFileHash = Chunker.sha256HexOfStream(request.source)
        val chunkCount = Chunker.chunkCountFor(request.source.sizeBytes(), chunkSizeBytes)

        val dek = vault.generateFileDek()
        val wrappedDekB64 = Base64.getEncoder().encodeToString(vault.wrapDek(dek))

        var fileRecord = FileRecord(
            id = fileId, name = request.name, mimeType = request.mimeType,
            sizeBytes = request.source.sizeBytes(), plaintextSha256Hex = wholeFileHash,
            wrappedDekB64 = wrappedDekB64, chunkCount = chunkCount, mirrored = request.mirror,
            status = FileStatus.UPLOADING, createdMs = clock.wallClockMs(),
        )
        files.upsert(fileRecord)
        walOps += ManifestReplicator.fileOp(deviceId, seqAllocator.next(), fileRecord)

        val plan = Allocator.planStripe(chunkCount, chunkSizeBytes, buildAccountViews()).let {
            when (it) {
                is StrataResult.Ok -> it.value
                is StrataResult.Err -> {
                    fileRecord = fileRecord.copy(status = FileStatus.INCOMPLETE_NO_SPACE)
                    files.upsert(fileRecord)
                    return StrataResult.Err(it.error)
                }
            }
        }

        // Reserve capacity per assigned account up front (plan §6.3 admission control).
        val perAccountChunkCounts = plan.chunkIndexToAccountId.values.groupingBy { it }.eachCount()
        val reservationExpiry = clock.wallClockMs() + 30 * 60_000
        for ((accountId, count) in perAccountChunkCounts) {
            val bytes = count * chunkSizeBytes * (if (request.mirror) 2 else 1)
            val r = reservations.reserve(accountId, bytes, reservationExpiry)
            reservationIds += r.id
        }

        var incomplete = false
        for (plain in Chunker.split(request.source, chunkSizeBytes)) {
            val primaryAccountId = plan.chunkIndexToAccountId[plain.index]
            if (primaryAccountId == null) { incomplete = true; continue }

            val chunkRecord = ChunkRecord(Ids.newId(), fileId, plain.index, plain.bytes.size.toLong(), plain.sha256Hex)
            chunks.upsert(chunkRecord)
            walOps += ManifestReplicator.chunkOp(deviceId, seqAllocator.next(), chunkRecord)

            val primaryOutcome = storeOneCopy(fileId, chunkRecord, plain.bytes, dek, wrappedDekB64, chunkCount, wholeFileHash, primaryAccountId, isMirror = false)
            if (primaryOutcome is StrataResult.Ok) walOps += ManifestReplicator.placementOp(deviceId, seqAllocator.next(), primaryOutcome.value)
            if (primaryOutcome is StrataResult.Err) { incomplete = true; continue }

            if (request.mirror) {
                val mirrorTarget = Allocator.chooseMirror(buildAccountViews(), chunkSizeBytes, setOf(primaryAccountId))
                if (mirrorTarget is StrataResult.Ok) {
                    val mirrorOutcome = storeOneCopy(fileId, chunkRecord, plain.bytes, dek, wrappedDekB64, chunkCount, wholeFileHash, mirrorTarget.value, isMirror = true)
                    if (mirrorOutcome is StrataResult.Ok) {
                        walOps += ManifestReplicator.placementOp(deviceId, seqAllocator.next(), mirrorOutcome.value)
                        ledger.append(LedgerEventType.CHUNK_MIRRORED, CanonicalValue.map(
                            "fileId" to CanonicalValue.of(fileId), "chunkIndex" to CanonicalValue.of(plain.index),
                            "accountId" to CanonicalValue.of(mirrorTarget.value),
                        ))
                    } else {
                        incomplete = true // mirror failed — primary still stands, but file isn't fully mirrored
                    }
                } else {
                    incomplete = true
                }
            }
        }

        fileRecord = fileRecord.copy(status = if (incomplete) FileStatus.INCOMPLETE_NO_SPACE else FileStatus.COMMITTED)
        files.upsert(fileRecord)
        walOps += ManifestReplicator.fileOp(deviceId, seqAllocator.next(), fileRecord)

        for (id in reservationIds) reservations.release(id)

        if (!incomplete) {
            ledger.append(LedgerEventType.FILE_ADDED, CanonicalValue.map(
                "fileId" to CanonicalValue.of(fileId), "name" to CanonicalValue.of(request.name),
                "sizeBytes" to CanonicalValue.of(fileRecord.sizeBytes), "chunkCount" to CanonicalValue.of(chunkCount),
            ))
        }

        val allAccountIds = accounts.all().map { it.id }
        if (allAccountIds.isNotEmpty()) {
            manifestReplicator.flushWal(allAccountIds, walOps, seqStart = walOps.firstOrNull()?.seq ?: 0L)
            ledger.append(LedgerEventType.MANIFEST_REPLICATED, CanonicalValue.map("fileId" to CanonicalValue.of(fileId)))
        }

        return if (incomplete) StrataResult.Err(StrataError.PoolFull(0)) else StrataResult.Ok(fileRecord)
    }

    /** Uploads one (possibly mirror) copy of a chunk to [accountId] with retry/backoff, then verifies it server-side. */
    private suspend fun storeOneCopy(
        fileId: String, chunk: ChunkRecord, plaintext: ByteArray, dek: ByteArray, wrappedDekB64: String,
        totalChunkCount: Int, wholeFileSha256Hex: String, accountId: String, isMirror: Boolean,
    ): StrataResult<PlacementRecord> {
        val placementId = Ids.newId()
        var placement = PlacementRecord(
            placementId, chunk.id, accountId, driveFileId = null, nonceB64 = "", cipherMd5Hex = null,
            sessionUri = null, isMirror = isMirror, state = PlacementState.PLANNED, manifestEpoch = 0L,
            lastVerifiedAtMs = 0L,
        )
        placements.upsert(placement)

        val blob = vault.encryptChunk(dek, plaintext)
        val nonceB64 = Base64.getEncoder().encodeToString(blob.copyOfRange(0, 12))
        placement = placement.copy(nonceB64 = nonceB64)
        placements.upsert(placement)

        val metadata = ChunkMetadata(
            poolId = poolId, fileUuid = fileId, chunkIndex = chunk.index, chunkCount = totalChunkCount,
            plainSize = plaintext.size.toLong(), plaintextSha256Hex = chunk.plaintextSha256Hex,
            ciphertextMd5Hex = md5Hex(blob), wrappedDekB64 = wrappedDekB64, nonceB64 = nonceB64, manifestEpoch = 0L,
            fileWholeSha256Hex = wholeFileSha256Hex,
        )
        val properties = PropsCodec.encodeForDrive(vault, metadata)

        var lastError: StrataError? = null
        var sessionUri: String? = null
        for (attempt in 0 until maxAttemptsPerChunk) {
            val breaker = breakerFor(accountId)
            val bucket = bucketFor(accountId)
            if (!bucket.tryAcquire()) {
                kotlinx.coroutines.delay(bucket.msUntilAvailable())
            }

            val result = backend.uploadChunk(
                accountId = accountId,
                fileName = "${chunk.fileId}.${chunk.index}.strata",
                folderPathHint = poolId.take(2),
                content = blob,
                properties = properties,
                resumeSessionUri = sessionUri,
                onSessionOpened = { uri ->
                    sessionUri = uri
                    placement = placement.copy(sessionUri = uri, state = PlacementState.SESSION_OPEN)
                    placements.upsert(placement) // persist BEFORE the first byte leaves — R9/plan §6.9
                },
            )

            when (result) {
                is StrataResult.Ok -> {
                    breaker.recordSuccess()
                    // The essential check: compare what WE computed from the ciphertext before
                    // ever sending it (metadata.ciphertextMd5Hex) against what the server reports
                    // it actually stored. Comparing two server-side reads against each other (the
                    // upload response and a follow-up getFileStatus) would be self-referential —
                    // both read the same stored object, so they can never disagree even if the
                    // bytes that arrived were corrupted in transit or by a storage bug.
                    val statusResult = backend.getFileStatus(accountId, result.value.driveFileId)
                    val status = statusResult.getOrNull()
                    val serverMd5 = status?.md5Hex ?: result.value.cipherMd5Hex
                    if (!serverMd5.equals(metadata.ciphertextMd5Hex, ignoreCase = true)) {
                        lastError = StrataError.ChecksumMismatch(metadata.ciphertextMd5Hex, serverMd5)
                        sessionUri = null
                        continue // discard and re-attempt a fresh session (plan §6.1)
                    }
                    placement = placement.copy(
                        driveFileId = result.value.driveFileId, cipherMd5Hex = metadata.ciphertextMd5Hex,
                        state = PlacementState.VERIFIED, lastVerifiedAtMs = clock.wallClockMs(),
                    )
                    placements.upsert(placement)
                    ledger.append(LedgerEventType.CHUNK_STORED, CanonicalValue.map(
                        "fileId" to CanonicalValue.of(fileId), "chunkIndex" to CanonicalValue.of(chunk.index),
                        "accountId" to CanonicalValue.of(accountId), "isMirror" to CanonicalValue.of(isMirror),
                    ))
                    ledger.append(LedgerEventType.CHUNK_VERIFIED, CanonicalValue.map(
                        "chunkId" to CanonicalValue.of(chunk.id), "accountId" to CanonicalValue.of(accountId),
                    ))
                    return StrataResult.Ok(placement)
                }
                is StrataResult.Err -> {
                    lastError = result.error
                    if (!result.error.retryable) break
                    breaker.recordFailure()
                    kotlinx.coroutines.delay(Backoff.delayMs(attempt, baseMs = retryBaseDelayMs))
                }
            }
        }
        placement = placement.copy(state = PlacementState.LOST)
        placements.upsert(placement)
        return StrataResult.Err(lastError ?: StrataError.Fatal("upload failed with no recorded error"))
    }

    private suspend fun buildAccountViews(): List<Allocator.AccountView> {
        val now = clock.wallClockMs()
        return accounts.all().map { acct ->
            val reserved = reservations.activeReservedBytes(acct.id, now)
            val effectiveFree = (acct.quotaLimitBytes - acct.quotaUsageBytes - reserved - acct.headroomBytes).coerceAtLeast(0)
            Allocator.AccountView(
                accountId = acct.id, effectiveFreeBytes = effectiveFree, usageRatio = acct.usageRatio,
                eligible = acct.isFullyUsable,
            )
        }
    }

    private fun md5Hex(bytes: ByteArray): String =
        com.tejgokani.strata.core.Hex.encode(MessageDigest.getInstance("MD5").digest(bytes))

    /**
     * Reads a file back: downloads each chunk's best available placement (preferring a
     * non-mirror VERIFIED copy, falling back to a mirror), decrypts, and reassembles.
     *
     * Note on R18 (unauthenticated-plaintext streaming): [KeyVault.decryptChunk] uses
     * `Cipher.doFinal()`, not `CipherInputStream` — the GCM tag is verified atomically as part
     * of producing the plaintext byte array, so no unauthenticated byte is ever exposed, let
     * alone written to [out]. A tamper/corruption throws before any of this chunk's bytes are
     * touched.
     */
    suspend fun downloadFile(fileId: String, out: java.io.OutputStream): StrataResult<Unit> {
        val fileRecord = files.get(fileId) ?: return StrataResult.Err(StrataError.NotFound("file $fileId"))
        val dek = vault.unwrapDek(Base64.getDecoder().decode(fileRecord.wrappedDekB64))
        val orderedChunks = chunks.forFile(fileId).sortedBy { it.index }

        if (orderedChunks.size != fileRecord.chunkCount) {
            return StrataResult.Err(StrataError.InvalidState("expected ${fileRecord.chunkCount} chunks, index has ${orderedChunks.size}"))
        }

        val plainChunks = ArrayList<ByteArray>(orderedChunks.size)
        for (chunkRecord in orderedChunks) {
            val candidates = placements.forChunk(chunkRecord.id)
                .filter { it.state == PlacementState.VERIFIED && it.driveFileId != null }
                .sortedBy { it.isMirror } // primary (isMirror=false) first, mirror as fallback

            var recovered: ByteArray? = null
            var lastError: StrataError? = null
            for (candidate in candidates) {
                val downloadResult = backend.downloadFile(candidate.accountId, candidate.driveFileId!!)
                val blob = downloadResult.getOrNull()
                if (blob == null) { lastError = (downloadResult as? StrataResult.Err)?.error; continue }
                val plaintext = try {
                    vault.decryptChunk(dek, blob)
                } catch (e: com.tejgokani.strata.crypto.ChunkCipher.AuthenticationFailedException) {
                    lastError = StrataError.ChecksumMismatch("tag", "invalid")
                    continue
                }
                if (Chunker.sha256Hex(plaintext) != chunkRecord.plaintextSha256Hex) {
                    lastError = StrataError.ChecksumMismatch(chunkRecord.plaintextSha256Hex, Chunker.sha256Hex(plaintext))
                    continue
                }
                recovered = plaintext
                break
            }

            if (recovered == null) {
                ledger.append(LedgerEventType.CHUNK_LOST, CanonicalValue.map(
                    "fileId" to CanonicalValue.of(fileId), "chunkIndex" to CanonicalValue.of(chunkRecord.index),
                ))
                files.upsert(fileRecord.copy(status = FileStatus.DAMAGED))
                return StrataResult.Err(lastError ?: StrataError.NotFound("chunk ${chunkRecord.index} unavailable on every placement"))
            }
            plainChunks += recovered
        }

        try {
            Reassembler.reassemble(plainChunks, fileRecord.plaintextSha256Hex, out)
        } catch (e: Reassembler.IntegrityException) {
            files.upsert(fileRecord.copy(status = FileStatus.DAMAGED))
            return StrataResult.Err(StrataError.ChecksumMismatch(fileRecord.plaintextSha256Hex, "whole-file mismatch"))
        }

        ledger.append(LedgerEventType.FILE_READ, CanonicalValue.map("fileId" to CanonicalValue.of(fileId)))
        return StrataResult.Ok(Unit)
    }
}
