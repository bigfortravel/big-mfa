// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.vault

import com.bigfortravel.bigmfa.crypto.AesGcmSivCipher
import com.bigfortravel.bigmfa.crypto.Argon2KeyDerivation
import com.bigfortravel.bigmfa.crypto.HkdfDerivation
import com.bigfortravel.bigmfa.crypto.VaultHmac
import com.bigfortravel.bigmfa.vault.model.VaultFile
import com.bigfortravel.bigmfa.vault.model.VaultSlot
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull

class VaultUnlockerTest {

    private lateinit var tempFile: File
    private lateinit var repository: VaultRepository
    private lateinit var unlocker: VaultUnlocker

    private val correctPassword = "MonMotDePasse123!"
    private val tier = Argon2KeyDerivation.MemoryTier.CONTRAINT // le plus rapide, pour des tests courts

    @Before
    fun setUp() {
        tempFile = File.createTempFile("vault-unlocker-test", ".json")
        repository = VaultRepository(tempFile)
        unlocker = VaultUnlocker(repository)
    }

    @After
    fun tearDown() {
        tempFile.delete()
    }

    private fun bytesToHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    /** Construit et écrit un coffre valide, avec un seul compte "GitHub". */
    private fun buildAndWriteValidVault(password: String = correctPassword) {
        val salt = Argon2KeyDerivation.generateSalt()
        val derivedKey = Argon2KeyDerivation.derive(password.toCharArray(), salt, tier)

        val masterKey = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        val wrappedMk = AesGcmSivCipher.encrypt(masterKey, derivedKey)

        val contentKey = HkdfDerivation.expand(masterKey, "big-mfa-content-v1")
        val hmacKey = HkdfDerivation.expand(masterKey, "big-mfa-integrity-v1")

        val accountsJson = JSONArray().apply {
            put(
                JSONObject().apply {
                    put("id", "acc-1")
                    put("type", "totp")
                    put("name", "GitHub")
                    put("issuer", "GitHub Inc.")
                    put("secret", "JBSWY3DPEHPK3PXP")
                    put("algorithm", "SHA-1")
                    put("digits", 6)
                    put("period", 30)
                },
            )
        }
        val contentBytes = accountsJson.toString().toByteArray(Charsets.UTF_8)
        val encryptedContent = AesGcmSivCipher.encrypt(contentBytes, contentKey)

        val passwordSlot = VaultSlot.PasswordSlot(
            uuid = "slot-1",
            wrappedKey = bytesToHex(wrappedMk.ciphertext),
            nonce = bytesToHex(wrappedMk.nonce),
            tag = bytesToHex(wrappedMk.tag),
            salt = bytesToHex(salt),
            memoryKiB = tier.memoryKiB,
            iterations = tier.iterations,
            parallelism = tier.parallelism,
        )

        val vaultWithoutHmac = VaultFile(
            createdAt = "2026-08-20T10:00:00Z",
            modifiedAt = "2026-08-20T10:00:00Z",
            slots = listOf(passwordSlot),
            content = bytesToHex(encryptedContent.ciphertext),
            contentNonce = bytesToHex(encryptedContent.nonce),
            contentTag = bytesToHex(encryptedContent.tag),
            vaultHmac = "", // calculé juste après
        )

        val canonicalBytes = repository.canonicalBytesForHmac(vaultWithoutHmac)
        val mac = VaultHmac.sign(canonicalBytes, hmacKey)
        val finalVault = vaultWithoutHmac.copy(vaultHmac = bytesToHex(mac))

        repository.write(finalVault)
    }

    @Test
    fun `1 - bon mot de passe deverrouille et retrouve le compte GitHub`() {
        buildAndWriteValidVault()
        val result = unlocker.unlock(correctPassword.toCharArray())

        assertTrue(result is VaultUnlocker.UnlockResult.Success)
        val success = result as VaultUnlocker.UnlockResult.Success
        assertEquals(1, success.accounts.size)
        assertEquals("GitHub", success.accounts[0].name)
    }

