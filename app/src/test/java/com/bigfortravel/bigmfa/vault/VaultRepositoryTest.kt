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

class VaultRepositoryTest {

    private lateinit var tempFile: File
    private lateinit var repository: VaultRepository

    @Before
    fun setUp() {
        tempFile = File.createTempFile("vault-test", ".json")
        repository = VaultRepository(tempFile)
    }

    @After
    fun tearDown() {
        tempFile.delete()
    }

    private fun sampleVault(): VaultFile {
        val passwordSlot = VaultSlot.PasswordSlot(
            uuid = "slot-1",
            wrappedKey = "aabbcc",
            nonce = "112233445566778899001122",
            tag = "aabbccddeeff00112233445566778899",
            salt = "ff".repeat(32),
            memoryKiB = 32768,
            iterations = 3,
            parallelism = 2,
        )
        return VaultFile(
            createdAt = "2026-08-20T10:00:00Z",
            modifiedAt = "2026-08-20T10:00:00Z",
            slots = listOf(passwordSlot),
            content = "deadbeef",
            contentNonce = "112233445566778899001122",
            contentTag = "aabbccddeeff00112233445566778899",
            vaultHmac = "0011223344556677889900112233445566778899001122334455667788990011",
        )
    }

    @Test
    fun `un coffre ecrit puis relu redonne exactement les memes donnees`() {
        val original = sampleVault()
        repository.write(original)
        val reloaded = repository.read()

        assertEquals(original.format, reloaded.format)
        assertEquals(original.version, reloaded.version)
        assertEquals(original.content, reloaded.content)
        assertEquals(original.vaultHmac, reloaded.vaultHmac)
        assertEquals(1, reloaded.slots.size)
    }

    @Test
    fun `un slot mot de passe conserve tous ses parametres apres ecriture-lecture`() {
        repository.write(sampleVault())
        val reloaded = repository.read()

        val slot = reloaded.slots[0] as VaultSlot.PasswordSlot
        assertEquals(32768, slot.memoryKiB)
        assertEquals(3, slot.iterations)
        assertEquals(2, slot.parallelism)
        assertEquals("ff".repeat(32), slot.salt)
    }

    @Test
    fun `un coffre avec slot biometrique en plus du mot de passe fonctionne`() {
        val original = sampleVault()
        val withBiometric = original.copy(
            slots = original.slots + VaultSlot.BiometricSlot(
                uuid = "slot-2",
                wrappedKey = "112233",
                nonce = "aabbccddeeff001122334455",
                tag = "112233445566778899aabbccddeeff0",
                keystoreAlias = "big-mfa-biometric-key-v1",
            ),
        )
        repository.write(withBiometric)
        val reloaded = repository.read()

        assertEquals(2, reloaded.slots.size)
        assertTrue(reloaded.slots[1] is VaultSlot.BiometricSlot)
    }

    @Test
    fun `exists renvoie false avant toute ecriture, true apres`() {
        val emptyFile = File.createTempFile("vault-empty", ".json")
        emptyFile.delete() // on veut un fichier qui n'existe pas encore
        val emptyRepo = VaultRepository(emptyFile)

        assertFalse(emptyRepo.exists())
        emptyRepo.write(sampleVault())
        assertTrue(emptyRepo.exists())

        emptyFile.delete()
    }

    @Test
    fun `delete supprime bien le fichier`() {
        repository.write(sampleVault())
        assertTrue(repository.exists())

        val deleted = repository.delete()
        assertTrue(deleted)
        assertFalse(repository.exists())
    }
}