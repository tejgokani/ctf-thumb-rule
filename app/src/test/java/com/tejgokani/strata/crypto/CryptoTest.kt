package com.tejgokani.strata.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class ChunkCipherTest {
    @Test fun `round trip preserves plaintext exactly`() {
        val key = ChunkCipher.generateKey()
        val plaintext = "the quick brown fox".toByteArray()
        val blob = ChunkCipher.encrypt(key, plaintext)
        assertArrayEquals(plaintext, ChunkCipher.decrypt(key, blob))
    }

    @Test fun `wrong key fails authentication rather than returning garbage`() {
        val key = ChunkCipher.generateKey()
        val wrongKey = ChunkCipher.generateKey()
        val blob = ChunkCipher.encrypt(key, "secret".toByteArray())
        try {
            ChunkCipher.decrypt(wrongKey, blob)
            org.junit.Assert.fail("expected AuthenticationFailedException")
        } catch (e: ChunkCipher.AuthenticationFailedException) { /* expected */ }
    }

    @Test fun `tampering with ciphertext is detected`() {
        val key = ChunkCipher.generateKey()
        val blob = ChunkCipher.encrypt(key, "important data".toByteArray())
        val tampered = blob.copyOf()
        tampered[tampered.size - 1] = (tampered[tampered.size - 1] + 1).toByte()
        try {
            ChunkCipher.decrypt(key, tampered)
            org.junit.Assert.fail("expected AuthenticationFailedException")
        } catch (e: ChunkCipher.AuthenticationFailedException) { /* expected */ }
    }

    @Test fun `every encryption uses a fresh nonce even for identical plaintext and repeated calls`() {
        val key = ChunkCipher.generateKey()
        val plaintext = "same bytes every time".toByteArray()
        val nonces = (0 until 200).map { ChunkCipher.encrypt(key, plaintext).copyOfRange(0, ChunkCipher.NONCE_LENGTH_BYTES) }
        val distinct = nonces.map { Base64.getEncoder().encodeToString(it) }.toSet()
        assertEquals("every nonce must be unique across repeated encryptions", 200, distinct.size)
    }

    @Test fun `empty plaintext round trips`() {
        val key = ChunkCipher.generateKey()
        val blob = ChunkCipher.encrypt(key, ByteArray(0))
        assertArrayEquals(ByteArray(0), ChunkCipher.decrypt(key, blob))
    }
}

class KdfTest {
    @Test fun `same passphrase and salt derive the same key`() {
        val salt = Kdf.randomSalt()
        val k1 = Kdf.deriveMasterKey("correct horse battery staple".toCharArray(), salt, iterations = 10_000)
        val k2 = Kdf.deriveMasterKey("correct horse battery staple".toCharArray(), salt, iterations = 10_000)
        assertArrayEquals(k1, k2)
    }

    @Test fun `different salts derive different keys from the same passphrase`() {
        val k1 = Kdf.deriveMasterKey("same passphrase".toCharArray(), Kdf.randomSalt(), iterations = 10_000)
        val k2 = Kdf.deriveMasterKey("same passphrase".toCharArray(), Kdf.randomSalt(), iterations = 10_000)
        assertFalse(k1.contentEquals(k2))
    }

    @Test fun `hkdf domain separation produces distinct subkeys from the same input`() {
        val ikm = ChunkCipher.generateKey()
        val a = Kdf.hkdfSha256(ikm, Kdf.INFO_DEK_WRAP)
        val b = Kdf.hkdfSha256(ikm, Kdf.INFO_APP_PROPS)
        val c = Kdf.hkdfSha256(ikm, Kdf.INFO_MANIFEST)
        assertNotEquals(Base64.getEncoder().encodeToString(a), Base64.getEncoder().encodeToString(b))
        assertNotEquals(Base64.getEncoder().encodeToString(b), Base64.getEncoder().encodeToString(c))
        assertNotEquals(Base64.getEncoder().encodeToString(a), Base64.getEncoder().encodeToString(c))
    }
}

class RecoveryKeyTest {
    @Test fun `exported key round trips through parse`() {
        val key = ChunkCipher.generateKey()
        val exported = RecoveryKey.export(key)
        val result = RecoveryKey.parse(exported)
        assertTrue(result.checksumValid)
        assertArrayEquals(key, result.masterKey)
    }

    @Test fun `a single mistyped character is caught by the checksum, not silently accepted`() {
        val key = ChunkCipher.generateKey()
        val exported = RecoveryKey.export(key)
        val corrupted = "A" + exported.substring(1) // flip the first character
        val result = RecoveryKey.parse(corrupted)
        assertFalse(result.checksumValid)
        assertNull(result.masterKey)
    }
}

class PropsCodecTest {
    @Test fun `chunk metadata survives the Drive appProperties round trip`() {
        val vault = KeyVault.fromPassphrase("test passphrase".toCharArray(), Kdf.randomSalt(), iterations = 10_000)
        val meta = ChunkMetadata(
            poolId = "pool-1", fileUuid = "file-1", chunkIndex = 3, chunkCount = 10,
            plainSize = 16_777_216L, plaintextSha256Hex = "ab".repeat(32),
            ciphertextMd5Hex = "cd".repeat(16), wrappedDekB64 = Base64.getEncoder().encodeToString(ByteArray(60) { it.toByte() }),
            nonceB64 = Base64.getEncoder().encodeToString(ByteArray(12) { 7 }), manifestEpoch = 5L,
            fileWholeSha256Hex = "ef".repeat(32),
        )
        val properties = PropsCodec.encodeForDrive(vault, meta)
        assertTrue("Drive appProperties key limit", properties.size <= 30)
        val decoded = PropsCodec.decodeFromDrive(vault, properties)
        assertEquals(meta, decoded)
    }

    @Test fun `a different vault cannot decrypt another vault's metadata`() {
        val vaultA = KeyVault.fromPassphrase("alice".toCharArray(), Kdf.randomSalt(), iterations = 10_000)
        val vaultB = KeyVault.fromPassphrase("bob".toCharArray(), Kdf.randomSalt(), iterations = 10_000)
        val meta = ChunkMetadata("pool", "file", 0, 1, 100, "x".repeat(64), "y".repeat(32), "z", "n", 0, "w".repeat(64))
        val properties = PropsCodec.encodeForDrive(vaultA, meta)
        assertNull(PropsCodec.decodeFromDrive(vaultB, properties))
    }

    @Test fun `missing or incomplete properties decode to null rather than throwing`() {
        val vault = KeyVault.fromPassphrase("x".toCharArray(), Kdf.randomSalt(), iterations = 10_000)
        assertNull(PropsCodec.decodeFromDrive(vault, null))
        assertNull(PropsCodec.decodeFromDrive(vault, emptyMap()))
        assertNull(PropsCodec.decodeFromDrive(vault, mapOf("dn" to "2", "d0" to "onlyone")))
    }
}
