// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.keystore

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import javax.crypto.Cipher

/**
 * Encapsule androidx.biometric.BiometricPrompt, lié à un Cipher via
 * CryptoObject — la biométrie conditionne littéralement l'usage du
 * Cipher au niveau du système d'exploitation, ce n'est jamais un simple
 * portail UX devant une clé qui existerait déjà en clair quelque part.
 */
class BiometricAuthenticator(private val activity: FragmentActivity) {

    sealed class AuthResult {
        data class Success(val cipher: Cipher) : AuthResult()
        data class Error(val message: String) : AuthResult()
        object Failed : AuthResult()
    }

    /** À vérifier AVANT de proposer l'option biométrique dans l'UI —
     *  ex. capteur absent, ou aucune empreinte enregistrée sur l'appareil. */
    fun isAvailable(): Boolean {
        val biometricManager = BiometricManager.from(activity)
        return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
                BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Déclenche le vrai prompt biométrique système. onResult est appelé
     * de façon asynchrone, une fois que l'utilisateur a interagi
     * (succès, erreur, ou échec de reconnaissance).
     */
    fun authenticate(cipher: Cipher, onResult: (AuthResult) -> Unit) {
        val executor = ContextCompat.getMainExecutor(activity)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                val authenticatedCipher = result.cryptoObject?.cipher
                if (authenticatedCipher != null) {
                    onResult(AuthResult.Success(authenticatedCipher))
                } else {
                    onResult(AuthResult.Error("Cipher manquant après authentification"))
                }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                onResult(AuthResult.Error(errString.toString()))
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onResult(AuthResult.Failed)
            }
        }

        val biometricPrompt = BiometricPrompt(activity, executor, callback)

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Déverrouiller Big MFA")
            .setSubtitle("Authentification biométrique requise")
            .setNegativeButtonText("Annuler")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()

        biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
    }
}