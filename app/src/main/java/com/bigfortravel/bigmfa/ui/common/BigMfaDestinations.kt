// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.ui.common

import androidx.annotation.StringRes
import com.bigfortravel.bigmfa.R

/**
 * Les 7 destinations principales de l'app — IDENTIQUES à celles de
 * l'extension Chrome (mêmes noms, même ordre), pour qu'un utilisateur
 * habitué à l'une ne soit jamais perdu sur l'autre.
 *
 * labelResId au lieu d'une String fixe -- une enum ne peut jamais
 * appeler stringResource() (pas un contexte @Composable), donc la
 * traduction n'est résolue qu'au moment de l'affichage, dans
 * BigMfaNavHost.kt.
 */
enum class BigMfaDestination(val route: String, @StringRes val labelResId: Int, val icon: String) {
    ACCOUNTS(route = "com/bigfortravel/bigmfa/ui/accounts", labelResId = R.string.nav_accounts, icon = "🔐"),
    DECODER(route = "com/bigfortravel/bigmfa/ui/decoder", labelResId = R.string.nav_decoder, icon = "🔍"),
    GENERATE_KEY(route = "generate_key", labelResId = R.string.nav_key, icon = "🔑"),
    ADD_ACCOUNT(route = "add_account", labelResId = R.string.nav_add, icon = "➕"),
    GUIDE(route = "com/bigfortravel/bigmfa/ui/guide", labelResId = R.string.nav_guide, icon = "📖"),
    SUPPORT(route = "com/bigfortravel/bigmfa/ui/support", labelResId = R.string.nav_support, icon = "❤️"),
    SETTINGS(route = "com/bigfortravel/bigmfa/ui/settings", labelResId = R.string.nav_settings, icon = "⚙️"),
}