/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.deezer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeezerAudioProviderTest {

    @Test
    fun hasAccounts_returnsTrue() {
        assertTrue(DeezerAudioProvider.hasAccounts())
        assertTrue(DeezerAudioProvider.hasBackends())
    }

    @Test
    fun deezerCrypto_deriveKey_consistent() {
        val key = DeezerCrypto.deriveKey("1383987152")
        assertEquals(16, key.size)
        // Ensure same trackId always produces exact same key
        val key2 = DeezerCrypto.deriveKey("1383987152")
        assertTrue(key.contentEquals(key2))
    }

    @Test
    fun deezerCrypto_isEncryptedChunk_stride() {
        assertTrue(DeezerCrypto.isEncryptedChunk(0))
        assertTrue(!DeezerCrypto.isEncryptedChunk(1))
        assertTrue(!DeezerCrypto.isEncryptedChunk(2))
        assertTrue(DeezerCrypto.isEncryptedChunk(3))
        assertTrue(DeezerCrypto.isEncryptedChunk(6))
    }

    @Test
    fun deezerCrypto_decryptChunk_executesWithoutError() {
        val key = DeezerCrypto.deriveKey("1383987152")
        val sampleChunk = ByteArray(DeezerCrypto.CHUNK_SIZE) { (it % 256).toByte() }
        DeezerCrypto.decryptChunk(sampleChunk, sampleChunk.size, key)
        assertNotNull(sampleChunk)
        assertEquals(DeezerCrypto.CHUNK_SIZE, sampleChunk.size)
    }

    @Test
    fun proxyMode_configuration() {
        DeezerAudioProvider.setProxyMode(moe.rukamori.archivetune.constants.DeezerProxyMode.UK1)
        assertEquals(moe.rukamori.archivetune.constants.DeezerProxyMode.UK1, DeezerAudioProvider.proxyMode)
        DeezerAudioProvider.setProxyMode(moe.rukamori.archivetune.constants.DeezerProxyMode.AUTO)
        assertEquals(moe.rukamori.archivetune.constants.DeezerProxyMode.AUTO, DeezerAudioProvider.proxyMode)
    }
}
