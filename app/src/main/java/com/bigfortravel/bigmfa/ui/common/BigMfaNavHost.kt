// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.ui.common

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.bigfortravel.bigmfa.R
import com.bigfortravel.bigmfa.autofill.AutofillSessionHolder
import com.bigfortravel.bigmfa.otp.HotpGenerator
import com.bigfortravel.bigmfa.ui.accounts.AccountsScreen
import com.bigfortravel.bigmfa.ui.addaccount.AddAccountScreen
import com.bigfortravel.bigmfa.ui.addaccount.AddAccountViewModel
import com.bigfortravel.bigmfa.ui.decoder.DecoderScreen
import com.bigfortravel.bigmfa.ui.decoder.DecoderViewModel
import com.bigfortravel.bigmfa.ui.generatekey.GenerateKeyScreen
import com.bigfortravel.bigmfa.ui.generatekey.GenerateKeyViewModel
import com.bigfortravel.bigmfa.ui.settings.BiometricSetupViewModel
import com.bigfortravel.bigmfa.ui.settings.SettingsScreen
import com.bigfortravel.bigmfa.ui.support.SupportScreen
import com.bigfortravel.bigmfa.vault.SlotManager
import com.bigfortravel.bigmfa.vault.VaultContentWriter
import com.bigfortravel.bigmfa.vault.VaultRepository
import com.bigfortravel.bigmfa.vault.VaultUnlocker
import org.bouncycastle.util.encoders.Base32
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BigMfaNavHost(
    session: VaultUnlocker.UnlockResult.Success,
    onLockRequested: () -> Unit,
    onVaultDeleted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.runtime.LaunchedEffect(session) {
        AutofillSessionHolder.currentSession = session
    }
    val navController = rememberNavController()
    var accounts by remember { mutableStateOf(session.accounts) }
    val context = LocalContext.current

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.big_mfa_logo),
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = "Big MFA",
                            fontWeight = FontWeight.Bold,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        AutofillSessionHolder.clear()
                        onLockRequested()
                    }) {
                        Icon(imageVector = Icons.Filled.Lock, contentDescription = stringResource(R.string.nav_lock_content_description))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                BigMfaDestination.entries.forEach { destination ->
                    val isSettings = destination == BigMfaDestination.SETTINGS
                    NavigationBarItem(
                        selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Text(destination.icon) },
                        label = {
                            Text(
                                text = stringResource(destination.labelResId),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = if (isSettings) 9.sp else 10.sp,
                                    letterSpacing = if (isSettings) (-0.5).sp else (-0.3).sp,
                                ),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                        },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = BigMfaDestination.ACCOUNTS.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(BigMfaDestination.ACCOUNTS.route) {
                val writer = remember {
                    VaultContentWriter(VaultRepository(File(context.filesDir, "vault.json")))
                }
                AccountsScreen(
                    accounts = accounts,
                    onRenameConfirmed = { accountToRename, newName, newIssuer, newAutofillPackageName, newAutofillWebDomain ->
                        val updatedAccounts = accounts.map { acc ->
                            if (acc.id == accountToRename.id) {
                                acc.copy(
                                    name = newName,
                                    issuer = newIssuer,
                                    autofillPackageName = newAutofillPackageName,
                                    autofillWebDomain = newAutofillWebDomain,
                                )
                            } else {
                                acc
                            }
                        }
                        writer.writeAccounts(updatedAccounts, session.contentKey, session.hmacKey)
                        accounts = updatedAccounts
                    },
                    onDeleteConfirmed = { accountToDelete ->
                        val updatedAccounts = accounts.filterNot { it.id == accountToDelete.id }
                        writer.writeAccounts(updatedAccounts, session.contentKey, session.hmacKey)
                        accounts = updatedAccounts
                    },
                    onGenerateHotpCode = { account ->
                        val code = HotpGenerator.generate(
                            secret = Base32.decode(account.secret.toByteArray()),
                            counter = account.counter ?: 0L,
                        )
                        val updatedAccounts = accounts.map { acc ->
                            if (acc.id == account.id) acc.copy(counter = (acc.counter ?: 0L) + 1) else acc
                        }
                        writer.writeAccounts(updatedAccounts, session.contentKey, session.hmacKey)
                        accounts = updatedAccounts
                        code
                    },
                )
            }
            composable(BigMfaDestination.DECODER.route) {
                val decoderViewModel: DecoderViewModel = viewModel()
                DecoderScreen(viewModel = decoderViewModel)
            }
            composable(BigMfaDestination.GENERATE_KEY.route) {
                val generateKeyViewModel: GenerateKeyViewModel = viewModel()
                GenerateKeyScreen(viewModel = generateKeyViewModel)
            }
            composable(BigMfaDestination.ADD_ACCOUNT.route) {
                val writer = remember {
                    VaultContentWriter(VaultRepository(File(context.filesDir, "vault.json")))
                }
                val addAccountViewModel: AddAccountViewModel = viewModel {
                    AddAccountViewModel({ accounts }, session.contentKey, session.hmacKey, writer)
                }
                val uiState by addAccountViewModel.uiState.collectAsState()

                LaunchedEffect(uiState) {
                    val savedState = uiState
                    if (savedState is AddAccountViewModel.UiState.Saved) {
                        accounts = savedState.updatedAccounts
                        navController.navigate(BigMfaDestination.ACCOUNTS.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                        }
                        addAccountViewModel.consumeSavedState()
                    }
                }

                AddAccountScreen(viewModel = addAccountViewModel)
            }
            composable(BigMfaDestination.GUIDE.route) {
                com.bigfortravel.bigmfa.ui.guide.GuideScreen()
            }
            composable(BigMfaDestination.SUPPORT.route) {
                SupportScreen()
            }
            composable(BigMfaDestination.SETTINGS.route) {
                val settingsViewModel: BiometricSetupViewModel = viewModel {
                    val repo = VaultRepository(File(context.filesDir, "vault.json"))
                    BiometricSetupViewModel(VaultUnlocker(repo), SlotManager(repo), session)
                }
                SettingsScreen(
                    viewModel = settingsViewModel,
                    accounts = accounts,
                    session = session,
                    onDataDeleted = onVaultDeleted,
                    onAccountsImported = { accounts = it },
                )
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "$title — à venir", style = MaterialTheme.typography.headlineSmall)
    }
}