    @Test
    fun `2 - mauvais mot de passe refuse le deverrouillage, message generique`() {
        buildAndWriteValidVault()
        val result = unlocker.unlock("MauvaisMotDePasse999!".toCharArray())

        assertTrue(result is VaultUnlocker.UnlockResult.WrongPasswordOrCorruptedSlot)
    }

    @Test
    fun `3 - vault_hmac corrompu bloque le chargement, jamais de chargement partiel`() {
        buildAndWriteValidVault()

        // On corrompt le fichier directement sur le disque après écriture
        val vault = repository.read()
        val corrupted = vault.copy(vaultHmac = "00".repeat(32))
        repository.write(corrupted)

        val result = unlocker.unlock(correctPassword.toCharArray())
        assertTrue(result is VaultUnlocker.UnlockResult.VaultDataCorrupted)
    }

    @Test
    fun `4 - contenu chiffre corrompu (mais hmac invalide en consequence) est detecte`() {
        buildAndWriteValidVault()

        val vault = repository.read()
        // On modifie le contenu chiffré SANS recalculer le hmac -> incohérence détectée
        val corrupted = vault.copy(content = "00" + vault.content.substring(2))
        repository.write(corrupted)

        val result = unlocker.unlock(correctPassword.toCharArray())
        assertTrue(result is VaultUnlocker.UnlockResult.VaultDataCorrupted)
    }

    @Test
    fun `5 - aucun slot mot de passe present est detecte explicitement`() {
        buildAndWriteValidVault()
        val vault = repository.read()
        val withoutPasswordSlot = vault.copy(slots = emptyList())
        repository.write(withoutPasswordSlot)

        val result = unlocker.unlock(correctPassword.toCharArray())
        assertTrue(result is VaultUnlocker.UnlockResult.NoPasswordSlotFound)
    }

    @Test
    fun `6 - deux mots de passe differents donnent deux coffres non interchangeables`() {
        buildAndWriteValidVault(password = "PremierMotDePasse1!")
        val resultWithSecondPassword = unlocker.unlock("DeuxiemeMotDePasse2!".toCharArray())

        assertTrue(resultWithSecondPassword is VaultUnlocker.UnlockResult.WrongPasswordOrCorruptedSlot)
    }

    @Test
    fun `7 - deverrouillage reussi expose des cles de 256 bits`() {
        buildAndWriteValidVault()
        val result = unlocker.unlock(correctPassword.toCharArray()) as VaultUnlocker.UnlockResult.Success

        assertEquals(32, result.masterKey.size)
        assertEquals(32, result.contentKey.size)
        assertEquals(32, result.hmacKey.size)
    }
    @Test
    fun `8 - aucun fichier de coffre du tout est detecte proprement, sans exception`() {
        // Fichier temporaire supprimé avant le test -- simule un tout
        // premier lancement de l'app, sans coffre jamais créé.
        tempFile.delete()

        val result = unlocker.unlock(correctPassword.toCharArray())
        assertTrue(result is VaultUnlocker.UnlockResult.NoPasswordSlotFound)
    }
    @Test
    fun `9 - deverrouillage biometrique reussi avec un vrai cipher logiciel`() {
        buildAndWriteValidVault()

        val unlockByPassword = unlocker.unlock(correctPassword.toCharArray()) as VaultUnlocker.UnlockResult.Success

        val biometricKey = javax.crypto.KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val encryptCipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        encryptCipher.init(javax.crypto.Cipher.ENCRYPT_MODE, biometricKey)
        val wrapped = encryptCipher.doFinal(unlockByPassword.masterKey)
        val nonce = encryptCipher.iv
        val ciphertext = wrapped.copyOfRange(0, wrapped.size - 16)
        val tag = wrapped.copyOfRange(wrapped.size - 16, wrapped.size)

        val biometricSlot = com.bigfortravel.bigmfa.vault.model.VaultSlot.BiometricSlot(
            uuid = "bio-slot-1",
            wrappedKey = ciphertext.joinToString("") { "%02x".format(it) },
            nonce = nonce.joinToString("") { "%02x".format(it) },
            tag = tag.joinToString("") { "%02x".format(it) },
            keystoreAlias = "test-alias",
        )

        // Recalcul du vault_hmac APRÈS ajout du slot -- exactement ce que
        // fait SlotManager en production, indispensable ici aussi.
        val vault = repository.read()
        val vaultWithBiometricNoHmac = vault.copy(slots = vault.slots + biometricSlot, vaultHmac = "")
        val canonicalBytes = repository.canonicalBytesForHmac(vaultWithBiometricNoHmac)
        val newMac = com.bigfortravel.bigmfa.crypto.VaultHmac.sign(canonicalBytes, unlockByPassword.hmacKey)
        val newMacHex = newMac.joinToString("") { "%02x".format(it) }
        repository.write(vaultWithBiometricNoHmac.copy(vaultHmac = newMacHex))

        val decryptCipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        decryptCipher.init(
            javax.crypto.Cipher.DECRYPT_MODE,
            biometricKey,
            javax.crypto.spec.GCMParameterSpec(128, nonce),
        )

        val result = unlocker.unlockWithBiometric(decryptCipher)
        assertTrue(result is VaultUnlocker.UnlockResult.Success)
        assertEquals(1, (result as VaultUnlocker.UnlockResult.Success).accounts.size)
    }

