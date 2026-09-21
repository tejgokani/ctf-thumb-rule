package com.tejgokani.strata.drive

import com.tejgokani.strata.core.StrataResult

/**
 * Everything the engine needs from "a Google Drive account" — implemented for real by
 * [DriveBackend] (REST v3 over OkHttp) and for tests by FakeDriveBackend (test source set),
 * which simulates quota, latency, 429 storms, mid-upload failure, trashing, and node death
 * without any network or Android dependency.
 *
 * This is the seam that makes the whole engine JVM-testable (plan §3).
 */
interface StorageBackend {

    suspend fun getQuota(accountId: String): StrataResult<AccountQuota>

    /**
     * Uploads one chunk via a Drive resumable session.
     *
     * [onSessionOpened] is invoked with the session URI the moment Drive returns it — BEFORE any
     * content byte is sent — so the caller can persist it immediately (plan R9 / §6.9: the
     * session URI must be durable before the first byte leaves the device, so a crash mid-upload
     * is always resumable rather than silently orphaning a session).
     *
     * If [resumeSessionUri] is supplied, the backend probes it with `Content-Range: "bytes {wildcard}/SIZE"`
     * and resumes from the reported offset instead of starting a new session.
     */
    suspend fun uploadChunk(
        accountId: String,
        fileName: String,
        folderPathHint: String,
        content: ByteArray,
        properties: Map<String, String>,
        resumeSessionUri: String?,
        onSessionOpened: suspend (String) -> Unit,
    ): StrataResult<UploadOutcome>

    suspend fun getFileStatus(accountId: String, driveFileId: String): StrataResult<DriveFileStatus>

    suspend fun downloadFile(accountId: String, driveFileId: String): StrataResult<ByteArray>

    suspend fun trashFile(accountId: String, driveFileId: String): StrataResult<Unit>

    /** Restores a trashed file (R12: a trashed chunk within the 30-day window is a free repair). */
    suspend fun untrashFile(accountId: String, driveFileId: String): StrataResult<Unit>

    /** Permanent delete — reclaims quota. Never routes through emptyTrash (R13). */
    suspend fun permanentlyDeleteFile(accountId: String, driveFileId: String): StrataResult<Unit>

    /**
     * Full enumeration of every file this app created in [accountId] (drive.file scope — R2).
     * Callers MUST treat a paginated listing as admissible evidence only once every page has
     * been read successfully (plan §6.5 admissibility rule) — a partial listing must never be
     * used to justify a deletion.
     */
    suspend fun listAppFiles(accountId: String, pageToken: String?): StrataResult<DriveListPage>

    /**
     * Writes (or overwrites) a file in this account's private `appDataFolder`, returning the
     * server-assigned [AppDataEntry] — in particular its `createdAtMs`, which Lease (engine/)
     * uses as an external ordering oracle immune to device clock skew (plan §6.6).
     */
    suspend fun writeAppDataFile(accountId: String, name: String, content: ByteArray): StrataResult<AppDataEntry>
    suspend fun readAppDataFile(accountId: String, name: String): StrataResult<ByteArray?>
    suspend fun listAppDataFiles(accountId: String): StrataResult<List<AppDataEntry>>
    suspend fun deleteAppDataFile(accountId: String, name: String): StrataResult<Unit>
}

data class AppDataEntry(
    val name: String,
    val driveFileId: String,
    val createdAtMs: Long,
)

data class AccountQuota(
    val limitBytes: Long,
    val usageBytes: Long,
    val usageInDriveBytes: Long,
    val usageInDriveTrashBytes: Long,
)

data class UploadOutcome(
    val driveFileId: String,
    val cipherMd5Hex: String,
    val cipherSizeBytes: Long,
)

data class DriveFileStatus(
    val driveFileId: String,
    val sizeBytes: Long,
    val md5Hex: String?,
    val trashed: Boolean,
    val properties: Map<String, String>,
    /** Server-assigned creation time — used by ScrubEngine's orphan-age gate. */
    val createdAtMs: Long = 0L,
)

data class DriveListPage(
    val files: List<DriveFileStatus>,
    val nextPageToken: String?,
)
