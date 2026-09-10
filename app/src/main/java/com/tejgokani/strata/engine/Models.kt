package com.tejgokani.strata.engine

/**
 * Plain data model shared by the engine, Room persistence (data/db — entities mirror these) and
 * tests (in-memory fakes implement the same repository interfaces). Keeping these free of
 * Android/Room annotations is what makes the engine testable on a plain JVM.
 */

enum class AuthState { OK, NEEDS_CONSENT, ACCOUNT_MISSING, ACCESS_REVOKED }
enum class CircuitState { CLOSED, OPEN, HALF_OPEN }

data class AccountRecord(
    val id: String,
    val email: String,
    val quotaLimitBytes: Long,
    val quotaUsageBytes: Long,
    val quotaUsageInDriveTrashBytes: Long,
    val reservedBytes: Long,
    val authState: AuthState,
    val circuitState: CircuitState,
    val circuitOpenUntilMs: Long,
    val dailyUploadedBytes: Long,
    val dailyBudgetResetAtMs: Long,
    val lastFullEnumerationMs: Long,
    val lastQuotaRefreshMs: Long,
) {
    val headroomBytes: Long get() = maxOf(1_000_000_000L, (quotaLimitBytes * 0.08).toLong())
    val effectiveFreeBytes: Long get() =
        (quotaLimitBytes - quotaUsageBytes - reservedBytes - headroomBytes).coerceAtLeast(0)
    val usageRatio: Double get() =
        if (quotaLimitBytes <= 0) 1.0 else quotaUsageBytes.toDouble() / quotaLimitBytes.toDouble()
    val reclaimableBytes: Long get() = quotaUsageInDriveTrashBytes

    /** True only when this node is safe to place new chunks on or trust for GC evidence. */
    val isFullyUsable: Boolean get() =
        authState == AuthState.OK && circuitState != CircuitState.OPEN
}

enum class FileStatus { UPLOADING, INCOMPLETE_NO_SPACE, COMMITTED, DEGRADED, DAMAGED }

data class FileRecord(
    val id: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val plaintextSha256Hex: String,
    val wrappedDekB64: String,
    val chunkCount: Int,
    val mirrored: Boolean,
    val status: FileStatus,
    val createdMs: Long,
)

data class ChunkRecord(
    val id: String,
    val fileId: String,
    val index: Int,
    val plainSizeBytes: Long,
    val plaintextSha256Hex: String,
)

enum class PlacementState { PLANNED, SESSION_OPEN, UPLOADING, UPLOADED, VERIFIED, TRASHED_REPAIRABLE, LOST }

data class PlacementRecord(
    val id: String,
    val chunkId: String,
    val accountId: String,
    val driveFileId: String?,
    val nonceB64: String,
    val cipherMd5Hex: String?,
    val sessionUri: String?,
    val isMirror: Boolean,
    val state: PlacementState,
    val manifestEpoch: Long,
    val lastVerifiedAtMs: Long,
)

data class ReservationRecord(
    val id: String,
    val accountId: String,
    val bytes: Long,
    val expiresAtMs: Long,
)
