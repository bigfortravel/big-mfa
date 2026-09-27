// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.vault.model

/**
 * Un slot de déverrouillage — enveloppe la clé maîtresse (MK) chiffrée
 * selon une méthode donnée. Le slot mot de passe est OBLIGATOIRE et non
 * supprimable ; le slot biométrique est additif uniquement (jamais seul),
 * exactement comme décidé dans le schéma de dérivation de clé.
 *
 * "sealed class" : le compilateur force à gérer explicitement CHAQUE type
 * de slot partout où on en manipule un (dans un "when" par exemple) — il
 * devient impossible d'oublier un cas par erreur en ajoutant plus tard un
 * troisième type de slot.
 */
sealed class VaultSlot {
    abstract val uuid: String
    abstract val wrappedKey: String  // clé maîtresse chiffrée, en hexadécimal
    abstract val nonce: String       // hexadécimal, 12 octets (AES-256-GCM-SIV)
    abstract val tag: String         // hexadécimal, 16 octets

    data class PasswordSlot(
        override val uuid: String,
        override val wrappedKey: String,
        override val nonce: String,
        override val tag: String,
        val salt: String,            // hexadécimal, 32 octets (Argon2id)
        val memoryKiB: Int,
        val iterations: Int,
        val parallelism: Int,
    ) : VaultSlot()

    data class BiometricSlot(
        override val uuid: String,
        override val wrappedKey: String,
        override val nonce: String,
        override val tag: String,
        val keystoreAlias: String,
    ) : VaultSlot()
}