// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.ui.settings

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bigfortravel.bigmfa.R
import com.bigfortravel.bigmfa.keystore.BiometricAuthenticator
import com.bigfortravel.bigmfa.keystore.HardwareSecurityChecker
import com.bigfortravel.bigmfa.keystore.KeystoreManager
import com.bigfortravel.bigmfa.ui.common.UiText
import com.bigfortravel.bigmfa.vault.SlotManager
import com.bigfortravel.bigmfa.vault.VaultUnlocker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class BiometricSetupViewModel(
    private val unlocker: VaultUnlocker,
    private val slotManager: SlotManager,
    private val currentSession: VaultUnlocker.UnlockResult.Success,
) : ViewModel() {

    sealed class UiState {
        object Idle : UiState()
        object AwaitingBiometricPrompt : UiState()
        object Loading : UiState()
        data class Error(val message: UiText) : UiState()
        object Enabled : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _isBiometricEnabled = MutableStateFlow(unlocker.hasBiometricSlot())
    val isBiometricEnabled: StateFlow<Boolean> = _isBiometricEnabled.asStateFlow()

    fun checkHardwareEligibility(): Boolean {
        val level = HardwareSecurityChecker.detectSecurityLevel()
        return HardwareSecurityChecker.meetsMinimumRequirement(level)
    }

    fun onConfirmPassword(password: CharArray) {
        _uiState.value = UiState.Loading

        viewModelScope.launch {
            val confirmResult = unlocker.unlock(password)
            _uiState.value = if (confirmResult is VaultUnlocker.UnlockResult.Success) {
                UiState.AwaitingBiometricPrompt
            } else {
                UiState.Error(UiText.Resource(R.string.biometric_wrong_password))
            }
        }
    }

    fun onBiometricPromptRequested(activity: FragmentActivity) {
        val alias = "big-mfa-biometric-${UUID.randomUUID()}"
        try {
            KeystoreManager.generateBiometricBoundKey(alias)
        } catch (_: Exception) {
            _uiState.value = UiState.Error(UiText.Resource(R.string.biometric_key_creation_error))
            return
        }

        val encryptCipher = try {
            KeystoreManager.getEncryptCipher(alias)
        } catch (_: Exception) {
            KeystoreManager.deleteKey(alias)
            _uiState.value = UiState.Error(UiText.Resource(R.string.biometric_cipher_error))
            return
        }

        val authenticator = BiometricAuthenticator(activity)
        authenticator.authenticate(encryptCipher) { authResult ->
            when (authResult) {
                is BiometricAuthenticator.AuthResult.Success -> {
                    try {
                        slotManager.addBiometricSlot(
                            masterKey = currentSession.masterKey,
                            hmacKey = currentSession.hmacKey,
                            encryptCipher = authResult.cipher,
                            keystoreAlias = alias,
                        )
                        _isBiometricEnabled.value = true
                        _uiState.value = UiState.Enabled
                    } catch (_: Exception) {
                        KeystoreManager.deleteKey(alias)
                        _uiState.value = UiState.Error(UiText.Resource(R.string.biometric_enable_error))
                    }
                }
                is BiometricAuthenticator.AuthResult.Error -> {
                    KeystoreManager.deleteKey(alias)
                    if (!authResult.message.contains("annul", ignoreCase = true) &&
                        !authResult.message.contains("cancel", ignoreCase = true)
                    ) {
                        _uiState.value = UiState.Error(UiText.Resource(R.string.biometric_auth_failed))
                    } else {
                        _uiState.value = UiState.Idle
                    }
                }
                is BiometricAuthenticator.AuthResult.Failed -> {
                    // Le prompt système reste ouvert, rien à faire ici.
                }
            }
        }
    }

    fun disableBiometric() {
        slotManager.removeBiometricSlot(currentSession.hmacKey)
        _isBiometricEnabled.value = false
        _uiState.value = UiState.Idle
    }

    fun resetError() {
        if (_uiState.value is UiState.Error) {
            _uiState.value = UiState.Idle
        }
    }
}