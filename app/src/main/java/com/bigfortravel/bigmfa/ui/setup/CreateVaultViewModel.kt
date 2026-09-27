// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bigfortravel.bigmfa.R
import com.bigfortravel.bigmfa.crypto.AesGcmSivCipher
import com.bigfortravel.bigmfa.crypto.Argon2KeyDerivation
import com.bigfortravel.bigmfa.crypto.HkdfDerivation
import com.bigfortravel.bigmfa.crypto.VaultHmac
import com.bigfortravel.bigmfa.ui.common.UiText
import com.bigfortravel.bigmfa.vault.BackupImporter
import com.bigfortravel.bigmfa.vault.VaultRepository
import com.bigfortravel.bigmfa.vault.VaultUnlocker
import com.bigfortravel.bigmfa.vault.model.VaultAccount
import com.bigfortravel.bigmfa.vault.model.VaultFile
import com.bigfortravel.bigmfa.vault.model.VaultSlot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class CreateVaultViewModel(
    private val repository: VaultRepository,
    private val memoryTier: Argon2KeyDerivation.MemoryTier,
) : ViewModel() {

    sealed class UiState {
        object Idle : UiState()
        object Loading : UiState()
        data class Error(val message: UiText) : UiState()
        data class Created(val session: VaultUnlocker.UnlockResult.Success) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun onCreateClicked(password: CharArray, confirmPassword: CharArray) {
        if (password.size < 12) {
            _uiState.value = UiState.Error(UiText.Resource(R.string.create_vault_password_too_short))
            return
        }
        if (!password.contentEquals(confirmPassword)) {
            _uiState.value = UiState.Error(UiText.Resource(R.string.create_vault_passwords_mismatch))
            return
        }

        _uiState.value = UiState.Loading

        viewModelScope.launch {
            try {
                createNewVaultFile(password, emptyList())
                val unlocker = VaultUnlocker(repository)
                val result = unlocker.unlock(password)
                if (result is VaultUnlocker.UnlockResult.Success) {
                    _uiState.value = UiState.Created(result)
                } else {
                    _uiState.value = UiState.Error(UiText.Resource(R.string.create_vault_creation_error))
                }
            } catch (_: Exception) {
                _uiState.value = UiState.Error(UiText.Resource(R.string.create_vault_creation_error))
            } finally {
                password.fill('\u0000')
                confirmPassword.fill('\u0000')
            }
        }
    }

    fun onRestoreClicked(
        backupFileContent: String,
        backupPassword: CharArray,
        newMasterPassword: CharArray,
        confirmNewMasterPassword: CharArray,
    ) {
        if (newMasterPassword.size < 12) {
            _uiState.value = UiState.Error(UiText.Resource(R.string.restore_vault_new_password_too_short))
            return
        }
        if (!newMasterPassword.contentEquals(confirmNewMasterPassword)) {
            _uiState.value = UiState.Error(UiText.Resource(R.string.restore_vault_new_passwords_mismatch))
            return
        }

        _uiState.value = UiState.Loading

        viewModelScope.launch {
            try {
                val importer = BackupImporter()
                val restoredAccounts = importer.importAndMerge(backupFileContent, backupPassword, emptyList())

                createNewVaultFile(newMasterPassword, restoredAccounts)

                val unlocker = VaultUnlocker(repository)
                val result = unlocker.unlock(newMasterPassword)
                if (result is VaultUnlocker.UnlockResult.Success) {
                    _uiState.value = UiState.Created(result)
                } else {
                    _uiState.value = UiState.Error(UiText.Resource(R.string.create_vault_creation_error))
                }
            } catch (e: BackupImporter.ImportException) {
                _uiState.value = UiState.Error(e.uiMessage)
            } catch (_: Exception) {
                _uiState.value = UiState.Error(UiText.Resource(R.string.restore_vault_generic_error))
            } finally {
                newMasterPassword.fill('\u0000')
                confirmNewMasterPassword.fill('\u0000')
            }
        }
    }

    private fun createNewVaultFile(masterPassword: CharArray, accounts: List<VaultAccount>) {
        val salt = Argon2KeyDerivation.generateSalt()
        val derivedKey = Argon2KeyDerivation.derive(masterPassword.copyOf(), salt, memoryTier)

        val masterKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val wrappedMk = AesGcmSivCipher.encrypt(masterKey, derivedKey)

        val contentKey = HkdfDerivation.expand(masterKey, "big-mfa-content-v1")
        val hmacKey = HkdfDerivation.expand(masterKey, "big-mfa-integrity-v1")

        val accountsJson = JSONArray().apply {
            for (acc in accounts) {
                put(
                    org.json.JSONObject().apply {
                        put("id", acc.id)
                        put("type", acc.type)
                        put("name", acc.name)
                        put("issuer", acc.issuer)
                        put("secret", acc.secret)
                        put("algorithm", acc.algorithm)
                        put("digits", acc.digits)
                        acc.period?.let { put("period", it) }
                        acc.counter?.let { put("counter", it) }
                    },
                )
            }
        }
        val encryptedContent = AesGcmSivCipher.encrypt(accountsJson.toString().toByteArray(), contentKey)

        val passwordSlot = VaultSlot.PasswordSlot(
            uuid = UUID.randomUUID().toString(),
            wrappedKey = bytesToHex(wrappedMk.ciphertext),
            nonce = bytesToHex(wrappedMk.nonce),
            tag = bytesToHex(wrappedMk.tag),
            salt = bytesToHex(salt),
            memoryKiB = memoryTier.memoryKiB,
            iterations = memoryTier.iterations,
            parallelism = memoryTier.parallelism,
        )

        val now = currentIsoTimestamp()
        val vaultWithoutHmac = VaultFile(
            createdAt = now,
            modifiedAt = now,
            slots = listOf(passwordSlot),
            content = bytesToHex(encryptedContent.ciphertext),
            contentNonce = bytesToHex(encryptedContent.nonce),
            contentTag = bytesToHex(encryptedContent.tag),
            vaultHmac = "",
        )

        val mac = VaultHmac.sign(repository.canonicalBytesForHmac(vaultWithoutHmac), hmacKey)
        repository.write(vaultWithoutHmac.copy(vaultHmac = bytesToHex(mac)))
    }

    fun resetError() {
        if (_uiState.value is UiState.Error) {
            _uiState.value = UiState.Idle
        }
    }

    fun lock() {
        _uiState.value = UiState.Idle
    }

    private fun bytesToHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    private fun currentIsoTimestamp(): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date())
    }
}