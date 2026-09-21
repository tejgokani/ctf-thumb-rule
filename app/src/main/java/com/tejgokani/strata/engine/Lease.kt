package com.tejgokani.strata.engine

import com.tejgokani.strata.core.StrataClock
import com.tejgokani.strata.core.StrataError
import com.tejgokani.strata.core.StrataResult
import com.tejgokani.strata.drive.StorageBackend

/**
 * Single-writer mutual exclusion using nothing but Drive create/list/delete on one designated
 * account's `appDataFolder` (plan §6.6). v1 is deliberately single-writer: a second device that
 * finds an unexpired lease held by someone else opens READ-ONLY and says so — silently merging
 * two divergent manifests is exactly how this class of app loses data, so real multi-writer
 * merge is left for a future CRDT-based design (plan §14) rather than faked here.
 *
 * Correctness rests entirely on Drive's SERVER-ASSIGNED [com.tejgokani.strata.drive.AppDataEntry.createdAtMs],
 * which is totally ordered and immune to device clock skew — never on comparing our own clock to
 * another device's.
 */
class Lease(
    private val backend: StorageBackend,
    private val designatedAccountId: String,
    private val deviceId: String,
    private val clock: StrataClock,
    private val leaseTtlMs: Long = 10 * 60_000,
) {
    sealed class LeaseResult {
        data class Holder(val leaseCreatedAtMs: Long) : LeaseResult()
        data class ReadOnly(val holderDeviceId: String, val holderCreatedAtMs: Long) : LeaseResult()
    }

    private fun leaseName(forDeviceId: String) = "pool/lease-$forDeviceId"

    /** Call periodically while holding the lease (renews) and once at startup (acquires or detects contention). */
    suspend fun acquireOrRenew(): StrataResult<LeaseResult> {
        val myEntry = backend.writeAppDataFile(designatedAccountId, leaseName(deviceId), deviceId.toByteArray())
            .getOrNull() ?: return StrataResult.Err(StrataError.NetworkError(null))

        val listing = backend.listAppDataFiles(designatedAccountId).getOrNull()
            ?: return StrataResult.Err(StrataError.NetworkError(null))

        val now = clock.wallClockMs()
        val leaseEntries = listing.filter { it.name.startsWith("pool/lease-") }
        val liveLeases = leaseEntries.filter { now - it.createdAtMs < leaseTtlMs || it.driveFileId == myEntry.driveFileId }
        if (liveLeases.isEmpty()) return StrataResult.Err(StrataError.Fatal("no lease entries visible right after writing one"))

        // Oldest server-assigned createdAtMs wins; tie-break on the (also server-assigned) file id.
        val winner = liveLeases.minWithOrNull(compareBy({ it.createdAtMs }, { it.driveFileId }))!!
        val winnerDeviceId = winner.name.removePrefix("pool/lease-")

        // Best-effort cleanup of stale entries so the folder doesn't grow forever.
        for (entry in leaseEntries) {
            val expired = now - entry.createdAtMs >= leaseTtlMs
            if (expired && entry.driveFileId != winner.driveFileId) {
                backend.deleteAppDataFile(designatedAccountId, entry.name)
            }
        }

        return if (winnerDeviceId == deviceId) {
            StrataResult.Ok(LeaseResult.Holder(myEntry.createdAtMs))
        } else {
            backend.deleteAppDataFile(designatedAccountId, leaseName(deviceId))
            StrataResult.Ok(LeaseResult.ReadOnly(winnerDeviceId, winner.createdAtMs))
        }
    }

    suspend fun release() {
        backend.deleteAppDataFile(designatedAccountId, leaseName(deviceId))
    }
}
