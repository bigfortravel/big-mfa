// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.vault

import com.bigfortravel.bigmfa.crypto.AesGcmSivCipher
import com.bigfortravel.bigmfa.crypto.Argon2KeyDerivation
import com.bigfortravel.bigmfa.crypto.HkdfDerivation
import com.bigfortravel.bigmfa.vault.model.VaultAccount
import com.bigfortravel.bigmfa.vault.model.VaultFile
import com.bigfortravel.bigmfa.vault.model.VaultSlot
import org.json.JSONArray
import javax.crypto.Cipher

/**
 * Le flux de déverrouillage complet — mot de passe ET biométrie
 * partagent désormais la même suite d'étapes après obtention de MK
 * (HKDF, vérification d'intégrité, déchiffrement du contenu) via
 * finishUnlock(), pour éviter toute divergence entre les deux chemins.
 */
class VaultUnlocker(private val repository: VaultRepository) {

    sealed class UnlockResult {
        class Success(
            val masterKey: ByteArray,
            val contentKey: ByteArray,
            val hmacKey: ByteArray,
            val accounts: List<VaultAccount>,
        ) : UnlockResult() {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other !is Success) return false
                return masterKey.contentEquals(other.masterKey) &&
                        contentKey.contentEquals(other.contentKey) &&
                        hmacKey.contentEquals(other.hmacKey) &&
                        accounts == other.accounts
            }

