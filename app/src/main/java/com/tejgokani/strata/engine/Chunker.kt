package com.tejgokani.strata.engine

import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/**
 * Abstracts "a readable file" so the engine never depends on Android's SAF/ContentResolver
 * directly — only java.io, which is testable on a plain JVM. The Android layer implements this
 * over `ContentResolver.openInputStream(uri)`.
 */
interface ByteSource {
    fun sizeBytes(): Long
    fun openStream(): InputStream
}

class ByteArraySource(private val bytes: ByteArray) : ByteSource {
    override fun sizeBytes(): Long = bytes.size.toLong()
    override fun openStream(): InputStream = bytes.inputStream()
}

data class PlainChunk(val index: Int, val bytes: ByteArray, val sha256Hex: String)

/**
 * Splits a [ByteSource] into fixed-size plaintext chunks, default 16 MiB — a multiple of the
 * 256 KiB resumable-upload frame size (R9) and comfortably inside a ~1h access token's lifetime
 * even on a slow link (plan §6.1).
 *
 * Chunks are produced one at a time from a stream (never the whole file materialized in memory);
 * combined with TransferCoordinator's bounded parallelism this keeps peak memory low even for
 * huge files (plan §6.8).
 */
object Chunker {
    const val DEFAULT_CHUNK_SIZE_BYTES = 16L * 1024 * 1024 // 16 MiB, multiple of 256 KiB

    fun chunkCountFor(totalSizeBytes: Long, chunkSizeBytes: Long = DEFAULT_CHUNK_SIZE_BYTES): Int {
        if (totalSizeBytes == 0L) return 1 // an empty file is still one (empty) chunk
        return ((totalSizeBytes + chunkSizeBytes - 1) / chunkSizeBytes).toInt()
    }

    /** Lazily yields [PlainChunk]s. The caller must fully consume the sequence before the source is closed. */
    fun split(source: ByteSource, chunkSizeBytes: Long = DEFAULT_CHUNK_SIZE_BYTES): Sequence<PlainChunk> = sequence {
        require(chunkSizeBytes > 0) { "chunkSizeBytes must be positive" }
        source.openStream().use { input ->
            var index = 0
            var producedAny = false
            val buffer = ByteArray(chunkSizeBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            while (true) {
                val read = readFully(input, buffer)
                if (read == 0) break
                producedAny = true
                val chunkBytes = if (read == buffer.size) buffer.copyOf() else buffer.copyOf(read)
                yield(PlainChunk(index, chunkBytes, sha256Hex(chunkBytes)))
                index++
                if (read < buffer.size) break // last, short read: end of stream
            }
            if (!producedAny) {
                // Zero-byte file: still emit exactly one empty chunk so downstream logic (a file
                // always has chunkCount >= 1) never has to special-case "no chunks".
                yield(PlainChunk(0, ByteArray(0), sha256Hex(ByteArray(0))))
            }
        }
    }

    /** Reads until [buffer] is full or the stream ends; returns the number of bytes actually read (may be 0). */
    private fun readFully(input: InputStream, buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val n = input.read(buffer, total, buffer.size - total)
            if (n < 0) break
            total += n
        }
        return total
    }

    fun sha256Hex(bytes: ByteArray): String =
        com.tejgokani.strata.core.Hex.encode(MessageDigest.getInstance("SHA-256").digest(bytes))

    fun sha256HexOfStream(source: ByteSource): String {
        val digest = MessageDigest.getInstance("SHA-256")
        source.openStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return com.tejgokani.strata.core.Hex.encode(digest.digest())
    }
}

/**
 * Reassembles a file from ordered plaintext chunks and verifies the whole-file hash before
 * declaring success (plan §6.1/§6.5 — never hand back unverified bytes; see also R18 about not
 * streaming unauthenticated plaintext to the final destination).
 */
object Reassembler {
    class IntegrityException(message: String) : Exception(message)

    /** [orderedPlainChunks] must already be in ascending index order and GCM-tag-verified. */
    fun reassemble(orderedPlainChunks: List<ByteArray>, expectedSha256Hex: String, out: OutputStream) {
        val digest = MessageDigest.getInstance("SHA-256")
        for (chunk in orderedPlainChunks) {
            digest.update(chunk)
            out.write(chunk)
        }
        out.flush()
        val actual = com.tejgokani.strata.core.Hex.encode(digest.digest())
        if (!actual.equals(expectedSha256Hex, ignoreCase = true)) {
            throw IntegrityException("Whole-file hash mismatch: expected $expectedSha256Hex, got $actual")
        }
    }
}
