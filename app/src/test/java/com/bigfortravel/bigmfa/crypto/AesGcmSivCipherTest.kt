// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.crypto

import org.bouncycastle.crypto.InvalidCipherTextException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.security.SecureRandom

class AesGcmSivCipherTest {

    private fun randomKey(): ByteArray {
        val key = ByteArray(32)
        SecureRandom().nextBytes(key)
        return key
    }

    @Test
    fun `chiffrer puis dechiffrer redonne le texte d'origine`() {
        val key = randomKey()
        val plaintext = "Big MFA - test de chiffrement".toByteArray()
        val encrypted = AesGcmSivCipher.encrypt(plaintext, key)
        val decrypted = AesGcmSivCipher.decrypt(encrypted, key)
        assertArrayEquals(plaintext, decrypted)
    }

    @Test(expected = InvalidCipherTextException::class)
    fun `un texte chiffre modifie fait echouer le dechiffrement`() {
        val key = randomKey()
        val plaintext = "Donnees sensibles".toByteArray()
        val encrypted = AesGcmSivCipher.encrypt(plaintext, key)

        val tamperedCiphertext = encrypted.ciphertext.copyOf()
        tamperedCiphertext[0] = (tamperedCiphertext[0] + 1).toByte()
        val tampered = encrypted.copy(ciphertext = tamperedCiphertext)

        AesGcmSivCipher.decrypt(tampered, key)
    }

    @Test(expected = InvalidCipherTextException::class)
    fun `un tag modifie fait echouer le dechiffrement`() {
        val key = randomKey()
        val plaintext = "Donnees sensibles".toByteArray()
        val encrypted = AesGcmSivCipher.encrypt(plaintext, key)

        val tamperedTag = encrypted.tag.copyOf()
        tamperedTag[0] = (tamperedTag[0] + 1).toByte()
        val tampered = encrypted.copy(tag = tamperedTag)

        AesGcmSivCipher.decrypt(tampered, key)
    }

    @Test
    fun `deux chiffrements du meme texte produisent des nonces differents`() {
        val key = randomKey()
        val plaintext = "Meme texte".toByteArray()
        val encrypted1 = AesGcmSivCipher.encrypt(plaintext, key)
        val encrypted2 = AesGcmSivCipher.encrypt(plaintext, key)
        assertFalse(encrypted1.nonce.contentEquals(encrypted2.nonce))
    }

    @Test
    fun `le nonce genere fait bien 96 bits (12 octets)`() {
        val nonce = AesGcmSivCipher.generateNonce()
        assertEquals(12, nonce.size)
    }

    @Test
    fun `le tag genere fait bien 128 bits (16 octets)`() {
        val key = randomKey()
        val encrypted = AesGcmSivCipher.encrypt("test".toByteArray(), key)
        assertEquals(16, encrypted.tag.size)
    }
}