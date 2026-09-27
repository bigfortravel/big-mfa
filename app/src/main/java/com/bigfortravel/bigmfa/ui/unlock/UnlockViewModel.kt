// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.ui.unlock

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bigfortravel.bigmfa.R
import com.bigfortravel.bigmfa.keystore.BiometricAuthenticator
import com.bigfortravel.bigmfa.keystore.KeystoreManager
import com.bigfortravel.bigmfa.security.BruteForceGuard
import com.bigfortravel.bigmfa.security.BruteForceStateStore
import com.bigfortravel.bigmfa.ui.common.UiText
import com.bigfortravel.bigmfa.vault.VaultUnlocker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class UnlockViewModel(
    private val unlocker: VaultUnlocker,
    private val appContext: android.content.Context,
) : ViewModel() {

    sealed class UiState {
        object Idle : UiState()
        object Loading : UiState()
        data class Error(val message: UiText) : UiState()
        data class Unlocked(val session: VaultUnlocker.UnlockResult.Success) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var bruteForceState = BruteForceStateStore.load(appContext)
    private val _failedAttempts = MutableStateFlow(0)
    val failedAttempts: StateFlow<Int> = _failedAttempts.asStateFlow()

    val isBiometricAvailable: Boolean
        get() = unlocker.hasBiometricSlot()

    fun onUnlockClicked(password: CharArray) {
        val now = System.currentTimeMillis()
        val check = BruteForceGuard.check(bruteForceState, now)
        if (check is BruteForceGuard.CheckResult.Blocked) {
            _uiState.value = UiState.Error(
                UiText.Resource(R.string.unlock_error_too_many_attempts, listOf(check.remainingSeconds)),
            )
            return
        }

        _uiState.value = UiState.Loading

        viewModelScope.launch {
            when (val result = unlocker.unlock(password)) {
                is VaultUnlocker.UnlockResult.Success -> {
                    bruteForceState = BruteForceGuard.recordSuccess()
                    BruteForceStateStore.persist(appContext, bruteForceState)
                    _failedAttempts.value = 0
                    _uiState.value = UiState.Unlocked(result)
                }
                else -> {
                    bruteForceState = BruteForceGuard.recordFailure(bruteForceState, System.currentTimeMillis())
                    BruteForceStateStore.persist(appContext, bruteForceState)
                    _failedAttempts.value = bruteForceState.failedAttempts
                    _uiState.value = UiState.Error(genericErrorMessage(result))
                }
            }
        }
    }

    fun onBiometricUnlockClicked(activity: FragmentActivity) {
        val info = unlocker.getBiometricSlotInfo() ?: return
        val cipher = try {
            KeystoreManager.getDecryptCipher(info.alias, info.nonce)
        } catch (_: Exception) {
            _uiState.value = UiState.Error(UiText.Resource(R.string.unlock_error_biometric_unavailable))
            return
        }

        val authenticator = BiometricAuthenticator(activity)
        authenticator.authenticate(cipher) { authResult ->
            when (authResult) {
                is BiometricAuthenticator.AuthResult.Success -> {
                    viewModelScope.launch {
                        when (val result = unlocker.unlockWithBiometric(authResult.cipher)) {
                            is VaultUnlocker.UnlockResult.Success -> {
                                _uiState.value = UiState.Unlocked(result)
                            }
                            else -> {
                                _uiState.value = UiState.Error(genericErrorMessage(result))
                            }
                        }
                    }
                }
                is BiometricAuthenticator.AuthResult.Error -> {
                    if (!authResult.message.contains("annul", ignoreCase = true) &&
                        !authResult.message.contains("cancel", ignoreCase = true)
                    ) {
                        _uiState.value = UiState.Error(UiText.Resource(R.string.unlock_error_biometric_failed))
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

    private fun genericErrorMessage(result: VaultUnlocker.UnlockResult): UiText = when (result) {
        is VaultUnlocker.UnlockResult.VaultDataCorrupted -> UiText.Resource(R.string.unlock_error_corrupted_vault)
        is VaultUnlocker.UnlockResult.NoPasswordSlotFound -> UiText.Resource(R.string.unlock_error_no_vault)
        else -> UiText.Resource(R.string.unlock_error_wrong_password)
    }

    fun resetError() {
        if (_uiState.value is UiState.Error) {
            _uiState.value = UiState.Idle
        }
    }

    fun lock() {
        _uiState.value = UiState.Idle
    }

    fun resetVault() {
        unlocker.resetVault()
        BruteForceStateStore.clear(appContext)
        bruteForceState = BruteForceGuard.BruteForceState(failedAttempts = 0, lastFailureTimeMillis = 0)
        _uiState.value = UiState.Idle
        _failedAttempts.value = 0
    }
}