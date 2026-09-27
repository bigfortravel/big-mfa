package com.bigfortravel.bigmfa.security

// SPDX-License-Identifier: GPL-3.0-only

import android.content.Context
import android.util.Base64
import com.bigfortravel.bigmfa.keystore.KeystoreManager

/**
 * Persiste l'état anti-bruteforce au-delà de la durée de vie du
 * processus -- sans cela, un attaquant ayant le téléphone en main
 * pourrait forcer la fermeture de l'app entre chaque lot de tentatives
 * pour remettre le compteur à zéro (le processus est tué fréquemment
 * par le système sur certains appareils, ColorOS notamment).
 *
 * Limite honnête : relève le niveau d'accès requis pour contourner
 * (root ou build de débogage exposant adb backup), sans l'éliminer
 * complètement -- aucune protection purement côté client ne peut
 * garantir mieux sans compteur matériel dédié.
 */
object BruteForceStateStore {

    private const val PREFS_NAME = "bigmfa_security_state"
    private const val KEY_ENCRYPTED_STATE = "encrypted_bruteforce_state"
    private const val KEY_NONCE = "bruteforce_nonce"
    private const val KEYSTORE_ALIAS = "bigmfa_bruteforce_counter_key"

    fun persist(context: Context, state: BruteForceGuard.BruteForceState) {
        if (!KeystoreManager.keyExists(KEYSTORE_ALIAS)) {
            KeystoreManager.generateDeviceBoundKey(KEYSTORE_ALIAS)
        }
        val cipher = KeystoreManager.getEncryptCipher(KEYSTORE_ALIAS)
        val plaintext = "${state.failedAttempts}:${state.lastFailureTimeMillis}".toByteArray(Charsets.UTF_8)
        val ciphertext = cipher.doFinal(plaintext)
        val nonce = cipher.iv

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_ENCRYPTED_STATE, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .putString(KEY_NONCE, Base64.encodeToString(nonce, Base64.NO_WRAP))
            .apply()
    }

    fun load(context: Context): BruteForceGuard.BruteForceState {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val encodedState = prefs.getString(KEY_ENCRYPTED_STATE, null)
        val encodedNonce = prefs.getString(KEY_NONCE, null)
        if (encodedState == null || encodedNonce == null || !KeystoreManager.keyExists(KEYSTORE_ALIAS)) {
            return BruteForceGuard.BruteForceState(failedAttempts = 0, lastFailureTimeMillis = 0)
        }

        return try {
            val ciphertext = Base64.decode(encodedState, Base64.NO_WRAP)
            val nonce = Base64.decode(encodedNonce, Base64.NO_WRAP)
            val cipher = KeystoreManager.getDecryptCipher(KEYSTORE_ALIAS, nonce)
            val plaintext = String(cipher.doFinal(ciphertext), Charsets.UTF_8)
            val parts = plaintext.split(":")
            BruteForceGuard.BruteForceState(
                failedAttempts = parts[0].toInt(),
                lastFailureTimeMillis = parts[1].toLong(),
            )
        } catch (_: Exception) {
            BruteForceGuard.BruteForceState(failedAttempts = 0, lastFailureTimeMillis = 0)
        }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }
}