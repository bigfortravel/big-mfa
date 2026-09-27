// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.vault

import com.bigfortravel.bigmfa.crypto.VaultHmac

/**
 * Vérification d'intégrité globale du coffre — traduction directe de
 * l'esprit REL/DATA-01 en Kotlin : toute incohérence structurelle DOIT
 * bloquer le chargement, jamais être absorbée silencieusement en tableau
 * vide ou en valeur par défaut.
 *
 * Volontairement indépendant du format JSON exact — reçoit les données
 * déjà mises sous une forme canonique (voir VaultRepository, à venir),
 * ne s'occupe que de la vérification cryptographique elle-même.
 */
object VaultIntegrityChecker {

    sealed class IntegrityResult {
        object Valid : IntegrityResult()
        object Corrupted : IntegrityResult()
    }

    /**
     * @param canonicalData Représentation déterministe de TOUT le fichier
     *   de coffre SAUF le champ vault_hmac lui-même.
     * @param hmacKey La sous-clé d'intégrité, dérivée via HKDF depuis MK.
     * @param expectedMacHex Le vault_hmac lu depuis le fichier, en hexadécimal.
     */
    fun verify(canonicalData: ByteArray, hmacKey: ByteArray, expectedMacHex: String): IntegrityResult {
        val expectedMac = hexToBytes(expectedMacHex)
        val isValid = VaultHmac.verify(canonicalData, hmacKey, expectedMac)
        return if (isValid) IntegrityResult.Valid else IntegrityResult.Corrupted
    }

    private fun hexToBytes(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "Chaîne hexadécimale de longueur invalide" }
        return ByteArray(hex.length / 2) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}