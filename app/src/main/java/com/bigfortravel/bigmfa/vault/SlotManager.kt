// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.vault

import com.bigfortravel.bigmfa.crypto.VaultHmac
import com.bigfortravel.bigmfa.vault.model.VaultSlot
import java.util.UUID
import javax.crypto.Cipher

/**
 * Ajout/retrait du slot biométrique — additif uniquement, jamais seul.
 * Le slot mot de passe n'est JAMAIS touché ici, ni supprimable par ce
 * chemin. Ne vérifie aucun accès lui-même — suppose que l'appelant a
 * déjà prouvé la possession de MK (déverrouillage réussi juste avant).
 * La "re-confirmation" décidée dans le schéma se fera côté écran, en
 * rappelant VaultUnlocker.unlock() une seconde fois avant d'appeler ceci.
 *
 * vault_hmac est TOUJOURS recalculé et persisté ici même, jamais laissé
 * à la charge de l'appelant — un seul point de vérité, pour ne jamais
 * risquer d'oublier cette étape ailleurs dans le code plus tard.
 */
class SlotManager(private val repository: VaultRepository) {

    private val TAG_LENGTH_BYTES = 16

    /**
     * @param encryptCipher Cipher DÉJÀ initialisé et authentifié
     *   biométriquement (indifféremment : Keystore matériel réel, ou en
     *   test, une clé logicielle ordinaire — cette fonction ne fait
     *   aucune différence).
     */
    fun addBiometricSlot(masterKey: ByteArray, hmacKey: ByteArray, encryptCipher: Cipher, keystoreAlias: String) {
        val vault = repository.read()

        val ciphertextWithTag = encryptCipher.doFinal(masterKey)
        val nonce = encryptCipher.iv
        val ciphertext = ciphertextWithTag.copyOfRange(0, ciphertextWithTag.size - TAG_LENGTH_BYTES)
        val tag = ciphertextWithTag.copyOfRange(ciphertextWithTag.size - TAG_LENGTH_BYTES, ciphertextWithTag.size)

        val newSlot = VaultSlot.BiometricSlot(
            uuid = UUID.randomUUID().toString(),
            wrappedKey = bytesToHex(ciphertext),
            nonce = bytesToHex(nonce),
            tag = bytesToHex(tag),
            keystoreAlias = keystoreAlias,
        )

        // Un seul slot biométrique à la fois -- on remplace, on n'accumule pas.
        val slotsWithoutOldBiometric = vault.slots.filterNot { it is VaultSlot.BiometricSlot }
        val updatedVault = vault.copy(slots = slotsWithoutOldBiometric + newSlot)

        persistWithRecomputedHmac(updatedVault, hmacKey)
    }

    /** Retire le slot biométrique. Le slot mot de passe n'est JAMAIS affecté. */
    fun removeBiometricSlot(hmacKey: ByteArray) {
        val vault = repository.read()
        val updatedVault = vault.copy(slots = vault.slots.filterNot { it is VaultSlot.BiometricSlot })
        persistWithRecomputedHmac(updatedVault, hmacKey)
    }

    fun hasBiometricSlot(): Boolean = repository.read().slots.any { it is VaultSlot.BiometricSlot }

    /** Garde-fou : le slot mot de passe doit TOUJOURS être présent. */
    fun hasPasswordSlot(): Boolean = repository.read().slots.any { it is VaultSlot.PasswordSlot }

    private fun persistWithRecomputedHmac(vault: com.bigfortravel.bigmfa.vault.model.VaultFile, hmacKey: ByteArray) {
        val vaultWithClearedHmac = vault.copy(vaultHmac = "")
        val canonicalBytes = repository.canonicalBytesForHmac(vaultWithClearedHmac)
        val mac = VaultHmac.sign(canonicalBytes, hmacKey)
        repository.write(vaultWithClearedHmac.copy(vaultHmac = bytesToHex(mac)))
    }

    private fun bytesToHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
}