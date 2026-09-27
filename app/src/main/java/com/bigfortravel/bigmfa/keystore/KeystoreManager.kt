// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.keystore

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Génère et utilise des clés AES-256 liées biométriquement dans le
 * Keystore matériel réel de l'appareil — précondition matérielle
 * stricte, pas un simple portail UX (voir le schéma de déverrouillage).
 *
 * setUserAuthenticationValidityDurationSeconds(-1) : authentification
 * biométrique exigée à CHAQUE usage de la clé, jamais de fenêtre de
 * grâce — cohérent avec le modèle Chrome où chaque déverrouillage
 * redemande le mot de passe.
 *
 * StrongBox essayé en premier (API 28+), repli automatique et silencieux
 * vers TEE si absent sur l'appareil — jamais une erreur, cohérent avec
 * la décision "TEE minimum exigé, StrongBox utilisé si disponible".
 */
object KeystoreManager {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val GCM_TAG_LENGTH_BITS = 128

    fun generateBiometricBoundKey(alias: String) {
        val builder = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationValidityDurationSeconds(-1)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // setInvalidatedByBiometricEnrollment n'existe qu'à partir d'Android 7.
            builder.setInvalidatedByBiometricEnrollment(true)
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                builder.setIsStrongBoxBacked(true)
                keyGenerator.init(builder.build())
                keyGenerator.generateKey()
                return
            } catch (_: StrongBoxUnavailableException) {
                // StrongBox absent sur cet appareil précis -> repli TEE, PAS une erreur.
                builder.setIsStrongBoxBacked(false)
            }
        }

        keyGenerator.init(builder.build())
        keyGenerator.generateKey()
    }

    /**
     * Clé liée à l'appareil, SANS exigence d'authentification biométrique
     * -- utilisée uniquement pour chiffrer le compteur anti-bruteforce,
     * qui doit être lisible automatiquement avant toute authentification
     * de l'utilisateur (contrairement aux clés du coffre lui-même).
     */
    fun generateDeviceBoundKey(alias: String) {
        val builder = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        keyGenerator.init(builder.build())
        keyGenerator.generateKey()
    }

    fun keyExists(alias: String): Boolean {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        return keyStore.containsAlias(alias)
    }

    fun deleteKey(alias: String) {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        if (keyStore.containsAlias(alias)) {
            keyStore.deleteEntry(alias)
        }
    }

    private fun getSecretKey(alias: String): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        return keyStore.getKey(alias, null) as SecretKey
    }

    /** Cipher prêt pour un chiffrement — à passer dans BiometricPrompt.CryptoObject
     *  (viendra dans BiometricAuthenticator.kt, le prochain fichier). */
    fun getEncryptCipher(alias: String): Cipher {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey(alias))
        return cipher
    }

    /** Le nonce utilisé au chiffrement doit être refourni ici pour déchiffrer. */
    fun getDecryptCipher(alias: String, nonce: ByteArray): Cipher {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(alias), GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce))
        return cipher
    }
}