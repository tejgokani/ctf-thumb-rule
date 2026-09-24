package com.tejgokani.strata.fakes

import com.tejgokani.strata.core.Hex
import com.tejgokani.strata.core.StrataClock
import com.tejgokani.strata.core.StrataError
import com.tejgokani.strata.core.StrataResult
import com.tejgokani.strata.drive.AccountQuota
import com.tejgokani.strata.drive.AppDataEntry
import com.tejgokani.strata.drive.DriveFileStatus
import com.tejgokani.strata.drive.DriveListPage
import com.tejgokani.strata.drive.StorageBackend
import com.tejgokani.strata.drive.UploadOutcome
import java.security.MessageDigest

/**
 * In-memory simulation of "a Google Drive account" — this is what makes the whole engine
 * JVM-testable with zero Google accounts and no device (plan §3/§11). Simulates per-account
 * quota, latency, 429 storms, mid-upload failure, trashing/untrashing, and node death, all
 * under direct test control.
 */
class FakeDriveBackend(private val clock: StrataClock) : StorageBackend {

    data class FakeFile(
        var driveFileId: String,
        var content: ByteArray,
        var properties: Map<String, String>,
        var trashed: Boolean = false,
        var permanentlyDeleted: Boolean = false,
        val createdAtMs: Long,
    )

    private class AccountState {
        var quotaLimitBytes: Long = 15_000_000_000L
        var quotaUsageBytes: Long = 0L
        var quotaUsageInDriveTrashBytes: Long = 0L
        val files = LinkedHashMap<String, FakeFile>() // driveFileId -> file
        val appData = LinkedHashMap<String, FakeFile>() // name -> file
        val openSessions = LinkedHashMap<String, SessionState>() // sessionUri -> state
        var unreachable = false
        var forceRateLimitCount = 0
        var forceServerErrorCount = 0
        var nextIdCounter = 0
        var interruptNextUploadAtBytes: Int? = null
        var sessionsCreatedCount = 0
        var uploadAttemptCount = 0
        var corruptNextUpload = false
    }

    private class SessionState(val fileName: String, val properties: Map<String, String>, val totalSize: Int) {
        var committedBytes = 0
        var content = ByteArray(totalSize)
        var finalFileId: String? = null
    }

    private val accounts = LinkedHashMap<String, AccountState>()
    private var idCounter = 0

    private fun state(accountId: String) = accounts.getOrPut(accountId) { AccountState() }

    // --- Test control surface -------------------------------------------------------------

    fun setQuota(accountId: String, limitBytes: Long, usageBytes: Long = 0L, trashBytes: Long = 0L) {
        val s = state(accountId)
        s.quotaLimitBytes = limitBytes
        s.quotaUsageBytes = usageBytes
        s.quotaUsageInDriveTrashBytes = trashBytes
    }

    fun setUnreachable(accountId: String, unreachable: Boolean) { state(accountId).unreachable = unreachable }

    /** The next [count] upload PUTs to this account will fail with a retryable 429. */
    fun injectRateLimit(accountId: String, count: Int) { state(accountId).forceRateLimitCount = count }

    fun injectServerError(accountId: String, count: Int) { state(accountId).forceServerErrorCount = count }

    /**
     * The NEXT upload attempt on this account will "receive" only [atByteOffset] bytes before
     * the simulated connection drops (a retryable NetworkError), without discarding the
     * resumable session. A subsequent call with the same `resumeSessionUri` must send only the
     * remaining bytes to complete — exactly what R9's Content-Range resume semantics require.
     */
    fun interruptNextUploadAt(accountId: String, atByteOffset: Int) {
        state(accountId).interruptNextUploadAtBytes = atByteOffset
    }

    /** Distinct resumable sessions actually opened — stays 1 across an interrupt+resume pair iff the resume was genuine. */
    fun sessionsCreated(accountId: String): Int = state(accountId).sessionsCreatedCount

    /**
     * The NEXT completed upload on this account will silently persist different bytes than what
     * the client sent — simulating in-transit corruption or a storage bug on Drive's side — so
     * the verify-after-write step has something real to catch (plan §6.1: "only VERIFIED
     * counts").
     */
    fun corruptNextUpload(accountId: String) { state(accountId).corruptNextUpload = true }

