// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.ui.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * Permet à un ViewModel (qui n'a jamais accès à stringResource(), réservé
 * aux fonctions @Composable) d'exposer un message d'erreur SANS le
 * traduire lui-même -- seul l'écran traduit, au moment de l'affichage,
 * avec la langue active à cet instant précis. Utilisé partout où un
 * ViewModel émet un message destiné à l'utilisateur.
 */
sealed class UiText {
    data class Resource(@StringRes val resId: Int, val args: List<Any> = emptyList()) : UiText()

    @Composable
    fun asString(): String = when (this) {
        is Resource -> stringResource(resId, *args.toTypedArray())
    }
}