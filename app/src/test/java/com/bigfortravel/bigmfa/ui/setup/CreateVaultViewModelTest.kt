// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.ui.setup

import com.bigfortravel.bigmfa.crypto.Argon2KeyDerivation
import com.bigfortravel.bigmfa.vault.VaultRepository
import com.bigfortravel.bigmfa.vault.VaultUnlocker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class CreateVaultViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var tempFile: File
    private lateinit var repository: VaultRepository
    private lateinit var viewModel: CreateVaultViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        tempFile = File.createTempFile("create-vault-test", ".json")
        tempFile.delete() // on veut un fichier qui n'existe pas encore, comme un vrai premier lancement
        repository = VaultRepository(tempFile)
        viewModel = CreateVaultViewModel(repository, Argon2KeyDerivation.MemoryTier.CONTRAINT)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        tempFile.delete()
    }

    @Test
    fun `mot de passe trop court est refuse avant toute derivation`() = runTest(testDispatcher) {
        viewModel.onCreateClicked("court".toCharArray(), "court".toCharArray())
        assertTrue(viewModel.uiState.value is CreateVaultViewModel.UiState.Error)
        assertTrue(!repository.exists())
    }

    @Test
    fun `mots de passe qui ne correspondent pas sont refuses`() = runTest(testDispatcher) {
        viewModel.onCreateClicked("MonMotDePasse123!".toCharArray(), "AutreMotDePasse456!".toCharArray())
        val state = viewModel.uiState.value
        assertTrue(state is CreateVaultViewModel.UiState.Error)
        assertEquals(
            "Les mots de passe ne correspondent pas",
            (state as CreateVaultViewModel.UiState.Error).message,
        )
    }

    @Test
    fun `creation reussie ecrit bien un coffre valide et deverrouillable`() = runTest(testDispatcher) {
        val password = "MonMotDePasse123!"
        viewModel.onCreateClicked(password.toCharArray(), password.toCharArray())
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is CreateVaultViewModel.UiState.Created)
        assertTrue(repository.exists())

        // Le coffre fraîchement créé doit être réellement déverrouillable.
        val unlocker = VaultUnlocker(repository)
        val result = unlocker.unlock(password.toCharArray())
        assertTrue(result is VaultUnlocker.UnlockResult.Success)
    }

    @Test
    fun `coffre nouvellement cree contient une liste de comptes vide`() = runTest(testDispatcher) {
        val password = "MonMotDePasse123!"
        viewModel.onCreateClicked(password.toCharArray(), password.toCharArray())
        advanceUntilIdle()

        val unlocker = VaultUnlocker(repository)
        val result = unlocker.unlock(password.toCharArray()) as VaultUnlocker.UnlockResult.Success
        assertEquals(0, result.accounts.size)
    }

    @Test
    fun `resetError remet Idle depuis Error`() = runTest(testDispatcher) {
        viewModel.onCreateClicked("court".toCharArray(), "court".toCharArray())
        viewModel.resetError()
        assertTrue(viewModel.uiState.value is CreateVaultViewModel.UiState.Idle)
    }
}