    /** Total uploadChunk() invocations (interrupted + successful) — for asserting a specific retry count. */
    fun uploadAttempts(accountId: String): Int = state(accountId).uploadAttemptCount

    /** Simulates a user manually deleting a chunk file in the Drive web UI (moves it to trash). */
    fun userTrashesFile(accountId: String, driveFileId: String) {
        state(accountId).files[driveFileId]?.let { it.trashed = true }
    }

    /** Simulates total node loss (account removed, files gone) for repair-path tests. */
    fun destroyAccount(accountId: String) { accounts.remove(accountId) }

    fun fileCount(accountId: String): Int = state(accountId).files.values.count { !it.permanentlyDeleted }

    private fun nextId(): String = "fake-file-${idCounter++}"

    // --- StorageBackend ---------------------------------------------------------------------

    override suspend fun getQuota(accountId: String): StrataResult<AccountQuota> {
        val s = state(accountId)
        if (s.unreachable) return StrataResult.Err(StrataError.NetworkError(null))
        return StrataResult.Ok(AccountQuota(s.quotaLimitBytes, s.quotaUsageBytes, s.quotaUsageBytes, s.quotaUsageInDriveTrashBytes))
    }

    override suspend fun uploadChunk(
        accountId: String, fileName: String, folderPathHint: String, content: ByteArray,
        properties: Map<String, String>, resumeSessionUri: String?, onSessionOpened: suspend (String) -> Unit,
    ): StrataResult<UploadOutcome> {
        val s = state(accountId)
        if (s.unreachable) return StrataResult.Err(StrataError.NetworkError(null))

        if (s.quotaUsageBytes + content.size > s.quotaLimitBytes) {
            return StrataResult.Err(StrataError.PoolFull(content.size.toLong()))
        }

        s.uploadAttemptCount++
        val sessionUri = resumeSessionUri ?: run {
            val newUri = "fake-session-${s.nextIdCounter++}-$accountId"
            s.openSessions[newUri] = SessionState(fileName, properties, content.size)
            s.sessionsCreatedCount++
            newUri
        }
        onSessionOpened(sessionUri)

        val session = s.openSessions[sessionUri]
            ?: return StrataResult.Err(StrataError.Fatal("resumed a session that no longer exists (simulates 1-week expiry / lost session)"))

        if (s.forceRateLimitCount > 0) {
            s.forceRateLimitCount--
            return StrataResult.Err(StrataError.RateLimited(100))
        }
        if (s.forceServerErrorCount > 0) {
            s.forceServerErrorCount--
            return StrataResult.Err(StrataError.ServerError(503))
        }

        val interruptAt = s.interruptNextUploadAtBytes
        if (interruptAt != null) {
            s.interruptNextUploadAtBytes = null
            val commitNow = interruptAt.coerceIn(session.committedBytes, content.size)
            // Only the bytes actually "received" before the simulated drop are committed —
            // never write past what a real dropped connection would have delivered.
            System.arraycopy(content, session.committedBytes, session.content, session.committedBytes, commitNow - session.committedBytes)
            session.committedBytes = commitNow
            return StrataResult.Err(StrataError.NetworkError(null))
        }

        // Resume from exactly where we left off — a correct caller sends the same full ciphertext
        // each attempt; the backend (real or fake) is responsible for only applying the tail that
        // was not already committed, so bytes are never duplicated or re-derived from scratch.
        System.arraycopy(content, session.committedBytes, session.content, session.committedBytes, content.size - session.committedBytes)
        session.committedBytes = content.size

        val driveFileId = session.finalFileId ?: nextId().also { session.finalFileId = it }
        var storedContent = session.content.copyOf()
        if (s.corruptNextUpload) {
            s.corruptNextUpload = false
            storedContent = storedContent.copyOf()
            storedContent[0] = (storedContent[0] + 1).toByte()
        }
        val md5 = md5Hex(storedContent)
        s.files[driveFileId] = FakeFile(driveFileId, storedContent, properties, createdAtMs = clock.wallClockMs())
        s.quotaUsageBytes += content.size
        s.openSessions.remove(sessionUri)

        return StrataResult.Ok(UploadOutcome(driveFileId, md5, content.size.toLong()))
    }

