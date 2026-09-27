// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.security.SecureRandom

class HkdfDerivationTest {

    private fun randomMasterKey(): ByteArray {
        val key = ByteArray(32)
        SecureRandom().nextBytes(key)
        return key
    }

    @Test
    fun `meme MK et meme info donnent la meme sous-cle`() {
        val mk = randomMasterKey()
        val key1 = HkdfDerivation.expand(mk, "big-mfa-content-v1")
        val key2 = HkdfDerivation.expand(mk, "big-mfa-content-v1")
        assertArrayEquals(key1, key2)
    }

    @Test
    fun `meme MK mais info differents donnent des sous-cles differentes`() {
        val mk = randomMasterKey()
        val contentKey = HkdfDerivation.expand(mk, "big-mfa-content-v1")
        val hmacKey = HkdfDerivation.expand(mk, "big-mfa-integrity-v1")
        assertFalse(contentKey.contentEquals(hmacKey))
    }

    @Test
    fun `MK differentes donnent des sous-cles differentes, meme info identique`() {
        val key1 = HkdfDerivation.expand(randomMasterKey(), "big-mfa-content-v1")
        val key2 = HkdfDerivation.expand(randomMasterKey(), "big-mfa-content-v1")
        assertFalse(key1.contentEquals(key2))
    }

    @Test
    fun `la sous-cle produite fait bien 256 bits`() {
        val key = HkdfDerivation.expand(randomMasterKey(), "big-mfa-content-v1")
        assertEquals(32, key.size)
    }
}