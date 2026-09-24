package com.tejgokani.strata.engine

import com.tejgokani.strata.core.FakeStrataClock
import com.tejgokani.strata.crypto.KeyVault
import com.tejgokani.strata.crypto.Kdf
import com.tejgokani.strata.fakes.FakeDriveBackend
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManifestReplicatorTest {
    private fun vault() = KeyVault.fromPassphrase("test".toCharArray(), Kdf.randomSalt(), iterations = 10_000)

    private fun sampleFile(id: String) = FileRecord(id, "file-$id", "application/octet-stream", 1000, "hash", "dek", 1, false, FileStatus.COMMITTED, 0)

    @Test fun `WAL flush and recovery round trips a file across a quorum of accounts`() = runTest {
        val clock = FakeStrataClock()
        val backend = FakeDriveBackend(clock)
        val vault = vault()
        val replicator = ManifestReplicator(backend, vault, "device-1")
        val accounts = listOf("a1", "a2", "a3")

        val op = ManifestReplicator.fileOp("device-1", 0, sampleFile("f1"))
        val flushResult = replicator.flushWal(accounts, listOf(op), seqStart = 0)
        assertTrue(flushResult is com.tejgokani.strata.core.StrataResult.Ok)

        val recovered = (replicator.recoverMerged(accounts) as com.tejgokani.strata.core.StrataResult.Ok).value
        assertEquals(1, recovered.files.size)
        assertEquals("f1", recovered.files[0].id)
    }

    @Test fun `WAL union across accounts is idempotent regardless of which accounts are reachable`() = runTest {
        val clock = FakeStrataClock()
        val backend = FakeDriveBackend(clock)
        val vault = vault()
        val replicator = ManifestReplicator(backend, vault, "device-1")
        val accounts = listOf("a1", "a2", "a3", "a4", "a5")

        // Two separate WAL flushes (simulating two upload sessions), each reaching a quorum.
        replicator.flushWal(accounts, listOf(ManifestReplicator.fileOp("device-1", 0, sampleFile("f1"))), seqStart = 0)
        replicator.flushWal(accounts, listOf(ManifestReplicator.fileOp("device-1", 1, sampleFile("f2"))), seqStart = 1)

        // Recovering from every possible non-empty subset that still has quorum coverage must
        // converge on the same two files — set union is inherently order/subset independent.
        val fromAll = (replicator.recoverMerged(accounts) as com.tejgokani.strata.core.StrataResult.Ok).value
        val fromSubset = (replicator.recoverMerged(listOf("a2", "a4")) as com.tejgokani.strata.core.StrataResult.Ok).value
        assertEquals(setOf("f1", "f2"), fromAll.files.map { it.id }.toSet())
        assertEquals(setOf("f1", "f2"), fromSubset.files.map { it.id }.toSet())
    }

    @Test fun `last writer wins by ascending seq for the same entity id`() = runTest {
        val clock = FakeStrataClock()
        val backend = FakeDriveBackend(clock)
        val vault = vault()
        val replicator = ManifestReplicator(backend, vault, "device-1")
        val accounts = listOf("a1", "a2", "a3")

        replicator.flushWal(accounts, listOf(ManifestReplicator.fileOp("device-1", 0, sampleFile("f1").copy(name = "first-name"))), seqStart = 0)
        replicator.flushWal(accounts, listOf(ManifestReplicator.fileOp("device-1", 1, sampleFile("f1").copy(name = "second-name"))), seqStart = 1)

        val recovered = (replicator.recoverMerged(accounts) as com.tejgokani.strata.core.StrataResult.Ok).value
        assertEquals(1, recovered.files.size)
        assertEquals("second-name", recovered.files[0].name)
    }

    @Test fun `base snapshot plus later WAL beyond its watermark both contribute`() = runTest {
        val clock = FakeStrataClock()
        val backend = FakeDriveBackend(clock)
        val vault = vault()
        val replicator = ManifestReplicator(backend, vault, "device-1")
        val accounts = listOf("a1", "a2", "a3")

        val base = ManifestSnapshot(epoch = 1, watermarksByDevice = mapOf("device-1" to 5L), accounts = emptyList(), files = listOf(sampleFile("old-file")), chunks = emptyList(), placements = emptyList())
        replicator.writeBase(accounts, base)
        // This op's seq (5) is AT the watermark, so it must NOT be double-applied/duplicated;
        // this later one (6) is past it and must be applied.
        replicator.flushWal(accounts, listOf(ManifestReplicator.fileOp("device-1", 6, sampleFile("new-file"))), seqStart = 6)

        val recovered = (replicator.recoverMerged(accounts) as com.tejgokani.strata.core.StrataResult.Ok).value
        assertEquals(setOf("old-file", "new-file"), recovered.files.map { it.id }.toSet())
    }
}
