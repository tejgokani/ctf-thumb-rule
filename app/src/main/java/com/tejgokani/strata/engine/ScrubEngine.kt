package com.tejgokani.strata.engine

import com.tejgokani.strata.core.StrataClock
import com.tejgokani.strata.crypto.ChunkMetadata
import com.tejgokani.strata.crypto.KeyVault
import com.tejgokani.strata.crypto.PropsCodec
import com.tejgokani.strata.drive.DriveFileStatus
import com.tejgokani.strata.drive.StorageBackend

/**
 * Reconciles Room's index against the actual contents of every connected Drive account
 * (plan §6.5). This is where the two rules that keep this app from eating user data live:
 *
 * 1. ADMISSIBILITY RULE: only a fully-paginated, fully-successful enumeration of an account is
 *    admissible evidence for a deletion. A partial listing, a 403, a rate-limited page, or an
 *    account in NEEDS_CONSENT contributes ZERO evidence — never "probably missing".
 * 2. GC SAFETY RULE: a chunk is never deleted because it is merely absent from Room. It is only
 *    ever deleted once a tombstone with a replicated epoch says so (enforced by the caller that
 *    applies [ScrubAction.TrashOrphan] — this engine only ever proposes candidates).
 */
sealed class ScrubAction {
    /** A trashed chunk within Drive's 30-day undo window — restoring it is a free repair (R12). */
    data class Untrash(val accountId: String, val driveFileId: String, val placementId: String) : ScrubAction()

    /** A Drive file with valid, decodable STRATA metadata but no local placement — self-healing. */
    data class Adopt(val accountId: String, val driveFileId: String, val metadata: ChunkMetadata) : ScrubAction()

    data class MarkDamaged(val fileId: String, val chunkId: String, val reason: String) : ScrubAction()

    /** Repair a damaged/missing chunk from a surviving mirror placement, if one exists. */
    data class RepairFromMirror(val chunkId: String, val sourcePlacementId: String) : ScrubAction()

    /** Candidate for (soft) trash — undecodable, or decodable-but-stale-and-old. Never a hard delete. */
    data class TrashOrphanCandidate(val accountId: String, val driveFileId: String, val reason: String) : ScrubAction()
}

data class ScrubReport(
    val actions: List<ScrubAction>,
    /** Accounts whose enumeration did not complete this pass — contributed no evidence at all. */
    val accountsWithIncompleteEnumeration: Set<String>,
)

class ScrubEngine(
    private val backend: StorageBackend,
    private val vault: KeyVault,
    private val clock: StrataClock,
    private val poolId: String,
) {
    companion object {
        const val ORPHAN_GRACE_PERIOD_MS = 14L * 24 * 60 * 60 * 1000 // 14 days
    }

    suspend fun scrub(
        accountIds: List<String>,
        placements: List<PlacementRecord>,
        chunks: List<ChunkRecord>,
        currentManifestEpoch: Long,
    ): ScrubReport {
        val chunkById = chunks.associateBy { it.id }
        val actions = mutableListOf<ScrubAction>()
        val incomplete = mutableSetOf<String>()
        val driveFilesByAccount = mutableMapOf<String, List<DriveFileStatus>>()

        for (accountId in accountIds) {
            val collected = mutableListOf<DriveFileStatus>()
            var pageToken: String? = null
            var complete = true
            do {
                val page = backend.listAppFiles(accountId, pageToken).getOrNull()
                if (page == null) { complete = false; break }
                collected += page.files
                pageToken = page.nextPageToken
            } while (pageToken != null)

            if (complete) driveFilesByAccount[accountId] = collected else incomplete += accountId
        }

        val driveByKey = driveFilesByAccount
            .flatMap { (acct, files) -> files.map { (acct to it.driveFileId) to it } }
            .toMap()

        // --- MISSING / TRASHED / CORRUPT: only for placements whose account gave full evidence ---
        val placementsWithMirrorSibling = placements
            .filter { it.isMirror || placements.count { p -> p.chunkId == it.chunkId } > 1 }
            .groupBy { it.chunkId }

        for (p in placements) {
            if (p.accountId in incomplete) continue // admissibility rule: no evidence, no action
            if (p.state != PlacementState.VERIFIED) continue
            val driveFileId = p.driveFileId ?: continue
            val status = driveByKey[p.accountId to driveFileId]
            val chunkId = p.chunkId
            val fileId = chunkById[chunkId]?.fileId ?: continue

            when {
                status == null -> {
                    val mirrorSibling = placementsWithMirrorSibling[chunkId]
                        ?.firstOrNull { it.id != p.id && it.state == PlacementState.VERIFIED }
                    if (mirrorSibling != null) {
                        actions += ScrubAction.RepairFromMirror(chunkId, mirrorSibling.id)
                    } else {
                        actions += ScrubAction.MarkDamaged(fileId, chunkId, "chunk not found on account and no mirror survives")
                    }
                }
                status.trashed -> actions += ScrubAction.Untrash(p.accountId, driveFileId, p.id)
                status.md5Hex != null && p.cipherMd5Hex != null && !status.md5Hex.equals(p.cipherMd5Hex, ignoreCase = true) -> {
                    actions += ScrubAction.MarkDamaged(fileId, chunkId, "server checksum mismatch — treat as missing")
                }
            }
        }

        // --- ORPHANS: files Drive has that our placement table doesn't know about ---
        val knownDriveFileIds = placements.mapNotNull { it.driveFileId }.toSet()
        for ((accountId, statuses) in driveFilesByAccount) {
            for (status in statuses) {
                if (status.driveFileId in knownDriveFileIds) continue
                val meta = PropsCodec.decodeFromDrive(vault, status.properties)
                val ageMs = clock.wallClockMs() - status.createdAtMs
                when {
                    meta == null -> actions += ScrubAction.TrashOrphanCandidate(accountId, status.driveFileId, "undecodable appProperties")
                    meta.poolId != poolId -> { /* belongs to a different STRATA pool sharing this account — ignore entirely */ }
                    meta.manifestEpoch < currentManifestEpoch && ageMs > ORPHAN_GRACE_PERIOD_MS ->
                        actions += ScrubAction.TrashOrphanCandidate(accountId, status.driveFileId, "stale epoch, unreferenced >14d")
                    else -> actions += ScrubAction.Adopt(accountId, status.driveFileId, meta)
                }
            }
        }

        return ScrubReport(actions, incomplete)
    }
}
