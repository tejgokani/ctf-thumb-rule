package com.tejgokani.strata.fakes

import com.tejgokani.strata.core.FakeStrataClock
import com.tejgokani.strata.crypto.KeyVault
import com.tejgokani.strata.crypto.Kdf
import com.tejgokani.strata.engine.AccountRecord
import com.tejgokani.strata.engine.AuthState
import com.tejgokani.strata.engine.CircuitState
import com.tejgokani.strata.engine.InMemoryAccountRepository
import com.tejgokani.strata.engine.InMemoryChunkRepository
import com.tejgokani.strata.engine.InMemoryFileRepository
import com.tejgokani.strata.engine.InMemoryManifestSeqAllocator
import com.tejgokani.strata.engine.InMemoryPlacementRepository
import com.tejgokani.strata.engine.InMemoryReservationRepository
import com.tejgokani.strata.engine.ManifestReplicator
import com.tejgokani.strata.engine.TransferCoordinator
import com.tejgokani.strata.ledger.InMemoryLedgerStore
import com.tejgokani.strata.ledger.Ledger

/**
 * Wires the whole engine stack — everything except real Android/Drive — for integration-style
 * JVM tests (plan §11). This is the harness the E2E and disaster-recovery tests build on.
 */
class TestHarness(
    val clock: FakeStrataClock = FakeStrataClock(),
    val deviceId: String = "device-1",
    val poolId: String = "pool-1",
    passphrase: String = "test passphrase for the harness",
    chunkSizeBytes: Long = 64 * 1024, // small so tests run fast; a multiple-of-256KiB stand-in
    retryBaseDelayMs: Long = 1L, // near-zero so retry/backoff tests don't slow the suite
) {
    val backend = FakeDriveBackend(clock)
    val vault: KeyVault = KeyVault.fromPassphrase(passphrase.toCharArray(), Kdf.randomSalt(), iterations = 10_000)
    val accounts = InMemoryAccountRepository()
    val files = InMemoryFileRepository()
    val chunks = InMemoryChunkRepository()
    val placements = InMemoryPlacementRepository()
    val reservations = InMemoryReservationRepository()
    val ledgerStore = InMemoryLedgerStore()
    val ledger = Ledger(ledgerStore, clock, deviceId)
    val seqAllocator = InMemoryManifestSeqAllocator()
    val manifestReplicator = ManifestReplicator(backend, vault, deviceId)

    val coordinator = TransferCoordinator(
        backend, vault, ledger, accounts, files, chunks, placements, reservations,
        manifestReplicator, seqAllocator, clock, deviceId, poolId, chunkSizeBytes,
        retryBaseDelayMs = retryBaseDelayMs,
    )

    suspend fun addAccount(id: String, limitBytes: Long = 15_000_000_000L, usageBytes: Long = 0L): AccountRecord {
        backend.setQuota(id, limitBytes, usageBytes)
        val record = AccountRecord(
            id = id, email = "$id@example.com", quotaLimitBytes = limitBytes, quotaUsageBytes = usageBytes,
            quotaUsageInDriveTrashBytes = 0L, reservedBytes = 0L, authState = AuthState.OK,
            circuitState = CircuitState.CLOSED, circuitOpenUntilMs = 0L, dailyUploadedBytes = 0L,
            dailyBudgetResetAtMs = 0L, lastFullEnumerationMs = 0L, lastQuotaRefreshMs = 0L,
        )
        accounts.upsert(record)
        return record
    }

    suspend fun allAccountIds(): List<String> = accounts.all().map { it.id }
}
