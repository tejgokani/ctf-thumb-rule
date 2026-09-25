package com.tejgokani.strata.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts") suspend fun all(): List<AccountEntity>
    @Query("SELECT * FROM accounts WHERE id = :id") suspend fun get(id: String): AccountEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(a: AccountEntity)
}

@Dao
interface FileDao {
    @Query("SELECT * FROM files") suspend fun all(): List<FileEntity>
    @Query("SELECT * FROM files WHERE id = :id") suspend fun get(id: String): FileEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(f: FileEntity)
}

@Dao
interface ChunkDao {
    @Query("SELECT * FROM chunks WHERE id = :id") suspend fun get(id: String): ChunkEntity?
    @Query("SELECT * FROM chunks WHERE fileId = :fileId ORDER BY idx ASC") suspend fun forFile(fileId: String): List<ChunkEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(c: ChunkEntity)
}

@Dao
interface PlacementDao {
    @Query("SELECT * FROM placements WHERE id = :id") suspend fun get(id: String): PlacementEntity?
    @Query("SELECT * FROM placements WHERE chunkId = :chunkId") suspend fun forChunk(chunkId: String): List<PlacementEntity>
    @Query("SELECT * FROM placements WHERE accountId = :accountId") suspend fun forAccount(accountId: String): List<PlacementEntity>
    @Query("SELECT * FROM placements") suspend fun all(): List<PlacementEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(p: PlacementEntity)
}

@Dao
interface ReservationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(r: ReservationEntity)
    @Query("DELETE FROM reservations WHERE id = :id") suspend fun delete(id: String)
    @Query("DELETE FROM reservations WHERE expiresAtMs <= :nowMs") suspend fun pruneExpired(nowMs: Long)
    @Query("SELECT COALESCE(SUM(bytes), 0) FROM reservations WHERE accountId = :accountId AND expiresAtMs > :nowMs")
    suspend fun activeBytes(accountId: String, nowMs: Long): Long
}

@Dao
interface LedgerDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun append(e: LedgerEventEntity)
    @Query("SELECT * FROM ledger_events WHERE deviceId = :deviceId ORDER BY seq DESC LIMIT 1")
    suspend fun lastForDevice(deviceId: String): LedgerEventEntity?
    @Query("SELECT * FROM ledger_events ORDER BY seq ASC") suspend fun allInOrder(): List<LedgerEventEntity>
    @Query("SELECT COALESCE(MAX(seq), -1) FROM ledger_events") suspend fun maxSeq(): Long
    @Query("SELECT * FROM ledger_events ORDER BY seq DESC LIMIT :n") suspend fun tail(n: Int): List<LedgerEventEntity>
}
