// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.vault

import com.bigfortravel.bigmfa.crypto.VaultHmac
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

class VaultIntegrityCheckerTest {

    private fun randomKey(): ByteArray {
        val key = ByteArray(32)
        SecureRandom().nextBytes(key)
        return key
    }

    private fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }

    @Test
    fun `coffre non modifie est reconnu valide`() {
        val key = randomKey()
        val data = "contenu du coffre".toByteArray()
        val mac = VaultHmac.sign(data, key)
        val macHex = bytesToHex(mac)

        val result = VaultIntegrityChecker.verify(data, key, macHex)
        assertTrue(result is VaultIntegrityChecker.IntegrityResult.Valid)
    }

    @Test
    fun `coffre dont les donnees ont ete modifiees est detecte corrompu`() {
        val key = randomKey()
        val originalData = "contenu original".toByteArray()
        val mac = VaultHmac.sign(originalData, key)
        val macHex = bytesToHex(mac)

        val tamperedData = "contenu modifie !".toByteArray()
        val result = VaultIntegrityChecker.verify(tamperedData, key, macHex)
        assertTrue(result is VaultIntegrityChecker.IntegrityResult.Corrupted)
    }

    @Test
    fun `mac tronque ou modifie est detecte corrompu`() {
        val key = randomKey()
        val data = "contenu du coffre".toByteArray()
        val mac = VaultHmac.sign(data, key)
        val macHex = bytesToHex(mac)

        // On modifie un seul caractère hexadécimal du mac stocké
        val tamperedMacHex = "f" + macHex.substring(1)
        val result = VaultIntegrityChecker.verify(data, key, tamperedMacHex)
        assertTrue(result is VaultIntegrityChecker.IntegrityResult.Corrupted)
    }

    @Test
    fun `verification avec une mauvaise cle est detectee corrompue`() {
        val data = "contenu du coffre".toByteArray()
        val mac = VaultHmac.sign(data, randomKey())
        val macHex = bytesToHex(mac)

        val result = VaultIntegrityChecker.verify(data, randomKey(), macHex)
        assertTrue(result is VaultIntegrityChecker.IntegrityResult.Corrupted)
    }
}