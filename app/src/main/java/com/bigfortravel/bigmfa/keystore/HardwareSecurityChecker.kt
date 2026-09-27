// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.keystore

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.UUID
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory

/**
 * Détecte le vrai niveau de sécurité matérielle offert par CET appareil
 * précis — exigence minimale décidée : TEE (matériel réel), refus
 * uniquement du cas résiduel "aucun ancrage matériel du tout". Vérifié
 * via la vraie API, jamais via le seul numéro de version Android (trop
 * imprécis — beaucoup de milieu de gamme récent n'a pas StrongBox).
 *
 * Nécessite un vrai Keystore Android — ne fonctionne PAS en JVM pur,
 * contrairement à tout ce qu'on a construit jusqu'ici. Utilisable
 * uniquement depuis androidTest ou l'app réelle sur un appareil/émulateur.
 */
object HardwareSecurityChecker {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    enum class SecurityLevel { STRONGBOX, TEE, SOFTWARE_ONLY }

    /**
     * Génère une clé de test temporaire, interroge son niveau réel, la
     * supprime immédiatement après.
     */
    fun detectSecurityLevel(): SecurityLevel {
        val alias = "big-mfa-hw-check-${UUID.randomUUID()}"
        try {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            keyGenerator.init(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
            val key = keyGenerator.generateKey()
            val keyInfo = getKeyInfo(key)

            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+ : API précise, distingue explicitement StrongBox de TEE.
                when (keyInfo.securityLevel) {
                    KeyProperties.SECURITY_LEVEL_STRONGBOX -> SecurityLevel.STRONGBOX
                    KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> SecurityLevel.TEE
                    else -> SecurityLevel.SOFTWARE_ONLY
                }
            } else {
                // Avant Android 12 : seule l'info binaire "matériel ou pas" existe.
                if (keyInfo.isInsideSecureHardware) SecurityLevel.TEE else SecurityLevel.SOFTWARE_ONLY
            }
        } finally {
            deleteKeyIfExists(alias)
        }
    }

    /** true pour TEE ou StrongBox — false UNIQUEMENT pour le cas résiduel
     *  sans aucun ancrage matériel, celui qu'on a décidé de refuser. */
    fun meetsMinimumRequirement(level: SecurityLevel): Boolean = level != SecurityLevel.SOFTWARE_ONLY

    private fun getKeyInfo(key: SecretKey): KeyInfo {
        val factory = SecretKeyFactory.getInstance(key.algorithm, ANDROID_KEYSTORE)
        return factory.getKeySpec(key, KeyInfo::class.java) as KeyInfo
    }

    private fun deleteKeyIfExists(alias: String) {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        if (keyStore.containsAlias(alias)) {
            keyStore.deleteEntry(alias)
        }
    }
}