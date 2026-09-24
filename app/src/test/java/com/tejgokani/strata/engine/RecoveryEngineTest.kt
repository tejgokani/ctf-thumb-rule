package com.tejgokani.strata.engine

import com.tejgokani.strata.core.StrataResult
import com.tejgokani.strata.fakes.TestHarness
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.security.SecureRandom

/**
 * The "SIMULATE DISASTER RECOVERY" drill (plan §10/§11): wipe the local index entirely and
 * rebuild it from nothing but what is sitting in the connected Drive accounts — encrypted
 * appProperties on every chunk, plus whatever manifest WAL/base replicas survive. An untested
 * recovery path is a broken recovery path, so this is the one test in the whole suite that most
 * directly stands in for "does this app actually protect the user's data."
 */
class RecoveryEngineTest {
    private val random = SecureRandom()
    private fun randomBytes(n: Int) = ByteArray(n).also { random.nextBytes(it) }

    @Test fun `wiping the local index entirely and recovering from Drive alone reproduces every file byte for byte`() = runTest {
        val h = TestHarness(chunkSizeBytes = 20_000)
        h.addAccount("acct-1")
        h.addAccount("acct-2")
        h.addAccount("acct-3")

        val fileAContent = randomBytes(20_000 * 3)
        val fileBContent = randomBytes(5_000)
        val fileA = (h.coordinator.uploadFile(TransferCoordinator.UploadRequest("a.bin", "application/octet-stream", ByteArraySource(fileAContent))) as StrataResult.Ok).value
        val fileB = (h.coordinator.uploadFile(TransferCoordinator.UploadRequest("b.bin", "application/octet-stream", ByteArraySource(fileBContent), mirror = true)) as StrataResult.Ok).value

        // --- Total local wipe: brand new, empty repositories, as if the app were reinstalled ---
        val freshFiles = InMemoryFileRepository()
        val freshChunks = InMemoryChunkRepository()
        val freshPlacements = InMemoryPlacementRepository()

        val recoveryEngine = RecoveryEngine(h.backend, h.vault, h.manifestReplicator, h.poolId)
        val recovered = (recoveryEngine.recover(h.allAccountIds()) as StrataResult.Ok).value

        assertEquals(setOf(fileA.id, fileB.id), recovered.files.map { it.id }.toSet())
        assertTrue("recovery should not need to fall back to adoption when the manifest WAL survived", recovered.adoptedFileIds.isEmpty())

        for (f in recovered.files) freshFiles.upsert(f)
        for (c in recovered.chunks) freshChunks.upsert(c)
        for (p in recovered.placements) freshPlacements.upsert(p)

        // Rebuild a coordinator on the RECOVERED repositories only, and confirm both files still read back correctly.
        val recoveredCoordinator = TransferCoordinator(
            h.backend, h.vault, h.ledger, h.accounts, freshFiles, freshChunks, freshPlacements,
            h.reservations, h.manifestReplicator, h.seqAllocator, h.clock, h.deviceId, h.poolId,
        )

        val outA = ByteArrayOutputStream()
        assertTrue(recoveredCoordinator.downloadFile(fileA.id, outA) is StrataResult.Ok)
        assertArrayEquals(fileAContent, outA.toByteArray())

        val outB = ByteArrayOutputStream()
        assertTrue(recoveredCoordinator.downloadFile(fileB.id, outB) is StrataResult.Ok)
        assertArrayEquals(fileBContent, outB.toByteArray())
    }

    @Test fun `recovery still succeeds purely from chunk appProperties even if every manifest replica is wiped`() = runTest {
        val h = TestHarness(chunkSizeBytes = 50_000)
        h.addAccount("acct-1")
        h.addAccount("acct-2")

        val content = randomBytes(50_000 * 2)
        val file = (h.coordinator.uploadFile(TransferCoordinator.UploadRequest("f.bin", "application/octet-stream", ByteArraySource(content))) as StrataResult.Ok).value

        // Simulate the user wiping this app's hidden appDataFolder data from Drive settings —
        // every WAL segment and base snapshot is gone, but the chunk FILES themselves (and their
        // appProperties) remain, because those live in the ordinary drive.file space.
        for (accountId in h.allAccountIds()) {
            for (entry in (h.backend.listAppDataFiles(accountId) as StrataResult.Ok).value) {
                h.backend.deleteAppDataFile(accountId, entry.name)
            }
        }

        val recoveryEngine = RecoveryEngine(h.backend, h.vault, h.manifestReplicator, h.poolId)
        val recovered = (recoveryEngine.recover(h.allAccountIds()) as StrataResult.Ok).value

        assertEquals(1, recovered.files.size)
        assertTrue("with no manifest replica surviving, the file must be recovered via appProperties adoption", recovered.adoptedFileIds.contains(file.id))
        assertEquals(2, recovered.chunks.size)
        assertEquals(2, recovered.placements.size)

        val freshFiles = InMemoryFileRepository().also { repo -> recovered.files.forEach { runBlockingLocal { repo.upsert(it) } } }
        val freshChunks = InMemoryChunkRepository().also { repo -> recovered.chunks.forEach { runBlockingLocal { repo.upsert(it) } } }
        val freshPlacements = InMemoryPlacementRepository().also { repo -> recovered.placements.forEach { runBlockingLocal { repo.upsert(it) } } }

        val recoveredCoordinator = TransferCoordinator(
            h.backend, h.vault, h.ledger, h.accounts, freshFiles, freshChunks, freshPlacements,
            h.reservations, h.manifestReplicator, h.seqAllocator, h.clock, h.deviceId, h.poolId,
        )
        val out = ByteArrayOutputStream()
        assertTrue(recoveredCoordinator.downloadFile(file.id, out) is StrataResult.Ok)
        assertArrayEquals(content, out.toByteArray())
    }

    // InMemory*Repository methods are suspend; `also{}` above needs a tiny bridge since it isn't itself suspend.
    private fun runBlockingLocal(block: suspend () -> Unit) = kotlinx.coroutines.runBlocking { block() }
}