    override suspend fun getFileStatus(accountId: String, driveFileId: String): StrataResult<DriveFileStatus> {
        val s = state(accountId)
        if (s.unreachable) return StrataResult.Err(StrataError.NetworkError(null))
        val f = s.files[driveFileId] ?: return StrataResult.Err(StrataError.NotFound(driveFileId))
        return StrataResult.Ok(toStatus(f))
    }

    override suspend fun downloadFile(accountId: String, driveFileId: String): StrataResult<ByteArray> {
        val s = state(accountId)
        if (s.unreachable) return StrataResult.Err(StrataError.NetworkError(null))
        val f = s.files[driveFileId] ?: return StrataResult.Err(StrataError.NotFound(driveFileId))
        if (f.permanentlyDeleted) return StrataResult.Err(StrataError.NotFound(driveFileId))
        return StrataResult.Ok(f.content.copyOf())
    }

    override suspend fun trashFile(accountId: String, driveFileId: String): StrataResult<Unit> {
        val f = state(accountId).files[driveFileId] ?: return StrataResult.Err(StrataError.NotFound(driveFileId))
        f.trashed = true
        return StrataResult.Ok(Unit)
    }

    override suspend fun untrashFile(accountId: String, driveFileId: String): StrataResult<Unit> {
        val f = state(accountId).files[driveFileId] ?: return StrataResult.Err(StrataError.NotFound(driveFileId))
        f.trashed = false
        return StrataResult.Ok(Unit)
    }

    override suspend fun permanentlyDeleteFile(accountId: String, driveFileId: String): StrataResult<Unit> {
        val s = state(accountId)
        val f = s.files[driveFileId] ?: return StrataResult.Err(StrataError.NotFound(driveFileId))
        f.permanentlyDeleted = true
        s.quotaUsageBytes = (s.quotaUsageBytes - f.content.size).coerceAtLeast(0)
        s.files.remove(driveFileId)
        return StrataResult.Ok(Unit)
    }

    override suspend fun listAppFiles(accountId: String, pageToken: String?): StrataResult<DriveListPage> {
        val s = state(accountId)
        if (s.unreachable) return StrataResult.Err(StrataError.NetworkError(null))
        // Single-page fake — pagination correctness is exercised at the StorageBackend contract
        // level implicitly via the loop-until-null-token pattern every caller already uses.
        val files = s.files.values.filter { !it.permanentlyDeleted }.map { toStatus(it) }
        return StrataResult.Ok(DriveListPage(files, nextPageToken = null))
    }

    override suspend fun writeAppDataFile(accountId: String, name: String, content: ByteArray): StrataResult<AppDataEntry> {
        val s = state(accountId)
        if (s.unreachable) return StrataResult.Err(StrataError.NetworkError(null))
        val existing = s.appData[name]
        val id = existing?.driveFileId ?: nextId()
        val createdAt = existing?.createdAtMs ?: clock.wallClockMs()
        s.appData[name] = FakeFile(id, content.copyOf(), emptyMap(), createdAtMs = createdAt)
        return StrataResult.Ok(AppDataEntry(name, id, createdAt))
    }

    override suspend fun readAppDataFile(accountId: String, name: String): StrataResult<ByteArray?> {
        val s = state(accountId)
        if (s.unreachable) return StrataResult.Err(StrataError.NetworkError(null))
        return StrataResult.Ok(s.appData[name]?.content?.copyOf())
    }

    override suspend fun listAppDataFiles(accountId: String): StrataResult<List<AppDataEntry>> {
        val s = state(accountId)
        if (s.unreachable) return StrataResult.Err(StrataError.NetworkError(null))
        return StrataResult.Ok(s.appData.entries.map { (name, f) -> AppDataEntry(name, f.driveFileId, f.createdAtMs) })
    }

    override suspend fun deleteAppDataFile(accountId: String, name: String): StrataResult<Unit> {
        state(accountId).appData.remove(name)
        return StrataResult.Ok(Unit)
    }

    private fun toStatus(f: FakeFile) = DriveFileStatus(
        driveFileId = f.driveFileId, sizeBytes = f.content.size.toLong(),
        md5Hex = md5Hex(f.content), trashed = f.trashed, properties = f.properties, createdAtMs = f.createdAtMs,
    )

    private fun md5Hex(bytes: ByteArray): String = Hex.encode(MessageDigest.getInstance("MD5").digest(bytes))
}
