// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.vault.model

/**
 * Structure complète du fichier de coffre — reflète exactement le format
 * JSON défini avec la révision Aegis (slots + content chiffré séparément
 * + vault_hmac global couvrant TOUTE la structure, contrairement à Aegis
 * qui ne signe que le contenu de chaque slot individuellement).
 *
 * Toutes les valeurs binaires (clés, nonces, tags) sont stockées en
 * hexadécimal ici — cohérent avec le choix "nonce/tag explicites, jamais
 * un blob opaque" tranché pour l'auditabilité du format.
 */
data class VaultFile(
    val format: String = "big-mfa-vault",
    val version: Int = 1,
    val createdAt: String,
    val modifiedAt: String,
    val slots: List<VaultSlot>,
    val content: String,        // hex, contenu chiffré (AES-256-GCM-SIV)
    val contentNonce: String,   // hex, 12 octets
    val contentTag: String,     // hex, 16 octets
    val vaultHmac: String,      // hex, HMAC-SHA256, 32 octets — sur TOUT le reste
)