// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.vault

import com.bigfortravel.bigmfa.crypto.AesGcmSivCipher
import com.bigfortravel.bigmfa.crypto.Argon2KeyDerivation
import com.bigfortravel.bigmfa.crypto.HkdfDerivation
import com.bigfortravel.bigmfa.crypto.VaultHmac
import com.bigfortravel.bigmfa.vault.model.VaultAccount
import com.bigfortravel.bigmfa.vault.model.VaultFile
import com.bigfortravel.bigmfa.vault.model.VaultSlot
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.security.SecureRandom

class VaultContentWriterTest {

    private lateinit var tempFile: File
    private lateinit var repository: VaultRepository
    private lateinit var writer: VaultContentWriter

    private val password = "MonMotDePasse123!"
    private val tier = Argon2KeyDerivation.MemoryTier.CONTRAINT
    private lateinit var contentKey: ByteArray
    private lateinit var hmacKey: ByteArray

    private fun bytesToHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    @Before
    fun setUp() {
        tempFile = File.createTempFile("content-writer-test", ".json")
        repository = VaultRepository(tempFile)
        writer = VaultContentWriter(repository)

        // Coffre initial valide, avec un slot mot de passe -- comme un vrai coffre créé.
        val salt = Argon2KeyDerivation.generateSalt()
        val derivedKey = Argon2KeyDerivation.derive(password.toCharArray(), salt, tier)
        val masterKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val wrappedMk = AesGcmSivCipher.encrypt(masterKey, derivedKey)
        contentKey = HkdfDerivation.expand(masterKey, "big-mfa-content-v1")
        hmacKey = HkdfDerivation.expand(masterKey, "big-mfa-integrity-v1")

        val emptyContent = AesGcmSivCipher.encrypt(JSONArray().toString().toByteArray(), contentKey)
        val passwordSlot = VaultSlot.PasswordSlot(
            uuid = "slot-1", wrappedKey = bytesToHex(wrappedMk.ciphertext),
            nonce = bytesToHex(wrappedMk.nonce), tag = bytesToHex(wrappedMk.tag),
            salt = bytesToHex(salt), memoryKiB = tier.memoryKiB,
            iterations = tier.iterations, parallelism = tier.parallelism,
        )
        val vaultWithoutHmac = VaultFile(
            createdAt = "2026-08-20T10:00:00Z", modifiedAt = "2026-08-20T10:00:00Z",
            slots = listOf(passwordSlot), content = bytesToHex(emptyContent.ciphertext),
            contentNonce = bytesToHex(emptyContent.nonce), contentTag = bytesToHex(emptyContent.tag),
            vaultHmac = "",
        )
        val mac = VaultHmac.sign(repository.canonicalBytesForHmac(vaultWithoutHmac), hmacKey)
        repository.write(vaultWithoutHmac.copy(vaultHmac = bytesToHex(mac)))
    }

    @After
    fun tearDown() {
        tempFile.delete()
    }

    private fun sampleAccount() = VaultAccount(
        id = "acc-1", type = "totp", name = "GitHub", issuer = "GitHub Inc.",
        secret = "JBSWY3DPEHPK3PXP", algorithm = "SHA-1", digits = 6, period = 30,
    )

    @Test
    fun `un compte ecrit est bien relu via VaultUnlocker`() {
        writer.writeAccounts(listOf(sampleAccount()), contentKey, hmacKey)

        val unlocker = VaultUnlocker(repository)
        val result = unlocker.unlock(password.toCharArray()) as VaultUnlocker.UnlockResult.Success

        assertEquals(1, result.accounts.size)
        assertEquals("GitHub", result.accounts[0].name)
    }

    @Test
    fun `ecrire une liste vide fonctionne (suppression du dernier compte)`() {
        writer.writeAccounts(listOf(sampleAccount()), contentKey, hmacKey)
        writer.writeAccounts(emptyList(), contentKey, hmacKey)

        val unlocker = VaultUnlocker(repository)
        val result = unlocker.unlock(password.toCharArray()) as VaultUnlocker.UnlockResult.Success
        assertEquals(0, result.accounts.size)
    }

    @Test
    fun `le slot mot de passe reste intact apres ecriture de comptes`() {
        writer.writeAccounts(listOf(sampleAccount()), contentKey, hmacKey)

        val vault = repository.read()
        assertEquals(1, vault.slots.size)
        assertTrue(vault.slots[0] is VaultSlot.PasswordSlot)
    }

    @Test
    fun `deux comptes ecrits sont tous les deux relus dans le bon ordre`() {
        val second = sampleAccount().copy(id = "acc-2", name = "ServiceX", secret = "KRSXG5CTMVRXEZLU")
        writer.writeAccounts(listOf(sampleAccount(), second), contentKey, hmacKey)

        val unlocker = VaultUnlocker(repository)
        val result = unlocker.unlock(password.toCharArray()) as VaultUnlocker.UnlockResult.Success
        assertEquals(2, result.accounts.size)
        assertEquals("GitHub", result.accounts[0].name)
        assertEquals("ServiceX", result.accounts[1].name)
    }

    @Test
    fun `vault_hmac change a chaque ecriture de comptes`() {
        writer.writeAccounts(listOf(sampleAccount()), contentKey, hmacKey)
        val hmacAfterFirst = repository.read().vaultHmac

        val second = sampleAccount().copy(id = "acc-2", name = "ServiceX")
        writer.writeAccounts(listOf(sampleAccount(), second), contentKey, hmacKey)
        val hmacAfterSecond = repository.read().vaultHmac

        assertTrue(hmacAfterFirst != hmacAfterSecond)
    }
}