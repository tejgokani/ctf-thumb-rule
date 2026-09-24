package com.tejgokani.strata.engine

import com.tejgokani.strata.core.StrataResult
import com.tejgokani.strata.crypto.ChunkMetadata
import com.tejgokani.strata.crypto.PropsCodec
import com.tejgokani.strata.fakes.TestHarness
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.Base64

class ScrubEngineTest {
    private val random = SecureRandom()
    private fun randomBytes(n: Int) = ByteArray(n).also { random.nextBytes(it) }

    private fun scrubEngine(h: TestHarness) = ScrubEngine(h.backend, h.vault, h.clock, h.poolId)

    @Test fun `a chunk trashed by the user in the Drive web UI is scheduled for a free untrash repair`() = runTest {
        val h = TestHarness()
        h.addAccount("acct-1")
        val original = randomBytes(2_000)
        val fileRecord = (h.coordinator.uploadFile(
            TransferCoordinator.UploadRequest("f.bin", "application/octet-stream", ByteArraySource(original))
        ) as StrataResult.Ok).value
        val placement = h.placements.forAccount("acct-1").first()
        h.backend.userTrashesFile("acct-1", placement.driveFileId!!)

        val report = scrubEngine(h).scrub(h.allAccountIds(), h.placements.all(), h.chunks.forFile(fileRecord.id), currentManifestEpoch = 0)
        assertTrue(report.actions.any { it is ScrubAction.Untrash && it.placementId == placement.id })
    }

    @Test fun `an orphan file with valid pool metadata is adopted rather than deleted`() = runTest {
        val h = TestHarness()
        h.addAccount("acct-1")
        val plaintext = randomBytes(1_000)
        val dek = h.vault.generateFileDek()
        val blob = h.vault.encryptChunk(dek, plaintext)
        val metadata = ChunkMetadata(
            poolId = h.poolId, fileUuid = "orphan-file-1", chunkIndex = 0, chunkCount = 1,
            plainSize = plaintext.size.toLong(), plaintextSha256Hex = Chunker.sha256Hex(plaintext),
            ciphertextMd5Hex = "unused", wrappedDekB64 = Base64.getEncoder().encodeToString(h.vault.wrapDek(dek)),
            nonceB64 = Base64.getEncoder().encodeToString(blob.copyOfRange(0, 12)), manifestEpoch = 0,
            fileWholeSha256Hex = Chunker.sha256Hex(plaintext),
        )
        val properties = PropsCodec.encodeForDrive(h.vault, metadata)
        var sessionUri: String? = null
        h.backend.uploadChunk("acct-1", "orphan.strata", "xx", blob, properties, null) { sessionUri = it }

        // No placement record exists for this file at all — it is a pure orphan from Drive's perspective.
        val report = scrubEngine(h).scrub(h.allAccountIds(), emptyList(), emptyList(), currentManifestEpoch = 0)
        val adopt = report.actions.filterIsInstance<ScrubAction.Adopt>().firstOrNull()
        assertTrue("expected an Adopt action, got ${report.actions}", adopt != null)
        assertEquals("orphan-file-1", adopt!!.metadata.fileUuid)
    }

    @Test fun `an orphan with undecodable properties is only ever a soft trash candidate, never adopted`() = runTest {
        val h = TestHarness()
        h.addAccount("acct-1")
        var sessionUri: String? = null
        h.backend.uploadChunk("acct-1", "junk.bin", "xx", randomBytes(500), mapOf("not" to "strata-metadata"), null) { sessionUri = it }

        val report = scrubEngine(h).scrub(h.allAccountIds(), emptyList(), emptyList(), currentManifestEpoch = 0)
        assertTrue(report.actions.any { it is ScrubAction.TrashOrphanCandidate })
        assertTrue(report.actions.none { it is ScrubAction.Adopt })
    }

    @Test fun `an account whose enumeration fails contributes zero evidence for deletion`() = runTest {
        val h = TestHarness()
        h.addAccount("acct-1")
        val original = randomBytes(2_000)
        h.coordinator.uploadFile(TransferCoordinator.UploadRequest("f.bin", "application/octet-stream", ByteArraySource(original)))
        val placement = h.placements.forAccount("acct-1").first()

        // Simulate the account becoming unreachable mid-scrub (network down, needs consent, etc.)
        // — its placements must never be classified as missing/damaged from an incomplete listing.
        h.backend.setUnreachable("acct-1", true)

        val chunks = h.chunks.forFile(placement.chunkId.let { cid -> h.chunks.get(cid)!!.fileId })
        val report = scrubEngine(h).scrub(h.allAccountIds(), h.placements.all(), chunks, currentManifestEpoch = 0)

        assertTrue(report.accountsWithIncompleteEnumeration.contains("acct-1"))
        assertTrue(
            "admissibility rule violated: an action was proposed against a placement with no admissible evidence",
            report.actions.none { it is ScrubAction.MarkDamaged || it is ScrubAction.Untrash }
        )
    }

    @Test fun `a mirrored chunk missing from its primary account is repaired from the surviving mirror`() = runTest {
        val h = TestHarness()
        h.addAccount("primary")
        h.addAccount("mirror")
        val original = randomBytes(2_000)
        val fileRecord = (h.coordinator.uploadFile(
            TransferCoordinator.UploadRequest("f.bin", "application/octet-stream", ByteArraySource(original), mirror = true)
        ) as StrataResult.Ok).value

        val primaryPlacement = h.placements.forAccount("primary").first()
        h.backend.permanentlyDeleteFile("primary", primaryPlacement.driveFileId!!)

        val report = scrubEngine(h).scrub(h.allAccountIds(), h.placements.all(), h.chunks.forFile(fileRecord.id), currentManifestEpoch = 0)
        assertTrue(report.actions.any { it is ScrubAction.RepairFromMirror })
    }
}
