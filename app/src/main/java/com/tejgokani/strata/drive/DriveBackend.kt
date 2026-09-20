package com.tejgokani.strata.drive

import com.tejgokani.strata.auth.TokenProvider
import com.tejgokani.strata.core.StrataError
import com.tejgokani.strata.core.StrataResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dispatcher
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Real Google Drive REST v3 implementation of [StorageBackend], built directly on OkHttp rather
 * than `google-api-services-drive` (plan §3 — that library pulls Guava + Apache HttpClient and
 * fights the manual Content-Range/308 handling a resumable upload needs) or Retrofit (same
 * fight, plus an extra abstraction layer that doesn't help here).
 *
 * Honest limitation (plan §12): this class has not been exercised against live Drive in this
 * build pass — there is no emulator image, no attached device, and account setup is the user's
 * own step. Every other engine component is proven by FakeDriveBackend in the JVM test suite;
 * this file is where that abstraction meets the real network for the first time.
 */
class DriveBackend(
    private val tokenProvider: TokenProvider,
    private val emailForAccountId: suspend (String) -> String,
) : StorageBackend {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        // Default maxRequestsPerHost is 5, which would silently serialize every account's
        // traffic against www.googleapis.com (plan §6.8) — raised well above our own
        // per-account concurrency cap (2 metered / 5 unmetered) so OkHttp is never the bottleneck.
        .dispatcher(Dispatcher().apply { maxRequestsPerHost = 16 })
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private suspend fun bearer(accountId: String): StrataResult<String> {
        val email = emailForAccountId(accountId)
        return tokenProvider.getToken(email).map { "Bearer $it" }
    }

    private suspend fun exec(request: Request): StrataResult<Pair<Int, String>> = withContext(Dispatchers.IO) {
        try {
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                StrataResult.Ok(resp.code to body)
            }
        } catch (e: java.io.IOException) {
            StrataResult.Err(StrataError.NetworkError(e))
        }
    }

    private fun errorFor(code: Int, body: String): StrataError = when {
        code == 401 -> StrataError.NeedsConsent("token rejected")
        code == 404 -> StrataError.NotFound("drive file")
        DriveErrors.isQuotaExceeded(code, body) -> StrataError.PoolFull(0)
        DriveErrors.isRetryable(code) -> if (code == 429) StrataError.RateLimited(0) else StrataError.ServerError(code)
        else -> StrataError.Fatal("Drive HTTP $code: ${body.take(300)}")
    }

    override suspend fun getQuota(accountId: String): StrataResult<AccountQuota> {
        val auth = when (val r = bearer(accountId)) { is StrataResult.Ok -> r.value; is StrataResult.Err -> return r }
        val request = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/about?fields=storageQuota")
            .header("Authorization", auth).get().build()
        val (code, body) = when (val r = exec(request)) { is StrataResult.Ok -> r.value; is StrataResult.Err -> return r }
        if (code != 200) return StrataResult.Err(errorFor(code, body))
        val quota = JSONObject(body).optJSONObject("storageQuota") ?: JSONObject()
        return StrataResult.Ok(
            AccountQuota(
                // "limit" is absent entirely for unlimited-storage accounts (rare, but documented).
                limitBytes = if (quota.has("limit")) quota.optString("limit").toLongOrNull() ?: 0L else Long.MAX_VALUE,
                usageBytes = quota.optString("usage", "0").toLongOrNull() ?: 0L,
                usageInDriveBytes = quota.optString("usageInDrive", "0").toLongOrNull() ?: 0L,
                usageInDriveTrashBytes = quota.optString("usageInDriveTrash", "0").toLongOrNull() ?: 0L,
            )
        )
    }

    override suspend fun uploadChunk(
        accountId: String, fileName: String, folderPathHint: String, content: ByteArray,
        properties: Map<String, String>, resumeSessionUri: String?, onSessionOpened: suspend (String) -> Unit,
    ): StrataResult<UploadOutcome> {
        val auth = when (val r = bearer(accountId)) { is StrataResult.Ok -> r.value; is StrataResult.Err -> return r }

        val sessionUri = resumeSessionUri ?: run {
            val metadata = JSONObject().apply {
                put("name", fileName)
                put("appProperties", JSONObject(properties as Map<*, *>))
            }
            val initRequest = Request.Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable")
                .header("Authorization", auth)
                .header("X-Upload-Content-Type", "application/octet-stream")
                .header("X-Upload-Content-Length", content.size.toString())
                .post(metadata.toString().toRequestBody(jsonMedia))
                .build()
            val outcome = withContext(Dispatchers.IO) {
                try {
                    client.newCall(initRequest).execute().use { resp ->
                        if (resp.code !in 200..299) {
                            StrataResult.Err(errorFor(resp.code, resp.body?.string().orEmpty()))
                        } else {
                            val location = resp.header("Location")
                            if (location != null) StrataResult.Ok(location)
                            else StrataResult.Err(StrataError.Fatal("resumable session opened but no Location header returned"))
                        }
                    }
                } catch (e: java.io.IOException) {
                    StrataResult.Err(StrataError.NetworkError(e))
                }
            }
            when (outcome) { is StrataResult.Ok -> outcome.value; is StrataResult.Err -> return outcome }
        }

        // Resuming an existing session (resumeSessionUri != null): probe first so the PUT's
        // Content-Range starts exactly where Drive says it left off, per R9. Sending from byte 0
        // again on a resume would desync from what the server already committed and Drive would
        // reject the mismatched range.
        val startOffset: Long = if (resumeSessionUri != null) {
            when (val probe = probeResumeStatus(sessionUri, content.size.toLong())) {
                is ResumeProbe.AlreadyComplete -> {
                    onSessionOpened(sessionUri)
                    return StrataResult.Ok(probe.outcome)
                }
                is ResumeProbe.Incomplete -> probe.committedBytes
                is ResumeProbe.Failed -> return StrataResult.Err(probe.error)
            }
        } else 0L

        return uploadToSession(sessionUri, content, startOffset, onSessionOpened)
    }

    private suspend fun uploadToSession(
        sessionUri: String, content: ByteArray, startOffset: Long, onSessionOpened: suspend (String) -> Unit,
    ): StrataResult<UploadOutcome> {
        onSessionOpened(sessionUri) // persist BEFORE sending any (further) content byte (R9 / plan §6.9)

        val remaining = if (startOffset > 0) content.copyOfRange(startOffset.toInt(), content.size) else content
        val body = remaining.toRequestBody(null, 0, remaining.size)
        val putRequest = Request.Builder()
            .url(sessionUri)
            .header("Content-Range", "bytes $startOffset-${content.size - 1}/${content.size}")
            .put(body)
            .build()
        val (code, respBody) = when (val r = exec(putRequest)) { is StrataResult.Ok -> r.value; is StrataResult.Err -> return r }
        if (code !in listOf(200, 201)) return StrataResult.Err(errorFor(code, respBody))
        return StrataResult.Ok(parseUploadOutcome(JSONObject(respBody), content.size.toLong()))
    }

    private fun parseUploadOutcome(json: JSONObject, cipherSizeBytes: Long) = UploadOutcome(
        driveFileId = json.getString("id"),
        cipherMd5Hex = json.optString("md5Checksum", ""),
        cipherSizeBytes = cipherSizeBytes,
    )

    private sealed class ResumeProbe {
        data class Incomplete(val committedBytes: Long) : ResumeProbe()
        data class AlreadyComplete(val outcome: UploadOutcome) : ResumeProbe()
        data class Failed(val error: StrataError) : ResumeProbe()
    }

    /**
     * Probes an interrupted session per R9: an empty PUT with `Content-Range: "bytes {wildcard}/SIZE"`.
     * A `308` reports how many bytes Drive actually committed via its `Range` response header —
     * that becomes the next PUT's start offset. A `200`/`201` means the upload had in fact
     * already completed (the previous attempt's response was merely lost on our end); Drive
     * returns the finished file resource in that same response body.
     */
    private suspend fun probeResumeStatus(sessionUri: String, totalSize: Long): ResumeProbe {
        val putRequest = Request.Builder()
            .url(sessionUri)
            .header("Content-Range", "bytes */$totalSize")
            .put(ByteArray(0).toRequestBody(null))
            .build()
        return withContext(Dispatchers.IO) {
            try {
                client.newCall(putRequest).execute().use { resp ->
                    when (resp.code) {
                        308 -> {
                            val range = resp.header("Range") // e.g. "bytes=0-524287"
                            val committed = range?.substringAfter('-')?.toLongOrNull()?.plus(1) ?: 0L
                            ResumeProbe.Incomplete(committed)
                        }
                        200, 201 -> ResumeProbe.AlreadyComplete(parseUploadOutcome(JSONObject(resp.body?.string().orEmpty()), totalSize))
                        else -> ResumeProbe.Failed(errorFor(resp.code, resp.body?.string().orEmpty()))
                    }
                }
            } catch (e: java.io.IOException) {
                ResumeProbe.Failed(StrataError.NetworkError(e))
            }
        }
    }

    override suspend fun getFileStatus(accountId: String, driveFileId: String): StrataResult<DriveFileStatus> {
        val auth = when (val r = bearer(accountId)) { is StrataResult.Ok -> r.value; is StrataResult.Err -> return r }
        val fields = "id,size,md5Checksum,trashed,appProperties,createdTime"
        val request = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files/$driveFileId?fields=$fields")
            .header("Authorization", auth).get().build()
        val (code, body) = when (val r = exec(request)) { is StrataResult.Ok -> r.value; is StrataResult.Err -> return r }
        if (code != 200) return StrataResult.Err(errorFor(code, body))
        return StrataResult.Ok(parseFileStatus(JSONObject(body)))
    }

    override suspend fun downloadFile(accountId: String, driveFileId: String): StrataResult<ByteArray> {
        val auth = when (val r = bearer(accountId)) { is StrataResult.Ok -> r.value; is StrataResult.Err -> return r }
        val request = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files/$driveFileId?alt=media")
            .header("Authorization", auth).get().build()
        return withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@use StrataResult.Err(errorFor(resp.code, resp.body?.string().orEmpty()))
                    StrataResult.Ok(resp.body?.bytes() ?: ByteArray(0))
                }
            } catch (e: java.io.IOException) {
                StrataResult.Err(StrataError.NetworkError(e))
            }
        }
    }

    override suspend fun trashFile(accountId: String, driveFileId: String): StrataResult<Unit> =
        patchTrashed(accountId, driveFileId, trashed = true)

    override suspend fun untrashFile(accountId: String, driveFileId: String): StrataResult<Unit> =
        patchTrashed(accountId, driveFileId, trashed = false)

    private suspend fun patchTrashed(accountId: String, driveFileId: String, trashed: Boolean): StrataResult<Unit> {
        val auth = when (val r = bearer(accountId)) { is StrataResult.Ok -> r.value; is StrataResult.Err -> return r }
        val body = JSONObject().put("trashed", trashed).toString().toRequestBody(jsonMedia)
        val request = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files/$driveFileId")
            .header("Authorization", auth).patch(body).build()
        val (code, respBody) = when (val r = exec(request)) { is StrataResult.Ok -> r.value; is StrataResult.Err -> return r }
        return if (code == 200) StrataResult.Ok(Unit) else StrataResult.Err(errorFor(code, respBody))
    }

    /** Permanent delete. NEVER routes through files.emptyTrash, which would empty the user's ENTIRE trash (R13). */
    override suspend fun permanentlyDeleteFile(accountId: String, driveFileId: String): StrataResult<Unit> {
        val auth = when (val r = bearer(accountId)) { is StrataResult.Ok -> r.value; is StrataResult.Err -> return r }
        val request = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files/$driveFileId")
            .header("Authorization", auth).delete().build()
        val (code, body) = when (val r = exec(request)) { is StrataResult.Ok -> r.value; is StrataResult.Err -> return r }
        return if (code in listOf(200, 204)) StrataResult.Ok(Unit) else StrataResult.Err(errorFor(code, body))
    }

    override suspend fun listAppFiles(accountId: String, pageToken: String?): StrataResult<DriveListPage> {
        val auth = when (val r = bearer(accountId)) { is StrataResult.Ok -> r.value; is StrataResult.Err -> return r }
        val fields = "nextPageToken,files(id,size,md5Checksum,trashed,appProperties,createdTime)"
        val urlBuilder = StringBuilder("https://www.googleapis.com/drive/v3/files?spaces=drive&pageSize=1000&fields=$fields")
        pageToken?.let { urlBuilder.append("&pageToken=").append(it) }
        val request = Request.Builder().url(urlBuilder.toString()).header("Authorization", auth).get().build()
        val (code, body) = when (val r = exec(request)) { is StrataResult.Ok -> r.value; is StrataResult.Err -> return r }
        if (code != 200) return StrataResult.Err(errorFor(code, body))
        val json = JSONObject(body)
        val filesArray = json.optJSONArray("files") ?: JSONArray()
        val files = (0 until filesArray.length()).map { parseFileStatus(filesArray.getJSONObject(it)) }
        return StrataResult.Ok(DriveListPage(files, json.optString("nextPageToken", null.toString()).takeIf { json.has("nextPageToken") }))
    }

    private fun parseFileStatus(json: JSONObject): DriveFileStatus {
        val props = json.optJSONObject("appProperties")
        val propsMap = if (props != null) props.keys().asSequence().associateWith { props.getString(it) } else emptyMap()
        return DriveFileStatus(
            driveFileId = json.getString("id"),
            sizeBytes = json.optString("size", "0").toLongOrNull() ?: 0L,
            md5Hex = json.optString("md5Checksum", null.toString()).takeIf { json.has("md5Checksum") },
            trashed = json.optBoolean("trashed", false),
            properties = propsMap,
            createdAtMs = parseRfc3339(json.optString("createdTime", "")),
        )
    }

    private fun parseRfc3339(text: String): Long =
        try { java.time.Instant.parse(text).toEpochMilli() } catch (e: Exception) { 0L }

    override suspend fun writeAppDataFile(accountId: String, name: String, content: ByteArray): StrataResult<AppDataEntry> {
        val auth = when (val r = bearer(accountId)) { is StrataResult.Ok -> r.value; is StrataResult.Err -> return r }
        // appDataFolder "overwrite by name" = find-or-create, then upload content to that file id.
        val existing = findAppDataFile(accountId, name).getOrNull()
        val fileId = existing?.driveFileId

        val uploadUrl = if (fileId != null) {
            "https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media"
        } else {
            "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"
        }

        return if (fileId != null) {
            val request = Request.Builder().url(uploadUrl).header("Authorization", auth)
                .patch(content.toRequestBody(null)).build()
            val (code, body) = when (val r = exec(request)) { is StrataResult.Ok -> r.value; is StrataResult.Err -> return r }
            if (code != 200) return StrataResult.Err(errorFor(code, body))
            StrataResult.Ok(AppDataEntry(name, fileId, existing.createdAtMs))
        } else {
            val metadata = JSONObject().put("name", name).put("parents", JSONArray().put("appDataFolder"))
            val multipart = buildMultipart(metadata, content)
            val request = Request.Builder().url(uploadUrl).header("Authorization", auth)
                .header("Content-Type", "multipart/related; boundary=$MULTIPART_BOUNDARY")
                .post(multipart).build()
            val (code, body) = when (val r = exec(request)) { is StrataResult.Ok -> r.value; is StrataResult.Err -> return r }
            if (code !in listOf(200, 201)) return StrataResult.Err(errorFor(code, body))
            val json = JSONObject(body)
            StrataResult.Ok(AppDataEntry(name, json.getString("id"), System.currentTimeMillis()))
        }
    }

    private fun buildMultipart(metadata: JSONObject, content: ByteArray): RequestBody {
        val boundary = MULTIPART_BOUNDARY
        val head = "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n${metadata}\r\n" +
            "--$boundary\r\nContent-Type: application/octet-stream\r\n\r\n"
        val tail = "\r\n--$boundary--"
        val bytes = head.toByteArray(Charsets.UTF_8) + content + tail.toByteArray(Charsets.UTF_8)
        return bytes.toRequestBody(null)
    }

    private suspend fun findAppDataFile(accountId: String, name: String): StrataResult<AppDataEntry?> {
        val auth = when (val r = bearer(accountId)) { is StrataResult.Ok -> r.value; is StrataResult.Err -> return r }
        val q = java.net.URLEncoder.encode("name = '$name' and trashed = false", "UTF-8")
        val request = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=$q&fields=files(id,name,createdTime)")
            .header("Authorization", auth).get().build()
        val (code, body) = when (val r = exec(request)) { is StrataResult.Ok -> r.value; is StrataResult.Err -> return r }
        if (code != 200) return StrataResult.Err(errorFor(code, body))
        val arr = JSONObject(body).optJSONArray("files") ?: JSONArray()
        if (arr.length() == 0) return StrataResult.Ok(null)
        val f = arr.getJSONObject(0)
        return StrataResult.Ok(AppDataEntry(name, f.getString("id"), parseRfc3339(f.optString("createdTime", ""))))
    }

    override suspend fun readAppDataFile(accountId: String, name: String): StrataResult<ByteArray?> {
        val auth = when (val r = bearer(accountId)) { is StrataResult.Ok -> r.value; is StrataResult.Err -> return r }
        val entry = when (val r = findAppDataFile(accountId, name)) { is StrataResult.Ok -> r.value; is StrataResult.Err -> return r }
            ?: return StrataResult.Ok(null)
        val request = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files/${entry.driveFileId}?alt=media")
            .header("Authorization", auth).get().build()
        return withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) StrataResult.Err(errorFor(resp.code, resp.body?.string().orEmpty()))
                    else StrataResult.Ok(resp.body?.bytes())
                }
            } catch (e: java.io.IOException) {
                StrataResult.Err(StrataError.NetworkError(e))
            }
        }
    }

    override suspend fun listAppDataFiles(accountId: String): StrataResult<List<AppDataEntry>> {
        val auth = when (val r = bearer(accountId)) { is StrataResult.Ok -> r.value; is StrataResult.Err -> return r }
        val request = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&pageSize=1000&fields=files(id,name,createdTime)")
            .header("Authorization", auth).get().build()
        val (code, body) = when (val r = exec(request)) { is StrataResult.Ok -> r.value; is StrataResult.Err -> return r }
        if (code != 200) return StrataResult.Err(errorFor(code, body))
        val arr = JSONObject(body).optJSONArray("files") ?: JSONArray()
        val out = (0 until arr.length()).map {
            val f = arr.getJSONObject(it)
            AppDataEntry(f.getString("name"), f.getString("id"), parseRfc3339(f.optString("createdTime", "")))
        }
        return StrataResult.Ok(out)
    }

    override suspend fun deleteAppDataFile(accountId: String, name: String): StrataResult<Unit> {
        val entry = when (val r = findAppDataFile(accountId, name)) { is StrataResult.Ok -> r.value; is StrataResult.Err -> return r }
            ?: return StrataResult.Ok(Unit)
        return permanentlyDeleteFile(accountId, entry.driveFileId)
    }

    companion object {
        private const val MULTIPART_BOUNDARY = "strata_multipart_boundary"
    }
}
