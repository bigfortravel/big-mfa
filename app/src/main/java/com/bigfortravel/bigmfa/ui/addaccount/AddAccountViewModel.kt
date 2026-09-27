// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.ui.addaccount

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bigfortravel.bigmfa.R
import com.bigfortravel.bigmfa.ui.common.UiText
import com.bigfortravel.bigmfa.vault.VaultContentWriter
import com.bigfortravel.bigmfa.vault.model.VaultAccount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.bouncycastle.util.encoders.Base32
import java.util.UUID

class AddAccountViewModel(
    private val getCurrentAccounts: () -> List<VaultAccount>,
    private val contentKey: ByteArray,
    private val hmacKey: ByteArray,
    private val writer: VaultContentWriter,
) : ViewModel() {

    sealed class UiState {
        object Idle : UiState()
        object Loading : UiState()
        data class Error(val message: UiText) : UiState()
        data class Saved(val updatedAccounts: List<VaultAccount>) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun onSaveClicked(
        name: String,
        issuer: String,
        secret: String,
        isHotp: Boolean,
        algorithm: String = "SHA-1",
        digits: Int = 6,
        period: Int = 30,
    ) {
        val trimmedName = name.trim()
        val trimmedSecret = secret.trim().uppercase().replace(" ", "")

        if (trimmedName.isEmpty()) {
            _uiState.value = UiState.Error(UiText.Resource(R.string.add_account_name_required))
            return
        }
        if (trimmedSecret.isEmpty()) {
            _uiState.value = UiState.Error(UiText.Resource(R.string.add_account_secret_required))
            return
        }
        try {
            Base32.decode(trimmedSecret.toByteArray(Charsets.US_ASCII))
        } catch (_: Exception) {
            _uiState.value = UiState.Error(UiText.Resource(R.string.add_account_secret_invalid))
            return
        }

        _uiState.value = UiState.Loading

        viewModelScope.launch {
            try {
                val newAccount = VaultAccount(
                    id = UUID.randomUUID().toString(),
                    type = if (isHotp) "hotp" else "totp",
                    name = trimmedName,
                    issuer = issuer.trim(),
                    secret = trimmedSecret,
                    algorithm = algorithm,
                    digits = digits,
                    period = if (isHotp) null else period,
                    counter = if (isHotp) 0L else null,
                )
                val updatedAccounts = getCurrentAccounts() + newAccount
                writer.writeAccounts(updatedAccounts, contentKey, hmacKey)
                _uiState.value = UiState.Saved(updatedAccounts)
            } catch (_: Exception) {
                _uiState.value = UiState.Error(UiText.Resource(R.string.add_account_save_error))
            }
        }
    }

    fun resetError() {
        if (_uiState.value is UiState.Error) {
            _uiState.value = UiState.Idle
        }
    }

    fun consumeSavedState() {
        if (_uiState.value is UiState.Saved) {
            _uiState.value = UiState.Idle
        }
    }
}