package com.tejgokani.strata.engine

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Persistence seams. Room-backed implementations live in data/db (the real app); tests use
 * simple in-memory implementations so the whole engine — chunking, placement, transfer,
 * recovery, scrub — runs on a plain JVM with no Android runtime (plan §3: no emulator/device
 * available this pass, so this is the only way any of it gets proven).
 */

interface AccountRepository {
    suspend fun get(id: String): AccountRecord?
    suspend fun all(): List<AccountRecord>
    suspend fun upsert(account: AccountRecord)
}

interface FileRepository {
    suspend fun get(id: String): FileRecord?
    suspend fun all(): List<FileRecord>
    suspend fun upsert(file: FileRecord)
}

interface ChunkRepository {
    suspend fun get(id: String): ChunkRecord?
    suspend fun forFile(fileId: String): List<ChunkRecord>
    suspend fun upsert(chunk: ChunkRecord)
}

interface PlacementRepository {
    suspend fun get(id: String): PlacementRecord?
    suspend fun forChunk(chunkId: String): List<PlacementRecord>
    suspend fun forAccount(accountId: String): List<PlacementRecord>
    suspend fun all(): List<PlacementRecord>
    suspend fun upsert(placement: PlacementRecord)
}

interface ReservationRepository {
    suspend fun reserve(accountId: String, bytes: Long, expiresAtMs: Long): ReservationRecord
    suspend fun release(reservationId: String)
    /** Sums non-expired reservations for [accountId] as of [nowMs], pruning expired ones first. */
    suspend fun activeReservedBytes(accountId: String, nowMs: Long): Long
}

/**
 * Simple in-memory implementations shared by every JVM test in this project. Guarded by a
 * coroutine [Mutex] rather than `@Synchronized` — the latter is not applicable to suspend
 * functions and would block the underlying thread rather than suspending, which is exactly the
 * wrong tradeoff in code meant to run under bounded concurrency (plan §6.8).
 */
class InMemoryAccountRepository : AccountRepository {
    private val map = LinkedHashMap<String, AccountRecord>()
    private val mutex = Mutex()
    override suspend fun get(id: String) = mutex.withLock { map[id] }
    override suspend fun all() = mutex.withLock { map.values.toList() }
    override suspend fun upsert(account: AccountRecord) = mutex.withLock { map[account.id] = account }
}

class InMemoryFileRepository : FileRepository {
    private val map = LinkedHashMap<String, FileRecord>()
    private val mutex = Mutex()
    override suspend fun get(id: String) = mutex.withLock { map[id] }
    override suspend fun all() = mutex.withLock { map.values.toList() }
    override suspend fun upsert(file: FileRecord) = mutex.withLock { map[file.id] = file }
}

class InMemoryChunkRepository : ChunkRepository {
    private val map = LinkedHashMap<String, ChunkRecord>()
    private val mutex = Mutex()
    override suspend fun get(id: String) = mutex.withLock { map[id] }
    override suspend fun forFile(fileId: String) = mutex.withLock { map.values.filter { it.fileId == fileId }.sortedBy { it.index } }
    override suspend fun upsert(chunk: ChunkRecord) = mutex.withLock { map[chunk.id] = chunk }
}

class InMemoryPlacementRepository : PlacementRepository {
    private val map = LinkedHashMap<String, PlacementRecord>()
    private val mutex = Mutex()
    override suspend fun get(id: String) = mutex.withLock { map[id] }
    override suspend fun forChunk(chunkId: String) = mutex.withLock { map.values.filter { it.chunkId == chunkId } }
    override suspend fun forAccount(accountId: String) = mutex.withLock { map.values.filter { it.accountId == accountId } }
    override suspend fun all() = mutex.withLock { map.values.toList() }
    override suspend fun upsert(placement: PlacementRecord) = mutex.withLock { map[placement.id] = placement }
}

class InMemoryReservationRepository : ReservationRepository {
    private val map = LinkedHashMap<String, ReservationRecord>()
    private val mutex = Mutex()

    override suspend fun reserve(accountId: String, bytes: Long, expiresAtMs: Long): ReservationRecord = mutex.withLock {
        val r = ReservationRecord(com.tejgokani.strata.core.Ids.newId(), accountId, bytes, expiresAtMs)
        map[r.id] = r
        r
    }

    override suspend fun release(reservationId: String) = mutex.withLock { map.remove(reservationId); Unit }

    override suspend fun activeReservedBytes(accountId: String, nowMs: Long): Long = mutex.withLock {
        val expired = map.values.filter { it.expiresAtMs <= nowMs }
        expired.forEach { map.remove(it.id) }
        map.values.filter { it.accountId == accountId }.sumOf { it.bytes }
    }
}
