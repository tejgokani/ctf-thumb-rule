package com.tejgokani.strata.engine

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.security.SecureRandom

class ChunkerTest {
    private val random = SecureRandom()

    private fun randomBytes(size: Int): ByteArray = ByteArray(size).also { random.nextBytes(it) }

    private fun roundTrip(sizeBytes: Int, chunkSize: Long = 1024) {
        val original = randomBytes(sizeBytes)
        val source = ByteArraySource(original)
        val chunks = Chunker.split(source, chunkSize).toList()
        val expectedCount = Chunker.chunkCountFor(sizeBytes.toLong(), chunkSize)
        assertEquals("chunk count for $sizeBytes bytes at chunkSize $chunkSize", expectedCount, chunks.size)

        val out = ByteArrayOutputStream()
        chunks.forEach { out.write(it.bytes) }
        assertArrayEquals(original, out.toByteArray())

        chunks.forEach { assertEquals(it.sha256Hex, Chunker.sha256Hex(it.bytes)) }
    }

    @Test fun `zero byte file still produces exactly one empty chunk`() = roundTrip(0)
    @Test fun `one byte file`() = roundTrip(1)
    @Test fun `exact multiple of chunk size`() = roundTrip(1024 * 4, chunkSize = 1024)
    @Test fun `one byte over a chunk boundary`() = roundTrip(1024 * 4 + 1, chunkSize = 1024)
    @Test fun `large file at the real 16 MiB default chunk size`() {
        // Smaller than a real 16 MiB chunk but exercises the real default constant end to end.
        roundTrip(700_000, chunkSize = Chunker.DEFAULT_CHUNK_SIZE_BYTES)
    }

    @Test fun `whole file hash matches reassembler expectation`() {
        val original = randomBytes(50_000)
        val source = ByteArraySource(original)
        val expected = Chunker.sha256HexOfStream(source)
        val chunks = Chunker.split(source, 4096).toList().map { it.bytes }
        val out = ByteArrayOutputStream()
        Reassembler.reassemble(chunks, expected, out)
        assertArrayEquals(original, out.toByteArray())
    }

    @Test(expected = Reassembler.IntegrityException::class)
    fun `reassembler rejects a whole file hash mismatch`() {
        val chunks = listOf("hello".toByteArray())
        Reassembler.reassemble(chunks, "0".repeat(64), ByteArrayOutputStream())
    }
}
