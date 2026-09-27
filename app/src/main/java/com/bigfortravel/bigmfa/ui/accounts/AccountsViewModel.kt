// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.ui.accounts

import androidx.lifecycle.ViewModel
import com.bigfortravel.bigmfa.vault.VaultUnlocker
import com.bigfortravel.bigmfa.vault.model.VaultAccount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Version volontairement simple pour cette première itération : affiche
 * la liste des comptes telle que reçue au déverrouillage, SANS
 * rafraîchissement automatique des codes TOTP pour l'instant -- viendra
 * dans une prochaine étape, une fois cette base validée.
 */
class AccountsViewModel(session: VaultUnlocker.UnlockResult.Success) : ViewModel() {

    private val _accounts = MutableStateFlow<List<VaultAccount>>(session.accounts)
    val accounts: StateFlow<List<VaultAccount>> = _accounts.asStateFlow()
}