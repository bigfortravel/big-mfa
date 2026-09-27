
// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.security

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Observe le cycle de vie global du processus (ProcessLifecycleOwner)
 * pour détecter les passages en arrière-plan/premier plan de l'app dans
 * son ensemble -- pas d'un seul écran. Enregistre l'horodatage de mise
 * en arrière-plan ; AutoLockManager.shouldLock() compare cet horodatage
 * au moment du retour au premier plan pour décider s'il faut reverrouiller.
 *
 * Note : ce mécanisme protège contre l'inactivité APRÈS que l'app ait
 * quitté le premier plan (changement d'app, écran verrouillé). Il ne
 * détecte pas l'inactivité alors que l'app reste visible à l'écran sans
 * interaction -- cas plus rare, hors périmètre de cette correction.
 */
object AppLifecycleObserver : DefaultLifecycleObserver {

    var backgroundedAtMillis: Long? = null
        private set

    override fun onStop(owner: LifecycleOwner) {
        backgroundedAtMillis = System.currentTimeMillis()
    }

    override fun onStart(owner: LifecycleOwner) {
        // L'horodatage n'est PAS effacé ici -- c'est à l'appelant
        // (BigMfaRoot) de le lire, décider, puis appeler reset()
        // explicitement après avoir agi dessus.
    }

    fun reset() {
        backgroundedAtMillis = null
    }
}