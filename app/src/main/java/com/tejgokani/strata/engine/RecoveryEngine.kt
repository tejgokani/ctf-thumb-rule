package com.tejgokani.strata.engine

import com.tejgokani.strata.core.Ids
import com.tejgokani.strata.core.StrataResult
import com.tejgokani.strata.crypto.KeyVault
import com.tejgokani.strata.crypto.PropsCodec
import com.tejgokani.strata.drive.StorageBackend

/**
 * The central invariant of this app (plan §4): "Google Drive holds the truth. Room is a
 * rebuildable cache." RecoveryEngine is what makes that literally true — it can reconstruct the
 * entire manifest (files, chunks, placements) from nothing but the encrypted `appProperties` on
 * every chunk in every connected account, cross-checked against whatever manifest replicas
 * ([ManifestReplicator]) happen to still be reachable.
 *
 * This is deliberately run as an in-app "SIMULATE DISASTER RECOVERY" action (plan §8/§10) and
 * exercised by the JVM disaster-drill test (plan §11) — an untested recovery path is a broken
 * recovery path.
 */
data class RecoveredManifest(
    val accounts: List<AccountRecord>,
    val files: List<FileRecord>,
    val chunks: List<ChunkRecord>,
    val placements: List<PlacementRecord>,
    /** Chunks whose file had no manifest record at all — recovered under a synthesized name. */
    val adoptedFileIds: Set<String>,
)

class RecoveryEngine(
    private val backend: StorageBackend,
    private val vault: KeyVault,
    private val manifestReplicator: ManifestReplicator,
    private val poolId: String,
) {
    suspend fun recover(accountIds: List<String>): StrataResult<RecoveredManifest> {
        // Path A (fast path): merged WAL + base snapshot, if any replica survives.
        val manifestSnapshot = manifestReplicator.recoverMerged(accountIds).getOrNull()

        val filesById = manifestSnapshot?.files?.associateBy { it.id }?.toMutableMap() ?: mutableMapOf()
        val chunksById = manifestSnapshot?.chunks?.associateBy { it.id }?.toMutableMap() ?: mutableMapOf()
        val placementsById = manifestSnapshot?.placements?.associateBy { it.id }?.toMutableMap() ?: mutableMapOf()
        val chunkIdByFileAndIndex = chunksById.values.associateBy { it.fileId to it.index }.toMutableMap()
        val fileIdByUuid = filesById.keys.toMutableSet()
        val adopted = mutableSetOf<String>()

        // Path B (ground truth, always run): enumerate every app-created file in every account and
        // decrypt its appProperties. This is the safety net that survives a total appDataFolder wipe.
        for (accountId in accountIds) {
            var pageToken: String? = null
            var pagesOk = true
            do {
                val page = backend.listAppFiles(accountId, pageToken).getOrNull()
                if (page == null) { pagesOk = false; break }
                for (status in page.files) {
                    val meta = PropsCodec.decodeFromDrive(vault, status.properties) ?: continue
                    if (meta.poolId != poolId) continue

                    if (meta.fileUuid !in fileIdByUuid) {
                        // No manifest record survived for this file at all — adopt it under a
                        // synthesized name (plan §12: recovered files degrade gracefully, they
                        // are never silently dropped).
                        filesById[meta.fileUuid] = FileRecord(
                            id = meta.fileUuid,
                            name = "recovered-${meta.fileUuid.take(8)}",
                            mimeType = "application/octet-stream",
                            sizeBytes = -1, // unknown until all chunks are accounted for; recomputed below
                            // Redundantly stored on every chunk (plan §6.2) specifically so this
                            // is available WITHOUT downloading and decrypting any content — the
                            // adopted file's integrity check works from the very first chunk seen.
                            plaintextSha256Hex = meta.fileWholeSha256Hex,
                            wrappedDekB64 = meta.wrappedDekB64,
                            chunkCount = meta.chunkCount,
                            mirrored = false,
                            status = FileStatus.DEGRADED,
                            createdMs = 0L,
                        )
                        fileIdByUuid.add(meta.fileUuid)
                        adopted.add(meta.fileUuid)
                    }

                    val chunkKey = meta.fileUuid to meta.chunkIndex
                    val chunkId = chunkIdByFileAndIndex[chunkKey]?.id ?: Ids.newId().also {
                        chunkIdByFileAndIndex[chunkKey] = ChunkRecord(it, meta.fileUuid, meta.chunkIndex, meta.plainSize, meta.plaintextSha256Hex)
                    }
                    chunksById[chunkId] = ChunkRecord(chunkId, meta.fileUuid, meta.chunkIndex, meta.plainSize, meta.plaintextSha256Hex)

                    val existingForThisDriveFile = placementsById.values.find {
                        it.accountId == accountId && it.driveFileId == status.driveFileId
                    }
                    val placementId = existingForThisDriveFile?.id ?: Ids.newId()
                    placementsById[placementId] = PlacementRecord(
                        id = placementId,
                        chunkId = chunkId,
                        accountId = accountId,
                        driveFileId = status.driveFileId,
                        nonceB64 = meta.nonceB64,
                        cipherMd5Hex = status.md5Hex ?: meta.ciphertextMd5Hex,
                        sessionUri = null,
                        isMirror = existingForThisDriveFile?.isMirror ?: false,
                        state = if (status.trashed) PlacementState.TRASHED_REPAIRABLE else PlacementState.VERIFIED,
                        manifestEpoch = meta.manifestEpoch,
                        lastVerifiedAtMs = 0L,
                    )
                }
                pageToken = page.nextPageToken
            } while (pageToken != null && pagesOk)
        }

        // Recompute adopted files' sizeBytes/sha256 where possible now that all chunks are known.
        for (fileId in adopted) {
            val file = filesById.getValue(fileId)
            val chunksForFile = chunksById.values.filter { it.fileId == fileId }.sortedBy { it.index }
            if (chunksForFile.size == file.chunkCount) {
                filesById[fileId] = file.copy(sizeBytes = chunksForFile.sumOf { it.plainSizeBytes })
            }
        }

        return StrataResult.Ok(
            RecoveredManifest(
                accounts = manifestSnapshot?.accounts ?: emptyList(),
                files = filesById.values.toList(),
                chunks = chunksById.values.toList(),
                placements = placementsById.values.toList(),
                adoptedFileIds = adopted,
            )
        )
    }
}
