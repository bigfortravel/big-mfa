// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.ui.unlock

import com.bigfortravel.bigmfa.crypto.AesGcmSivCipher
import com.bigfortravel.bigmfa.crypto.Argon2KeyDerivation
import com.bigfortravel.bigmfa.crypto.HkdfDerivation
import com.bigfortravel.bigmfa.crypto.VaultHmac
import com.bigfortravel.bigmfa.vault.VaultRepository
import com.bigfortravel.bigmfa.vault.VaultUnlocker
import com.bigfortravel.bigmfa.vault.model.VaultFile
import com.bigfortravel.bigmfa.vault.model.VaultSlot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class UnlockViewModelTest {

    // Un SEUL dispatcher de test, partagé entre setMain() et runTest() --
    // c'est ce partage qui permet à advanceUntilIdle() de vraiment
    // attendre la coroutine lancée par viewModelScope.launch.
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var tempFile: File
    private lateinit var repository: VaultRepository
    private lateinit var viewModel: UnlockViewModel

    private val correctPassword = "MonMotDePasse123!"
    private val tier = Argon2KeyDerivation.MemoryTier.CONTRAINT

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        tempFile = File.createTempFile("unlock-viewmodel-test", ".json")
        repository = VaultRepository(tempFile)
        buildAndWriteValidVault()
        viewModel = UnlockViewModel(VaultUnlocker(repository))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        tempFile.delete()
    }

    private fun bytesToHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    private fun buildAndWriteValidVault() {
        val salt = Argon2KeyDerivation.generateSalt()
        val derivedKey = Argon2KeyDerivation.derive(correctPassword.toCharArray(), salt, tier)
        val masterKey = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        val wrappedMk = AesGcmSivCipher.encrypt(masterKey, derivedKey)
        val contentKey = HkdfDerivation.expand(masterKey, "big-mfa-content-v1")
        val hmacKey = HkdfDerivation.expand(masterKey, "big-mfa-integrity-v1")

        val accountsJson = JSONArray().apply {
            put(JSONObject().apply {
                put("id", "acc-1"); put("type", "totp"); put("name", "GitHub")
                put("issuer", "GitHub Inc."); put("secret", "JBSWY3DPEHPK3PXP")
                put("algorithm", "SHA-1"); put("digits", 6); put("period", 30)
            })
        }
        val encryptedContent = AesGcmSivCipher.encrypt(accountsJson.toString().toByteArray(), contentKey)

        val passwordSlot = VaultSlot.PasswordSlot(
            uuid = "slot-1", wrappedKey = bytesToHex(wrappedMk.ciphertext),
            nonce = bytesToHex(wrappedMk.nonce), tag = bytesToHex(wrappedMk.tag),
            salt = bytesToHex(salt), memoryKiB = tier.memoryKiB,
            iterations = tier.iterations, parallelism = tier.parallelism,
        )
        val vaultWithoutHmac = VaultFile(
            createdAt = "2026-08-20T10:00:00Z", modifiedAt = "2026-08-20T10:00:00Z",
            slots = listOf(passwordSlot), content = bytesToHex(encryptedContent.ciphertext),
            contentNonce = bytesToHex(encryptedContent.nonce), contentTag = bytesToHex(encryptedContent.tag),
            vaultHmac = "",
        )
        val mac = VaultHmac.sign(repository.canonicalBytesForHmac(vaultWithoutHmac), hmacKey)
        repository.write(vaultWithoutHmac.copy(vaultHmac = bytesToHex(mac)))
    }

    @Test
    fun `etat initial est Idle`() = runTest(testDispatcher) {
        assertTrue(viewModel.uiState.value is UnlockViewModel.UiState.Idle)
    }

    @Test
    fun `bon mot de passe mene a l'etat Unlocked`() = runTest(testDispatcher) {
        viewModel.onUnlockClicked(correctPassword.toCharArray())
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is UnlockViewModel.UiState.Unlocked)
    }

    @Test
    fun `mauvais mot de passe mene a l'etat Error avec message generique`() = runTest(testDispatcher) {
        viewModel.onUnlockClicked("MauvaisMotDePasse999!".toCharArray())
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertTrue(state is UnlockViewModel.UiState.Error)
        assertTrue((state as UnlockViewModel.UiState.Error).message == "Mot de passe incorrect")
    }

    @Test
    fun `resetError remet Idle depuis Error, mais pas depuis Unlocked`() = runTest(testDispatcher) {
        viewModel.onUnlockClicked("MauvaisMotDePasse999!".toCharArray())
        advanceUntilIdle()
        viewModel.resetError()
        assertTrue(viewModel.uiState.value is UnlockViewModel.UiState.Idle)
    }
}