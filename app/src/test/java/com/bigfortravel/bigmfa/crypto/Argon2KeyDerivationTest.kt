// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class Argon2KeyDerivationTest {

    @Test
    fun `meme mot de passe et meme sel donnent la meme cle`() {
        val salt = Argon2KeyDerivation.generateSalt()
        val key1 = Argon2KeyDerivation.derive("MonMotDePasse123!".toCharArray(), salt, Argon2KeyDerivation.MemoryTier.STANDARD)
        val key2 = Argon2KeyDerivation.derive("MonMotDePasse123!".toCharArray(), salt, Argon2KeyDerivation.MemoryTier.STANDARD)
        assertArrayEquals(key1, key2)
    }

    @Test
    fun `mots de passe differents donnent des cles differentes`() {
        val salt = Argon2KeyDerivation.generateSalt()
        val key1 = Argon2KeyDerivation.derive("MotDePasseA123!".toCharArray(), salt, Argon2KeyDerivation.MemoryTier.STANDARD)
        val key2 = Argon2KeyDerivation.derive("MotDePasseB123!".toCharArray(), salt, Argon2KeyDerivation.MemoryTier.STANDARD)
        assertFalse(key1.contentEquals(key2))
    }

    @Test
    fun `detection de palier selon la RAM disponible`() {
        assertEquals(Argon2KeyDerivation.MemoryTier.CONFORTABLE, Argon2KeyDerivation.detectTierForThisDevice(6L * 1024 * 1024 * 1024))
        assertEquals(Argon2KeyDerivation.MemoryTier.STANDARD, Argon2KeyDerivation.detectTierForThisDevice(3L * 1024 * 1024 * 1024))
        assertEquals(Argon2KeyDerivation.MemoryTier.CONTRAINT, Argon2KeyDerivation.detectTierForThisDevice(1L * 1024 * 1024 * 1024))
    }

    @Test
    fun `la cle derivee fait bien 256 bits`() {
        val salt = Argon2KeyDerivation.generateSalt()
        val key = Argon2KeyDerivation.derive("MotDePasse123!".toCharArray(), salt, Argon2KeyDerivation.MemoryTier.CONTRAINT)
        assertEquals(32, key.size)
    }
}