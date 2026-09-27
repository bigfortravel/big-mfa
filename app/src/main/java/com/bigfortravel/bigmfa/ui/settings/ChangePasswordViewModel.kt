// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bigfortravel.bigmfa.R
import com.bigfortravel.bigmfa.ui.common.UiText
import com.bigfortravel.bigmfa.vault.VaultUnlocker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChangePasswordViewModel(private val unlocker: VaultUnlocker) : ViewModel() {

    sealed class UiState {
        object Idle : UiState()
        object Loading : UiState()
        data class Error(val message: UiText) : UiState()
        object Success : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun onChangePasswordClicked(currentPassword: CharArray, newPassword: CharArray, confirmPassword: CharArray) {
        if (newPassword.size < 12) {
            _uiState.value = UiState.Error(UiText.Resource(R.string.change_password_new_too_short))
            return
        }
        if (!newPassword.contentEquals(confirmPassword)) {
            _uiState.value = UiState.Error(UiText.Resource(R.string.change_password_new_mismatch))
            return
        }

        _uiState.value = UiState.Loading

        viewModelScope.launch {
            when (unlocker.changeMasterPassword(currentPassword, newPassword)) {
                is VaultUnlocker.ChangePasswordResult.Success -> {
                    _uiState.value = UiState.Success
                }
                is VaultUnlocker.ChangePasswordResult.WrongCurrentPassword -> {
                    _uiState.value = UiState.Error(UiText.Resource(R.string.change_password_wrong_current))
                }
                is VaultUnlocker.ChangePasswordResult.Failed -> {
                    _uiState.value = UiState.Error(UiText.Resource(R.string.change_password_generic_error))
                }
            }
            confirmPassword.fill('\u0000')
        }
    }

    fun resetError() {
        if (_uiState.value is UiState.Error) {
            _uiState.value = UiState.Idle
        }
    }
}