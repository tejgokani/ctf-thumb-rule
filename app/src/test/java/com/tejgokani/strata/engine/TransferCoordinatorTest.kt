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

class TransferCoordinatorTest {
    private val random = SecureRandom()
    private fun randomBytes(n: Int) = ByteArray(n).also { random.nextBytes(it) }

    @Test fun `a small file uploads across a single account and reads back byte identical`() = runTest {
        val h = TestHarness()
        h.addAccount("acct-1")
        val original = randomBytes(10_000)

        val uploaded = h.coordinator.uploadFile(TransferCoordinator.UploadRequest("photo.jpg", "image/jpeg", ByteArraySource(original)))
        val fileRecord = (uploaded as StrataResult.Ok).value
        assertEquals(FileStatus.COMMITTED, fileRecord.status)

        val out = ByteArrayOutputStream()
        val downloadResult = h.coordinator.downloadFile(fileRecord.id, out)
        assertTrue(downloadResult is StrataResult.Ok)
        assertArrayEquals(original, out.toByteArray())
    }

    @Test fun `a file larger than one chunk is striped across accounts proportional to free space`() = runTest {
        val h = TestHarness(chunkSizeBytes = 100_000)
        // Realistic Drive-sized quotas (the 1GB headroom floor in AccountRecord.headroomBytes
        // dominates any account under ~12.5GB, so tests must use realistic magnitudes rather
        // than toy byte counts for the proportionality math to mean anything).
        h.addAccount("big", limitBytes = 15_000_000_000L, usageBytes = 0L)             // ~13.8GB effective free
        h.addAccount("small", limitBytes = 15_000_000_000L, usageBytes = 13_000_000_000L) // ~0.8GB effective free

        val original = randomBytes(100_000 * 8) // 8 chunks
        val uploaded = h.coordinator.uploadFile(TransferCoordinator.UploadRequest("big.bin", "application/octet-stream", ByteArraySource(original)))
        val fileRecord = (uploaded as StrataResult.Ok).value

        val bigCount = h.placements.forAccount("big").size
        val smallCount = h.placements.forAccount("small").size
        assertEquals(8, bigCount + smallCount)
        // "big" has ~17x "small"'s effective free space and must carry the large majority of chunks.
        assertTrue("expected big ($bigCount) > small ($smallCount)", bigCount > smallCount)

        val out = ByteArrayOutputStream()
        h.coordinator.downloadFile(fileRecord.id, out)
        assertArrayEquals(original, out.toByteArray())
    }

    @Test fun `server side corruption is caught by verify-after-write and the chunk is re-placed rather than trusted`() = runTest {
        val h = TestHarness()
        h.addAccount("acct-1")
        h.backend.corruptNextUpload("acct-1") // the very first attempt will silently store wrong bytes
        val original = randomBytes(5_000)

        val uploaded = h.coordinator.uploadFile(TransferCoordinator.UploadRequest("f.bin", "application/octet-stream", ByteArraySource(original)))
        val fileRecord = (uploaded as StrataResult.Ok).value
        assertEquals(FileStatus.COMMITTED, fileRecord.status)

        // The chunk must have gone through at least two upload attempts (one caught corruption, one succeeded).
        assertTrue(h.backend.uploadAttempts("acct-1") >= 2)

        val out = ByteArrayOutputStream()
        h.coordinator.downloadFile(fileRecord.id, out)
        assertArrayEquals("corruption must never reach the user as if it were a successful upload", original, out.toByteArray())
    }

    @Test fun `an interrupted upload resumes from the committed offset without duplicating bytes or opening a new session`() = runTest {
        val h = TestHarness(chunkSizeBytes = 100_000) // one chunk, big enough to interrupt partway through
        h.addAccount("acct-1")
        h.backend.interruptNextUploadAt("acct-1", atByteOffset = 40_000)
        val original = randomBytes(90_000)

        val uploaded = h.coordinator.uploadFile(TransferCoordinator.UploadRequest("f.bin", "application/octet-stream", ByteArraySource(original)))
        val fileRecord = (uploaded as StrataResult.Ok).value
        assertEquals(FileStatus.COMMITTED, fileRecord.status)

        assertEquals("a genuine resume reuses the same session rather than starting a fresh one", 1, h.backend.sessionsCreated("acct-1"))
        assertEquals(2, h.backend.uploadAttempts("acct-1"))

        val out = ByteArrayOutputStream()
        h.coordinator.downloadFile(fileRecord.id, out)
        assertArrayEquals(original, out.toByteArray())
    }

    @Test fun `mirrored files survive the loss of their primary account`() = runTest {
        val h = TestHarness()
        h.addAccount("primary")
        h.addAccount("mirror")
        val original = randomBytes(5_000)

        val uploaded = h.coordinator.uploadFile(
            TransferCoordinator.UploadRequest("f.bin", "application/octet-stream", ByteArraySource(original), mirror = true)
        )
        val fileRecord = (uploaded as StrataResult.Ok).value
        assertTrue(fileRecord.mirrored)

        // Total node loss on the primary — the primary's driveFileId is gone from Drive's perspective.
        val primaryPlacement = h.placements.forAccount("primary").first()
        h.backend.destroyAccount("primary")

        val out = ByteArrayOutputStream()
        val result = h.coordinator.downloadFile(fileRecord.id, out)
        assertTrue("mirror must make the file readable even though the primary account is gone", result is StrataResult.Ok)
        assertArrayEquals(original, out.toByteArray())
    }

    @Test fun `an unmirrored file whose only account is lost is marked DAMAGED rather than silently missing`() = runTest {
        val h = TestHarness()
        h.addAccount("only")
        val original = randomBytes(5_000)
        val uploaded = h.coordinator.uploadFile(TransferCoordinator.UploadRequest("f.bin", "application/octet-stream", ByteArraySource(original)))
        val fileRecord = (uploaded as StrataResult.Ok).value

        h.backend.destroyAccount("only")

        val out = ByteArrayOutputStream()
        val result = h.coordinator.downloadFile(fileRecord.id, out)
        assertTrue(result is StrataResult.Err)
        val reloaded = h.files.get(fileRecord.id)!!
        assertEquals(FileStatus.DAMAGED, reloaded.status)
    }

    @Test fun `an upload that cannot fit anywhere is refused up front, and a later smaller file is unaffected`() = runTest {
        val h = TestHarness(chunkSizeBytes = 100_000)
        // ~200MB of real usable space above the fixed 1GB headroom floor.
        h.addAccount("acct", limitBytes = 1_200_000_000L)

        val tooBig = randomBytes(100_000 * 2_100) // ~210MB — more than the ~200MB available
        val bigResult = h.coordinator.uploadFile(TransferCoordinator.UploadRequest("big.bin", "application/octet-stream", ByteArraySource(tooBig)))
        assertTrue(bigResult is StrataResult.Err)
        val bigRecord = h.files.all().first { it.name == "big.bin" }
        assertEquals(FileStatus.INCOMPLETE_NO_SPACE, bigRecord.status)

        val small = randomBytes(100_000)
        val smallResult = h.coordinator.uploadFile(TransferCoordinator.UploadRequest("small.bin", "application/octet-stream", ByteArraySource(small)))
        assertTrue("the refused oversized attempt must not have leaked capacity or left the account unusable", smallResult is StrataResult.Ok)
    }
}