    @Test
    fun `10 - deverrouillage biometrique sans slot biometrique echoue proprement`() {
        buildAndWriteValidVault()

        val dummyKey = javax.crypto.KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val dummyCipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        dummyCipher.init(javax.crypto.Cipher.DECRYPT_MODE, dummyKey, javax.crypto.spec.GCMParameterSpec(128, ByteArray(12)))

        val result = unlocker.unlockWithBiometric(dummyCipher)
        assertTrue(result is VaultUnlocker.UnlockResult.WrongPasswordOrCorruptedSlot)
    }

    @Test
    fun `11 - unlockWithBiometric sur un coffre inexistant renvoie NoPasswordSlotFound`() {
        tempFile.delete()

        val dummyKey = javax.crypto.KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val dummyCipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        dummyCipher.init(javax.crypto.Cipher.DECRYPT_MODE, dummyKey, javax.crypto.spec.GCMParameterSpec(128, ByteArray(12)))

        val result = unlocker.unlockWithBiometric(dummyCipher)
        assertTrue(result is VaultUnlocker.UnlockResult.NoPasswordSlotFound)
    }
    @Test
    fun `12 - hasBiometricSlot est false sans slot biometrique, true avec`() {
        buildAndWriteValidVault()
        assertFalse(unlocker.hasBiometricSlot())

        val vault = repository.read()
        val biometricSlot = com.bigfortravel.bigmfa.vault.model.VaultSlot.BiometricSlot(
            uuid = "bio-slot-1", wrappedKey = "aabbcc", nonce = "112233445566778899001122",
            tag = "aabbccddeeff00112233445566778899", keystoreAlias = "test-alias",
        )
        repository.write(vault.copy(slots = vault.slots + biometricSlot))

        assertTrue(unlocker.hasBiometricSlot())
    }

    @Test
    fun `13 - getBiometricSlotInfo renvoie null sans slot, les bonnes infos avec`() {
        buildAndWriteValidVault()
        assertNull(unlocker.getBiometricSlotInfo())

        val vault = repository.read()
        val biometricSlot = com.bigfortravel.bigmfa.vault.model.VaultSlot.BiometricSlot(
            uuid = "bio-slot-1", wrappedKey = "aabbcc", nonce = "112233445566778899001122",
            tag = "aabbccddeeff00112233445566778899", keystoreAlias = "test-alias",
        )
        repository.write(vault.copy(slots = vault.slots + biometricSlot))

        val info = unlocker.getBiometricSlotInfo()
        assertEquals("test-alias", info?.alias)
        assertEquals(12, info?.nonce?.size)
    }
    @Test
    fun `14 - changement de mot de passe reussi permet de deverrouiller avec le nouveau`() {
        buildAndWriteValidVault()

        val result = unlocker.changeMasterPassword(
            correctPassword.toCharArray(),
            "NouveauMotDePasse456!".toCharArray(),
        )
        assertTrue(result is VaultUnlocker.ChangePasswordResult.Success)

        val unlockWithNew = unlocker.unlock("NouveauMotDePasse456!".toCharArray())
        assertTrue(unlockWithNew is VaultUnlocker.UnlockResult.Success)
    }

