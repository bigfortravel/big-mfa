// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.vault.model

/**
 * Un compte TOTP/HOTP tel que stocké dans le coffre (métadonnées en clair
 * uniquement — le secret y figure en Base32 texte, jamais en octets bruts,
 * pour rester cohérent avec le format déjà utilisé côté Chrome et éviter
 * tout piège ByteArray dans une data class).
 */
data class VaultAccount(
    val id: String,
    val type: String,        // "totp" ou "hotp"
    val name: String,
    val issuer: String,
    val secret: String,      // Base32, ex. "JBSWY3DPEHPK3PXP"
    val algorithm: String,   // "SHA-1", "SHA-256" ou "SHA-512"
    val digits: Int,
    val period: Int? = null,   // pertinent uniquement si type == "totp"
    val counter: Long? = null, // pertinent uniquement si type == "hotp"
    val lastUsedAt: Long? = null,
    // Phase 8.2 -- liaison Autofill anti-phishing. Optionnel, null par
    // défaut sur les comptes existants et les nouveaux comptes tant que
    // l'utilisateur ne l'a pas explicitement associé. Stocke soit un nom
    // de package Android ("com.google.android.gm"), soit un domaine web
    // ("github.com") -- jamais les deux à la fois pour un même compte.
    val autofillPackageName: String? = null,
    val autofillWebDomain: String? = null,
)