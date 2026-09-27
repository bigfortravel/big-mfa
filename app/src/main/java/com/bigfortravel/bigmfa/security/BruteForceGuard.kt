// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.security

/**
 * Logique anti-bruteforce — verrouillage exponentiel après un certain
 * nombre d'essais échoués. Reçoit l'état actuel (compteur, horodatage du
 * dernier échec) plutôt que de le stocker lui-même, pour rester testable
 * en JVM pur — la vraie persistance (SharedPreferences) sera branchée
 * plus tard, à l'intégration avec l'écran de déverrouillage.
 *
 * Palier identique à celui déjà affiché côté Chrome sur l'écran "État de
 * sécurité" : 5 essais avant le premier blocage, puis délai exponentiel.
 */
object BruteForceGuard {

    private const val MAX_ATTEMPTS_BEFORE_LOCKOUT = 5
    private const val BASE_LOCKOUT_SECONDS = 30L

    data class BruteForceState(
        val failedAttempts: Int,
        val lastFailureTimeMillis: Long,
    )

    sealed class CheckResult {
        object Allowed : CheckResult()
        data class Blocked(val remainingSeconds: Long) : CheckResult()
    }

    /**
     * Vérifie si une tentative de déverrouillage est autorisée MAINTENANT,
     * compte tenu de l'état actuel et de l'heure actuelle.
     */
    fun check(state: BruteForceState, nowMillis: Long): CheckResult {
        if (state.failedAttempts < MAX_ATTEMPTS_BEFORE_LOCKOUT) {
            return CheckResult.Allowed
        }

        val excessAttempts = state.failedAttempts - MAX_ATTEMPTS_BEFORE_LOCKOUT
        val lockoutSeconds = BASE_LOCKOUT_SECONDS * (1L shl excessAttempts.coerceAtMost(10))
        val unlockAtMillis = state.lastFailureTimeMillis + lockoutSeconds * 1000

        val remainingMillis = unlockAtMillis - nowMillis
        return if (remainingMillis <= 0) {
            CheckResult.Allowed
        } else {
            CheckResult.Blocked(remainingSeconds = (remainingMillis / 1000) + 1)
        }
    }

    /** Nouvel état après un échec de mot de passe. */
    fun recordFailure(state: BruteForceState, nowMillis: Long): BruteForceState =
        state.copy(failedAttempts = state.failedAttempts + 1, lastFailureTimeMillis = nowMillis)

    /** Nouvel état après un déverrouillage réussi — remise à zéro complète. */
    fun recordSuccess(): BruteForceState = BruteForceState(failedAttempts = 0, lastFailureTimeMillis = 0)
}