    @Test
    fun `15 - ancien mot de passe ne fonctionne plus apres changement`() {
        buildAndWriteValidVault()

        unlocker.changeMasterPassword(
            correctPassword.toCharArray(),
            "NouveauMotDePasse456!".toCharArray(),
        )

        val unlockWithOld = unlocker.unlock(correctPassword.toCharArray())
        assertTrue(unlockWithOld is VaultUnlocker.UnlockResult.WrongPasswordOrCorruptedSlot)
    }

    @Test
    fun `16 - mauvais mot de passe actuel refuse le changement`() {
        buildAndWriteValidVault()

        val result = unlocker.changeMasterPassword(
            "MauvaisMotDePasse999!".toCharArray(),
            "NouveauMotDePasse456!".toCharArray(),
        )
        assertTrue(result is VaultUnlocker.ChangePasswordResult.WrongCurrentPassword)

        // Le coffre ne doit PAS avoir été modifié -- l'ancien mot de passe fonctionne toujours.
        val unlockWithOld = unlocker.unlock(correctPassword.toCharArray())
        assertTrue(unlockWithOld is VaultUnlocker.UnlockResult.Success)
    }

    @Test
    fun `17 - les comptes restent intacts apres changement de mot de passe`() {
        buildAndWriteValidVault()

        unlocker.changeMasterPassword(
            correctPassword.toCharArray(),
            "NouveauMotDePasse456!".toCharArray(),
        )

        val result = unlocker.unlock("NouveauMotDePasse456!".toCharArray()) as VaultUnlocker.UnlockResult.Success
        assertEquals(1, result.accounts.size)
        assertEquals("GitHub", result.accounts[0].name)
    }

    @Test
    fun `18 - slot biometrique existant reste fonctionnel apres changement de mot de passe`() {
        buildAndWriteValidVault()

        // Ajoute un slot biométrique avant le changement de mot de passe.
        val vault = repository.read()
        val unlockResult = unlocker.unlock(correctPassword.toCharArray()) as VaultUnlocker.UnlockResult.Success
        val biometricKey = javax.crypto.KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val encryptCipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        encryptCipher.init(javax.crypto.Cipher.ENCRYPT_MODE, biometricKey)
        val wrapped = encryptCipher.doFinal(unlockResult.masterKey)
        val nonce = encryptCipher.iv
        val ciphertext = wrapped.copyOfRange(0, wrapped.size - 16)
        val tag = wrapped.copyOfRange(wrapped.size - 16, wrapped.size)

        val biometricSlot = com.bigfortravel.bigmfa.vault.model.VaultSlot.BiometricSlot(
            uuid = "bio-slot-1", wrappedKey = ciphertext.joinToString("") { "%02x".format(it) },
            nonce = nonce.joinToString("") { "%02x".format(it) },
            tag = tag.joinToString("") { "%02x".format(it) }, keystoreAlias = "test-alias",
        )
        val vaultWithBioNoHmac = vault.copy(slots = vault.slots + biometricSlot, vaultHmac = "")
        val bioMac = com.bigfortravel.bigmfa.crypto.VaultHmac.sign(
            repository.canonicalBytesForHmac(vaultWithBioNoHmac), unlockResult.hmacKey,
        )
        repository.write(vaultWithBioNoHmac.copy(vaultHmac = bioMac.joinToString("") { "%02x".format(it) }))

        // Change le mot de passe.
        unlocker.changeMasterPassword(correctPassword.toCharArray(), "NouveauMotDePasse456!".toCharArray())

        // Le slot biométrique doit toujours fonctionner (même MK, jamais touchée).
        val decryptCipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        decryptCipher.init(javax.crypto.Cipher.DECRYPT_MODE, biometricKey, javax.crypto.spec.GCMParameterSpec(128, nonce))
        val bioResult = unlocker.unlockWithBiometric(decryptCipher)
        assertTrue(bioResult is VaultUnlocker.UnlockResult.Success)
    }
}