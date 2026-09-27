// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.ui.addaccount

import com.bigfortravel.bigmfa.crypto.AesGcmSivCipher
import com.bigfortravel.bigmfa.crypto.Argon2KeyDerivation
import com.bigfortravel.bigmfa.crypto.HkdfDerivation
import com.bigfortravel.bigmfa.crypto.VaultHmac
import com.bigfortravel.bigmfa.vault.VaultContentWriter
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.security.SecureRandom

@OptIn(ExperimentalCoroutinesApi::class)
class AddAccountViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var tempFile: File
    private lateinit var repository: VaultRepository
    private lateinit var writer: VaultContentWriter
    private val password = "MonMotDePasse123!"
    private val tier = Argon2KeyDerivation.MemoryTier.CONTRAINT
    private lateinit var contentKey: ByteArray
    private lateinit var hmacKey: ByteArray

    private fun bytesToHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        tempFile = File.createTempFile("add-account-test", ".json")
        repository = VaultRepository(tempFile)
        writer = VaultContentWriter(repository)

        val salt = Argon2KeyDerivation.generateSalt()
        val derivedKey = Argon2KeyDerivation.derive(password.toCharArray(), salt, tier)
        val masterKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val wrappedMk = AesGcmSivCipher.encrypt(masterKey, derivedKey)
        contentKey = HkdfDerivation.expand(masterKey, "big-mfa-content-v1")
        hmacKey = HkdfDerivation.expand(masterKey, "big-mfa-integrity-v1")

        val emptyContent = AesGcmSivCipher.encrypt(JSONArray().toString().toByteArray(), contentKey)
        val passwordSlot = VaultSlot.PasswordSlot(
            uuid = "slot-1", wrappedKey = bytesToHex(wrappedMk.ciphertext),
            nonce = bytesToHex(wrappedMk.nonce), tag = bytesToHex(wrappedMk.tag),
            salt = bytesToHex(salt), memoryKiB = tier.memoryKiB,
            iterations = tier.iterations, parallelism = tier.parallelism,
        )
        val vaultWithoutHmac = VaultFile(
            createdAt = "2026-08-20T10:00:00Z", modifiedAt = "2026-08-20T10:00:00Z",
            slots = listOf(passwordSlot), content = bytesToHex(emptyContent.ciphertext),
            contentNonce = bytesToHex(emptyContent.nonce), contentTag = bytesToHex(emptyContent.tag),
            vaultHmac = "",
        )
        val mac = VaultHmac.sign(repository.canonicalBytesForHmac(vaultWithoutHmac), hmacKey)
        repository.write(vaultWithoutHmac.copy(vaultHmac = bytesToHex(mac)))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        tempFile.delete()
    }

    // getCurrentAccounts est maintenant une FONCTION -- {} au lieu d'une
    // valeur figée, pour que newViewModel() reflète l'usage réel avec
    // BigMfaNavHost.
    private fun newViewModel(currentAccounts: () -> List<com.bigfortravel.bigmfa.vault.model.VaultAccount> = { emptyList() }) =
        AddAccountViewModel(currentAccounts, contentKey, hmacKey, writer)

    @Test
    fun `nom vide est refuse`() = runTest(testDispatcher) {
        val vm = newViewModel()
        vm.onSaveClicked("", "GitHub", "JBSWY3DPEHPK3PXP", isHotp = false)
        assertTrue(vm.uiState.value is AddAccountViewModel.UiState.Error)
    }

    @Test
    fun `secret vide est refuse`() = runTest(testDispatcher) {
        val vm = newViewModel()
        vm.onSaveClicked("GitHub", "GitHub Inc.", "", isHotp = false)
        assertTrue(vm.uiState.value is AddAccountViewModel.UiState.Error)
    }

    @Test
    fun `secret Base32 invalide est refuse`() = runTest(testDispatcher) {
        val vm = newViewModel()
        vm.onSaveClicked("GitHub", "GitHub Inc.", "ceci n'est pas du base32 !!!", isHotp = false)
        assertTrue(vm.uiState.value is AddAccountViewModel.UiState.Error)
    }

    @Test
    fun `compte TOTP valide est enregistre et relisible via VaultUnlocker`() = runTest(testDispatcher) {
        val vm = newViewModel()
        vm.onSaveClicked("GitHub", "GitHub Inc.", "JBSWY3DPEHPK3PXP", isHotp = false)
        advanceUntilIdle()

        assertTrue(vm.uiState.value is AddAccountViewModel.UiState.Saved)

        val unlocker = VaultUnlocker(repository)
        val result = unlocker.unlock(password.toCharArray()) as VaultUnlocker.UnlockResult.Success
        assertEquals(1, result.accounts.size)
        assertEquals("totp", result.accounts[0].type)
        assertEquals(30, result.accounts[0].period)
    }

    @Test
    fun `compte HOTP valide demarre avec un compteur a 0`() = runTest(testDispatcher) {
        val vm = newViewModel()
        vm.onSaveClicked("ServiceX", "", "KRSXG5CTMVRXEZLU", isHotp = true)
        advanceUntilIdle()

        val unlocker = VaultUnlocker(repository)
        val result = unlocker.unlock(password.toCharArray()) as VaultUnlocker.UnlockResult.Success
        assertEquals("hotp", result.accounts[0].type)
        assertEquals(0L, result.accounts[0].counter)
    }

    @Test
    fun `secret avec espaces et minuscules est normalise avant enregistrement`() = runTest(testDispatcher) {
        val vm = newViewModel()
        vm.onSaveClicked("GitHub", "", "jbsw y3dp ehpk 3pxp", isHotp = false)
        advanceUntilIdle()

        val unlocker = VaultUnlocker(repository)
        val result = unlocker.unlock(password.toCharArray()) as VaultUnlocker.UnlockResult.Success
        assertEquals("JBSWY3DPEHPK3PXP", result.accounts[0].secret)
    }

    /**
     * TEST DE NON-RÉGRESSION -- verrouille précisément le bug réel trouvé
     * sur appareil : un deuxième ajout ne doit JAMAIS effacer le premier.
     * getCurrentAccounts() doit relire la vraie liste à jour au moment de
     * l'appel, pas une valeur figée capturée à la création du ViewModel.
     */
    @Test
    fun `deux ajouts successifs conservent bien les deux comptes, sans effacer le premier`() = runTest(testDispatcher) {
        var liveAccounts = emptyList<com.bigfortravel.bigmfa.vault.model.VaultAccount>()

        val vm1 = newViewModel(currentAccounts = { liveAccounts })
        vm1.onSaveClicked("GitHub", "GitHub Inc.", "JBSWY3DPEHPK3PXP", isHotp = false)
        advanceUntilIdle()
        liveAccounts = (vm1.uiState.value as AddAccountViewModel.UiState.Saved).updatedAccounts

        // Simule un nouveau ViewModel pour le deuxième ajout, comme le
        // fait réellement viewModel { } à chaque nouvelle visite de l'écran.
        val vm2 = newViewModel(currentAccounts = { liveAccounts })
        vm2.onSaveClicked("ServiceX", "", "KRSXG5CTMVRXEZLU", isHotp = true)
        advanceUntilIdle()
        liveAccounts = (vm2.uiState.value as AddAccountViewModel.UiState.Saved).updatedAccounts

        assertEquals(2, liveAccounts.size)

        val unlocker = VaultUnlocker(repository)
        val result = unlocker.unlock(password.toCharArray()) as VaultUnlocker.UnlockResult.Success
        assertEquals(2, result.accounts.size)
        assertTrue(result.accounts.any { it.name == "GitHub" })
        assertTrue(result.accounts.any { it.name == "ServiceX" })
    }
}