package com.tejgokani.strata.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.tejgokani.strata.engine.AccountRecord
import com.tejgokani.strata.engine.AuthState
import com.tejgokani.strata.engine.ChunkRecord
import com.tejgokani.strata.engine.CircuitState
import com.tejgokani.strata.engine.FileRecord
import com.tejgokani.strata.engine.FileStatus
import com.tejgokani.strata.engine.PlacementRecord
import com.tejgokani.strata.engine.PlacementState
import com.tejgokani.strata.engine.ReservationRecord
import com.tejgokani.strata.ledger.LedgerEventType

/**
 * Room entities mirroring the plain engine records (engine/Models.kt). Kept as a thin, explicit
 * mapping layer rather than annotating the engine classes directly, so the engine stays free of
 * Room/Android imports and testable on a plain JVM (plan §3/§9).
 */

class EnumConverters {
    @TypeConverter fun authStateToDb(v: AuthState): String = v.name
    @TypeConverter fun authStateFromDb(v: String): AuthState = AuthState.valueOf(v)
    @TypeConverter fun circuitStateToDb(v: CircuitState): String = v.name
    @TypeConverter fun circuitStateFromDb(v: String): CircuitState = CircuitState.valueOf(v)
    @TypeConverter fun fileStatusToDb(v: FileStatus): String = v.name
    @TypeConverter fun fileStatusFromDb(v: String): FileStatus = FileStatus.valueOf(v)
    @TypeConverter fun placementStateToDb(v: PlacementState): String = v.name
    @TypeConverter fun placementStateFromDb(v: String): PlacementState = PlacementState.valueOf(v)
    @TypeConverter fun ledgerEventTypeToDb(v: LedgerEventType): String = v.name
    @TypeConverter fun ledgerEventTypeFromDb(v: String): LedgerEventType = LedgerEventType.valueOf(v)
}

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String,
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
)

fun AccountEntity.toRecord() = AccountRecord(
    id, email, quotaLimitBytes, quotaUsageBytes, quotaUsageInDriveTrashBytes, reservedBytes,
    authState, circuitState, circuitOpenUntilMs, dailyUploadedBytes, dailyBudgetResetAtMs,
    lastFullEnumerationMs, lastQuotaRefreshMs,
)

fun AccountRecord.toEntity() = AccountEntity(
    id, email, quotaLimitBytes, quotaUsageBytes, quotaUsageInDriveTrashBytes, reservedBytes,
    authState, circuitState, circuitOpenUntilMs, dailyUploadedBytes, dailyBudgetResetAtMs,
    lastFullEnumerationMs, lastQuotaRefreshMs,
)

@Entity(tableName = "files")
data class FileEntity(
    @PrimaryKey val id: String,
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

fun FileEntity.toRecord() = FileRecord(id, name, mimeType, sizeBytes, plaintextSha256Hex, wrappedDekB64, chunkCount, mirrored, status, createdMs)
fun FileRecord.toEntity() = FileEntity(id, name, mimeType, sizeBytes, plaintextSha256Hex, wrappedDekB64, chunkCount, mirrored, status, createdMs)

@Entity(tableName = "chunks")
data class ChunkEntity(
    @PrimaryKey val id: String,
    val fileId: String,
    val idx: Int,
    val plainSizeBytes: Long,
    val plaintextSha256Hex: String,
)

fun ChunkEntity.toRecord() = ChunkRecord(id, fileId, idx, plainSizeBytes, plaintextSha256Hex)
fun ChunkRecord.toEntity() = ChunkEntity(id, fileId, index, plainSizeBytes, plaintextSha256Hex)

@Entity(tableName = "placements")
data class PlacementEntity(
    @PrimaryKey val id: String,
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

fun PlacementEntity.toRecord() = PlacementRecord(id, chunkId, accountId, driveFileId, nonceB64, cipherMd5Hex, sessionUri, isMirror, state, manifestEpoch, lastVerifiedAtMs)
fun PlacementRecord.toEntity() = PlacementEntity(id, chunkId, accountId, driveFileId, nonceB64, cipherMd5Hex, sessionUri, isMirror, state, manifestEpoch, lastVerifiedAtMs)

@Entity(tableName = "reservations")
data class ReservationEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val bytes: Long,
    val expiresAtMs: Long,
)

fun ReservationEntity.toRecord() = ReservationRecord(id, accountId, bytes, expiresAtMs)
fun ReservationRecord.toEntity() = ReservationEntity(id, accountId, bytes, expiresAtMs)

@Entity(tableName = "ledger_events")
data class LedgerEventEntity(
    @PrimaryKey(autoGenerate = false) val seq: Long,
    val deviceId: String,
    val lamport: Long,
    val wallClockMs: Long,
    val monotonicMs: Long,
    val type: LedgerEventType,
    val payload: ByteArray, // CanonicalCodec-encoded
    val prevHash: ByteArray,
    val hash: ByteArray,
)
