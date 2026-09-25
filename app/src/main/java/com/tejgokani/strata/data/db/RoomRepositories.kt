package com.tejgokani.strata.data.db

import com.tejgokani.strata.core.CanonicalCodec
import com.tejgokani.strata.engine.AccountRecord
import com.tejgokani.strata.engine.AccountRepository
import com.tejgokani.strata.engine.ChunkRecord
import com.tejgokani.strata.engine.ChunkRepository
import com.tejgokani.strata.engine.FileRecord
import com.tejgokani.strata.engine.FileRepository
import com.tejgokani.strata.engine.PlacementRecord
import com.tejgokani.strata.engine.PlacementRepository
import com.tejgokani.strata.engine.ReservationRecord
import com.tejgokani.strata.engine.ReservationRepository
import com.tejgokani.strata.ledger.LedgerEvent
import com.tejgokani.strata.ledger.LedgerStore

/** Thin adapters wiring the pure engine's repository interfaces to Room DAOs (plan §9). */

class RoomAccountRepository(private val dao: AccountDao) : AccountRepository {
    override suspend fun get(id: String): AccountRecord? = dao.get(id)?.toRecord()
    override suspend fun all(): List<AccountRecord> = dao.all().map { it.toRecord() }
    override suspend fun upsert(account: AccountRecord) = dao.upsert(account.toEntity())
}

class RoomFileRepository(private val dao: FileDao) : FileRepository {
    override suspend fun get(id: String): FileRecord? = dao.get(id)?.toRecord()
    override suspend fun all(): List<FileRecord> = dao.all().map { it.toRecord() }
    override suspend fun upsert(file: FileRecord) = dao.upsert(file.toEntity())
}

class RoomChunkRepository(private val dao: ChunkDao) : ChunkRepository {
    override suspend fun get(id: String): ChunkRecord? = dao.get(id)?.toRecord()
    override suspend fun forFile(fileId: String): List<ChunkRecord> = dao.forFile(fileId).map { it.toRecord() }
    override suspend fun upsert(chunk: ChunkRecord) = dao.upsert(chunk.toEntity())
}

class RoomPlacementRepository(private val dao: PlacementDao) : PlacementRepository {
    override suspend fun get(id: String): PlacementRecord? = dao.get(id)?.toRecord()
    override suspend fun forChunk(chunkId: String): List<PlacementRecord> = dao.forChunk(chunkId).map { it.toRecord() }
    override suspend fun forAccount(accountId: String): List<PlacementRecord> = dao.forAccount(accountId).map { it.toRecord() }
    override suspend fun all(): List<PlacementRecord> = dao.all().map { it.toRecord() }
    override suspend fun upsert(placement: PlacementRecord) = dao.upsert(placement.toEntity())
}

class RoomReservationRepository(private val dao: ReservationDao) : ReservationRepository {
    override suspend fun reserve(accountId: String, bytes: Long, expiresAtMs: Long): ReservationRecord {
        val r = ReservationRecord(com.tejgokani.strata.core.Ids.newId(), accountId, bytes, expiresAtMs)
        dao.insert(r.toEntity())
        return r
    }
    override suspend fun release(reservationId: String) = dao.delete(reservationId)
    override suspend fun activeReservedBytes(accountId: String, nowMs: Long): Long {
        dao.pruneExpired(nowMs)
        return dao.activeBytes(accountId, nowMs)
    }
}

/** Bridges the pure [LedgerStore] contract to Room; payload is (de)serialized via CanonicalCodec. */
class RoomLedgerStore(private val dao: LedgerDao) : LedgerStore {
    override fun appendRaw(event: LedgerEvent) {
        kotlinx.coroutines.runBlocking {
            dao.append(
                LedgerEventEntity(
                    seq = event.seq, deviceId = event.deviceId, lamport = event.lamport,
                    wallClockMs = event.wallClockMs, monotonicMs = event.monotonicMs, type = event.type,
                    payload = CanonicalCodec.encode(event.payload), prevHash = event.prevHash, hash = event.hash,
                )
            )
        }
    }

    override fun lastForDevice(deviceId: String): LedgerEvent? = kotlinx.coroutines.runBlocking {
        dao.lastForDevice(deviceId)?.toLedgerEvent()
    }

    override fun allInOrder(): List<LedgerEvent> = kotlinx.coroutines.runBlocking {
        dao.allInOrder().map { it.toLedgerEvent() }
    }

    override fun nextSeq(): Long = kotlinx.coroutines.runBlocking { dao.maxSeq() + 1 }

    private fun LedgerEventEntity.toLedgerEvent() = LedgerEvent(
        seq, deviceId, lamport, wallClockMs, monotonicMs, type, CanonicalCodec.decode(payload), prevHash, hash,
    )
}
