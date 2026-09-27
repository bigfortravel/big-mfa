// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.autofill

import com.bigfortravel.bigmfa.vault.VaultUnlocker

/**
 * Pont en mémoire entre l'activité principale (où le coffre est
 * déverrouillé) et le service Autofill (qui tourne dans un contexte
 * séparé, sans accès direct à cette session).
 *
 * Sécurité : ne persiste JAMAIS sur disque, uniquement en RAM tant que
 * le processus de l'app vit. Effacé explicitement au verrouillage.
 */
object AutofillSessionHolder {
    @Volatile
    var currentSession: VaultUnlocker.UnlockResult.Success? = null

    fun clear() {
        currentSession = null
    }
}