// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.security

/**
 * Détermine si le coffre doit se reverrouiller automatiquement après une
 * période d'inactivité — "Délai d'inactivité : 2 min" déjà affiché sur
 * l'écran État de sécurité côté Chrome.
 *
 * Calcule seulement — ne gère pas de vrai minuteur en tâche de fond (ça
 * viendra avec de vraies API de cycle de vie Android, à l'intégration
 * avec l'écran principal). Reste testable en JVM pur.
 */
object AutoLockManager {

    const val DEFAULT_INACTIVITY_TIMEOUT_SECONDS = 120L // 2 minutes

    /**
     * @param lastActivityTimeMillis Horodatage de la dernière action
     *   utilisateur connue (frappe, tap, navigation dans l'app).
     * @param nowMillis Horodatage actuel.
     * @param timeoutSeconds Délai avant verrouillage — paramétrable pour
     *   un futur réglage utilisateur, 120s par défaut.
     */
    fun shouldLock(
        lastActivityTimeMillis: Long,
        nowMillis: Long,
        timeoutSeconds: Long = DEFAULT_INACTIVITY_TIMEOUT_SECONDS,
    ): Boolean {
        val elapsedSeconds = (nowMillis - lastActivityTimeMillis) / 1000
        return elapsedSeconds >= timeoutSeconds
    }

    /** Secondes restantes avant verrouillage automatique — utile pour un
     *  éventuel indicateur visuel, jamais négatif. */
    fun secondsRemainingBeforeLock(
        lastActivityTimeMillis: Long,
        nowMillis: Long,
        timeoutSeconds: Long = DEFAULT_INACTIVITY_TIMEOUT_SECONDS,
    ): Long {
        val elapsedSeconds = (nowMillis - lastActivityTimeMillis) / 1000
        val remaining = timeoutSeconds - elapsedSeconds
        return remaining.coerceAtLeast(0)
    }
}