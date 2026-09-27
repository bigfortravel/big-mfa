// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa

import android.os.Bundle
import android.view.WindowManager
import androidx.lifecycle.ProcessLifecycleOwner
import com.bigfortravel.bigmfa.security.AppLifecycleObserver
import com.bigfortravel.bigmfa.security.AutoLockManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.bigfortravel.bigmfa.crypto.Argon2KeyDerivation
import com.bigfortravel.bigmfa.ui.common.BigMfaNavHost
import com.bigfortravel.bigmfa.ui.setup.CreateVaultScreen
import com.bigfortravel.bigmfa.ui.setup.CreateVaultViewModel
import com.bigfortravel.bigmfa.ui.theme.BigMFATheme
import com.bigfortravel.bigmfa.ui.unlock.UnlockScreen
import com.bigfortravel.bigmfa.ui.unlock.UnlockViewModel
import com.bigfortravel.bigmfa.vault.VaultRepository
import com.bigfortravel.bigmfa.vault.VaultUnlocker
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var repository: VaultRepository

    private val unlockViewModel: UnlockViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return UnlockViewModel(VaultUnlocker(repository), applicationContext) as T
            }
        }
    }

    private val createVaultViewModel: CreateVaultViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val tier = Argon2KeyDerivation.detectTierForThisDevice(Runtime.getRuntime().maxMemory())
                return CreateVaultViewModel(repository, tier) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Empêche captures d'écran, enregistrement, et affichage du
        // contenu réel dans la vignette du sélecteur d'apps récentes --
        // pertinent sur tout l'écran de l'app (codes TOTP, coffre).
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )

        enableEdgeToEdge()

        val vaultFile = File(filesDir, "vault.json")
        ProcessLifecycleOwner.get().lifecycle.addObserver(AppLifecycleObserver)
        repository = VaultRepository(vaultFile)

        setContent {
            BigMFATheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    BigMfaRoot(
                        modifier = Modifier.padding(innerPadding),
                        vaultExistsAtStartup = repository.exists(),
                        unlockViewModel = unlockViewModel,
                        createVaultViewModel = createVaultViewModel,
                    )
                }
            }
        }
    }
}

@Composable
private fun BigMfaRoot(
    modifier: Modifier,
    vaultExistsAtStartup: Boolean,
    unlockViewModel: UnlockViewModel,
    createVaultViewModel: CreateVaultViewModel,
) {
    val unlockState by unlockViewModel.uiState.collectAsState()
    val createState by createVaultViewModel.uiState.collectAsState()

    var vaultKnownToExist by remember(vaultExistsAtStartup) { mutableStateOf(vaultExistsAtStartup) }
    var activeSession by remember { mutableStateOf<VaultUnlocker.UnlockResult.Success?>(null) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_START) {
                val backgroundedAt = AppLifecycleObserver.backgroundedAtMillis
                if (activeSession != null && backgroundedAt != null &&
                    AutoLockManager.shouldLock(backgroundedAt, System.currentTimeMillis())
                ) {
                    activeSession = null
                    unlockViewModel.lock()
                    createVaultViewModel.lock()
                }
                AppLifecycleObserver.reset()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(unlockState) {
        val state = unlockState
        if (state is UnlockViewModel.UiState.Unlocked) {
            activeSession = state.session
        }
    }
    LaunchedEffect(createState) {
        val state = createState
        if (state is CreateVaultViewModel.UiState.Created) {
            activeSession = state.session
            vaultKnownToExist = true
        }
    }

    val session = activeSession
    when {
        session != null -> {
            BigMfaNavHost(
                session = session,
                onLockRequested = {
                    activeSession = null
                    unlockViewModel.lock()
                    createVaultViewModel.lock()
                },
                onVaultDeleted = {
                    activeSession = null
                    vaultKnownToExist = false
                    unlockViewModel.lock()
                    createVaultViewModel.lock()
                },
                modifier = modifier,
            )
        }
        vaultKnownToExist -> {
            UnlockScreen(
                viewModel = unlockViewModel,
                onVaultReset = { vaultKnownToExist = false },
                modifier = modifier,
            )
        }
        else -> {
            CreateVaultScreen(viewModel = createVaultViewModel, modifier = modifier)
        }
    }
}