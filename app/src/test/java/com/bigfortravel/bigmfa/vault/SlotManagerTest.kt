// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.vault

import com.bigfortravel.bigmfa.vault.model.VaultFile
import com.bigfortravel.bigmfa.vault.model.VaultSlot
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

class SlotManagerTest {

    private lateinit var tempFile: File
    private lateinit var repository: VaultRepository
    private lateinit var slotManager: SlotManager

    @Before
    fun setUp() {
        tempFile = File.createTempFile("slot-manager-test", ".json")
        repository = VaultRepository(tempFile)
        slotManager = SlotManager(repository)

        // Coffre initial avec juste un slot mot de passe -- comme un vrai coffre existant.
        val vault = VaultFile(
            createdAt = "2026-08-20T10:00:00Z",
            modifiedAt = "2026-08-20T10:00:00Z",
            slots = listOf(
                VaultSlot.PasswordSlot(
                    uuid = "slot-password",
                    wrappedKey = "aabbcc",
                    nonce = "112233445566778899001122",
                    tag = "aabbccddeeff00112233445566778899",
                    salt = "ff".repeat(32),
                    memoryKiB = 32768, iterations = 3, parallelism = 2,
                ),
            ),
            content = "deadbeef",
            contentNonce = "112233445566778899001122",
            contentTag = "aabbccddeeff00112233445566778899",
            vaultHmac = "00".repeat(32),
        )
        repository.write(vault)
    }

    @After
    fun tearDown() {
        tempFile.delete()
    }

    /** Cipher AES logiciel ordinaire -- simule un cipher biométrique déjà
     *  authentifié, sans avoir besoin d'un vrai appareil pour ce test. */
    private fun softwareCipherForTest(): Cipher {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return cipher
    }

    private fun randomKey(): ByteArray {
        val key = ByteArray(32)
        SecureRandom().nextBytes(key)
        return key
    }

    @Test
    fun `nouveau coffre n'a pas de slot biometrique au depart`() {
        assertFalse(slotManager.hasBiometricSlot())
        assertTrue(slotManager.hasPasswordSlot())
    }

    @Test
    fun `addBiometricSlot ajoute bien un slot biometrique, sans toucher au slot mot de passe`() {
        slotManager.addBiometricSlot(randomKey(), randomKey(), softwareCipherForTest(), "test-alias")

        assertTrue(slotManager.hasBiometricSlot())
        assertTrue(slotManager.hasPasswordSlot())

        val vault = repository.read()
        assertEquals(2, vault.slots.size)
    }

    @Test
    fun `removeBiometricSlot retire le slot biometrique, jamais le mot de passe`() {
        slotManager.addBiometricSlot(randomKey(), randomKey(), softwareCipherForTest(), "test-alias")
        assertTrue(slotManager.hasBiometricSlot())

        slotManager.removeBiometricSlot(randomKey())

        assertFalse(slotManager.hasBiometricSlot())
        assertTrue(slotManager.hasPasswordSlot())
    }

    @Test
    fun `ajouter un slot biometrique recalcule bien vault_hmac`() {
        val vaultBefore = repository.read()
        val hmacBefore = vaultBefore.vaultHmac

        slotManager.addBiometricSlot(randomKey(), randomKey(), softwareCipherForTest(), "test-alias")

        val vaultAfter = repository.read()
        assertFalse(vaultAfter.vaultHmac == hmacBefore)
    }

    @Test
    fun `ajouter un deuxieme slot biometrique remplace le premier, n'accumule pas`() {
        slotManager.addBiometricSlot(randomKey(), randomKey(), softwareCipherForTest(), "premier-alias")
        slotManager.addBiometricSlot(randomKey(), randomKey(), softwareCipherForTest(), "second-alias")

        val vault = repository.read()
        val biometricSlots = vault.slots.filterIsInstance<VaultSlot.BiometricSlot>()
        assertEquals(1, biometricSlots.size)
        assertEquals("second-alias", biometricSlots[0].keystoreAlias)
    }
}