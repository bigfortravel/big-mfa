// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.crypto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

class VaultHmacTest {

    private fun randomKey(): ByteArray {
        val key = ByteArray(32)
        SecureRandom().nextBytes(key)
        return key
    }

    @Test
    fun `signer deux fois les memes donnees avec la meme cle donne la meme signature`() {
        val key = randomKey()
        val data = "contenu du coffre".toByteArray()
        val mac1 = VaultHmac.sign(data, key)
        val mac2 = VaultHmac.sign(data, key)
        assertArrayEquals(mac1, mac2)
    }

    @Test
    fun `verify reussit quand les donnees et la signature correspondent`() {
        val key = randomKey()
        val data = "contenu du coffre".toByteArray()
        val mac = VaultHmac.sign(data, key)
        assertTrue(VaultHmac.verify(data, key, mac))
    }

    @Test
    fun `verify echoue si les donnees ont ete modifiees apres signature`() {
        val key = randomKey()
        val data = "contenu original".toByteArray()
        val mac = VaultHmac.sign(data, key)

        val tamperedData = "contenu modifie!".toByteArray()
        assertFalse(VaultHmac.verify(tamperedData, key, mac))
    }

    @Test
    fun `verify echoue si la cle utilisee est differente`() {
        val data = "contenu du coffre".toByteArray()
        val mac = VaultHmac.sign(data, randomKey())
        assertFalse(VaultHmac.verify(data, randomKey(), mac))
    }

    @Test
    fun `la signature HMAC-SHA256 fait bien 256 bits (32 octets)`() {
        val mac = VaultHmac.sign("test".toByteArray(), randomKey())
        assertEquals(32, mac.size)
    }
}