            override fun hashCode(): Int {
                var result = masterKey.contentHashCode()
                result = 31 * result + contentKey.contentHashCode()
                result = 31 * result + hmacKey.contentHashCode()
                result = 31 * result + accounts.hashCode()
                return result
            }
        }

        object WrongPasswordOrCorruptedSlot : UnlockResult()
        object VaultDataCorrupted : UnlockResult()
        object NoPasswordSlotFound : UnlockResult()
    }

    fun unlock(password: CharArray): UnlockResult {
        if (!repository.exists()) {
            return UnlockResult.NoPasswordSlotFound
        }

        val vault = repository.read()
        val passwordSlot = vault.slots.filterIsInstance<VaultSlot.PasswordSlot>().firstOrNull()
            ?: return UnlockResult.NoPasswordSlotFound

        val masterKey: ByteArray
        try {
            val derivedKey = Argon2KeyDerivation.derive(
                password = password,
                salt = hexToBytes(passwordSlot.salt),
                memoryKiB = passwordSlot.memoryKiB,
                iterations = passwordSlot.iterations,
                parallelism = passwordSlot.parallelism,
            )
            val wrapped = AesGcmSivCipher.EncryptedData(
                nonce = hexToBytes(passwordSlot.nonce),
                ciphertext = hexToBytes(passwordSlot.wrappedKey),
                tag = hexToBytes(passwordSlot.tag),
            )
            masterKey = AesGcmSivCipher.decrypt(wrapped, derivedKey)
        } catch (_: Exception) {
            return UnlockResult.WrongPasswordOrCorruptedSlot
        }

        return finishUnlock(vault, masterKey)
    }

    /**
     * Déverrouillage biométrique — cipher DÉJÀ authentifié par
     * BiometricPrompt (voir BiometricAuthenticator), initialisé pour le
     * déchiffrement via KeystoreManager.getDecryptCipher(). Message
     * générique en cas d'échec, comme pour le mot de passe -- jamais
     * d'oracle sur la cause exacte.
     */
    fun unlockWithBiometric(cipher: Cipher): UnlockResult {
        if (!repository.exists()) {
            return UnlockResult.NoPasswordSlotFound
        }

        val vault = repository.read()
        val biometricSlot = vault.slots.filterIsInstance<VaultSlot.BiometricSlot>().firstOrNull()
            ?: return UnlockResult.WrongPasswordOrCorruptedSlot

        val masterKey: ByteArray
        try {
            val ciphertext = hexToBytes(biometricSlot.wrappedKey)
            val tag = hexToBytes(biometricSlot.tag)
            masterKey = cipher.doFinal(ciphertext + tag)
        } catch (_: Exception) {
            return UnlockResult.WrongPasswordOrCorruptedSlot
        }

        return finishUnlock(vault, masterKey)
    }
    /** À vérifier AVANT de proposer le bouton biométrique dans l'UI. */
    fun hasBiometricSlot(): Boolean {
        if (!repository.exists()) return false
        return repository.read().slots.any { it is VaultSlot.BiometricSlot }
    }

    /**
     * Alias Keystore + nonce nécessaires pour préparer le Cipher de
     * déchiffrement (voir KeystoreManager.getDecryptCipher). Retourne null
     * si aucun slot biométrique n'existe -- l'appelant doit vérifier
     * hasBiometricSlot() avant, ou gérer ce cas null proprement.
     */
    fun getBiometricSlotInfo(): BiometricSlotInfo? {
        if (!repository.exists()) return null
        val slot = repository.read().slots.filterIsInstance<VaultSlot.BiometricSlot>().firstOrNull()
            ?: return null
        return BiometricSlotInfo(alias = slot.keystoreAlias, nonce = hexToBytes(slot.nonce))
    }

    data class BiometricSlotInfo(val alias: String, val nonce: ByteArray)
    /**
     * Supprime définitivement le coffre — accessible SANS mot de passe,
     * volontairement, comme côté Chrome : dernier recours en cas de mot de
     * passe oublié. La double confirmation se fait côté UI, jamais ici.
     */
    fun resetVault() {
        repository.delete()
    }

    private fun finishUnlock(vault: VaultFile, masterKey: ByteArray): UnlockResult {
        val contentKey = HkdfDerivation.expand(masterKey, "big-mfa-content-v1")
        val hmacKey = HkdfDerivation.expand(masterKey, "big-mfa-integrity-v1")

        val canonicalBytes = repository.canonicalBytesForHmac(vault)
        val integrityResult = VaultIntegrityChecker.verify(canonicalBytes, hmacKey, vault.vaultHmac)
        if (integrityResult is VaultIntegrityChecker.IntegrityResult.Corrupted) {
            return UnlockResult.VaultDataCorrupted
        }

        val contentEncrypted = AesGcmSivCipher.EncryptedData(
            nonce = hexToBytes(vault.contentNonce),
            ciphertext = hexToBytes(vault.content),
            tag = hexToBytes(vault.contentTag),
        )
        val contentBytes = AesGcmSivCipher.decrypt(contentEncrypted, contentKey)
        val accounts = parseAccountsJson(contentBytes)

        return UnlockResult.Success(masterKey, contentKey, hmacKey, accounts)
    }

    /**
     * Change le mot de passe maître -- MK ne change JAMAIS, seul le slot
     * mot de passe (dérivation + enveloppe) est reconstruit. Les comptes
     * restent intacts, aucun rechiffrement de leur contenu nécessaire.
     */
    sealed class ChangePasswordResult {
        object Success : ChangePasswordResult()
        object WrongCurrentPassword : ChangePasswordResult()
        object Failed : ChangePasswordResult()
    }

    fun changeMasterPassword(currentPassword: CharArray, newPassword: CharArray): ChangePasswordResult {
        val currentSessionResult = unlock(currentPassword)
        if (currentSessionResult !is UnlockResult.Success) {
            return ChangePasswordResult.WrongCurrentPassword
        }

        return try {
            val vault = repository.read()
            val tier = com.bigfortravel.bigmfa.crypto.Argon2KeyDerivation.detectTierForThisDevice(
                Runtime.getRuntime().maxMemory(),
            )
            val newSalt = com.bigfortravel.bigmfa.crypto.Argon2KeyDerivation.generateSalt()
            val newDerivedKey = com.bigfortravel.bigmfa.crypto.Argon2KeyDerivation.derive(
                newPassword, newSalt, tier,
            )
            val wrappedMk = AesGcmSivCipher.encrypt(currentSessionResult.masterKey, newDerivedKey)

            val newPasswordSlot = VaultSlot.PasswordSlot(
                uuid = java.util.UUID.randomUUID().toString(),
                wrappedKey = bytesToHex(wrappedMk.ciphertext),
                nonce = bytesToHex(wrappedMk.nonce),
                tag = bytesToHex(wrappedMk.tag),
                salt = bytesToHex(newSalt),
                memoryKiB = tier.memoryKiB,
                iterations = tier.iterations,
                parallelism = tier.parallelism,
            )

            val otherSlots = vault.slots.filterNot { it is VaultSlot.PasswordSlot }
            val vaultWithoutHmac = vault.copy(slots = listOf(newPasswordSlot) + otherSlots, vaultHmac = "")
            val mac = com.bigfortravel.bigmfa.crypto.VaultHmac.sign(
                repository.canonicalBytesForHmac(vaultWithoutHmac),
                currentSessionResult.hmacKey,
            )
            repository.write(vaultWithoutHmac.copy(vaultHmac = bytesToHex(mac)))

            ChangePasswordResult.Success
        } catch (_: Exception) {
            ChangePasswordResult.Failed
        } finally {
            currentPassword.fill('\u0000')
            newPassword.fill('\u0000')
        }
    }

    private fun bytesToHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    private fun parseAccountsJson(bytes: ByteArray): List<VaultAccount> {
        val array = JSONArray(String(bytes, Charsets.UTF_8))
        val accounts = mutableListOf<VaultAccount>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            accounts.add(
                VaultAccount(
                    id = obj.getString("id"),
                    type = obj.getString("type"),
                    name = obj.getString("name"),
                    issuer = obj.getString("issuer"),
                    secret = obj.getString("secret"),
                    algorithm = obj.getString("algorithm"),
                    digits = obj.getInt("digits"),
                    period = if (obj.has("period")) obj.getInt("period") else null,
                    counter = if (obj.has("counter")) obj.getLong("counter") else null,
                    lastUsedAt = if (obj.has("last_used_at")) obj.getLong("last_used_at") else null,
                    autofillPackageName = if (obj.has("autofill_package_name")) obj.getString("autofill_package_name") else null,
                    autofillWebDomain = if (obj.has("autofill_web_domain")) obj.getString("autofill_web_domain") else null,
                ),
            )
        }
        return accounts
    }

    private fun hexToBytes(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "Chaîne hexadécimale de longueur invalide" }
        return ByteArray(hex.length / 2) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}