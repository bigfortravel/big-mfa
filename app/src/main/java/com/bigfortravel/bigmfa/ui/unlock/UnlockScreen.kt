// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.ui.unlock

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.fragment.app.FragmentActivity
import com.bigfortravel.bigmfa.R
import com.bigfortravel.bigmfa.ui.common.BigMfaAuthFooter
import com.bigfortravel.bigmfa.ui.common.PasswordTextField
import com.bigfortravel.bigmfa.ui.support.SupportScreen

@Composable
fun UnlockScreen(
    viewModel: UnlockViewModel,
    onVaultReset: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val failedAttempts by viewModel.failedAttempts.collectAsState()
    var passwordText by remember { mutableStateOf("") }
    var showResetFirstConfirm by remember { mutableStateOf(false) }
    var showResetFinalConfirm by remember { mutableStateOf(false) }
    var showSupport by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val activity = context as? FragmentActivity

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(id = R.drawable.big_mfa_logo),
            contentDescription = stringResource(R.string.unlock_title),
            modifier = Modifier.size(80.dp),
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.unlock_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )

        Text(
            text = stringResource(R.string.unlock_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PasswordTextField(
                value = passwordText,
                onValueChange = {
                    passwordText = it
                    viewModel.resetError()
                },
                label = stringResource(R.string.unlock_password_label),
                isError = uiState is UnlockViewModel.UiState.Error,
                modifier = Modifier.weight(1f),
            )

            if (viewModel.isBiometricAvailable && activity != null) {
                IconButton(onClick = { viewModel.onBiometricUnlockClicked(activity) }) {
                    Icon(
                        imageVector = Icons.Filled.Fingerprint,
                        contentDescription = stringResource(R.string.unlock_biometric_content_description),
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(5) { index ->
                val filled = index < failedAttempts
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (filled) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.outline,
                        ),
                )
            }
        }

        if (uiState is UnlockViewModel.UiState.Error) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = (uiState as UnlockViewModel.UiState.Error).message.asString(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                val passwordChars = passwordText.toCharArray()
                passwordText = ""
                viewModel.onUnlockClicked(passwordChars)
            },
            enabled = uiState !is UnlockViewModel.UiState.Loading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (uiState is UnlockViewModel.UiState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(stringResource(R.string.unlock_button))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.unlock_reset_vault),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier.clickable {
                showResetFirstConfirm = true
            },
        )

        BigMfaAuthFooter(onSupportClicked = { showSupport = true })
    }

    if (showResetFirstConfirm) {
        AlertDialog(
            onDismissRequest = { showResetFirstConfirm = false },
            title = { Text("Réinitialiser le coffre ?") },
            text = { Text("Tous vos comptes seront définitivement supprimés. Cette action ne peut pas être annulée.") },
            confirmButton = {
                TextButton(onClick = {
                    showResetFirstConfirm = false
                    showResetFinalConfirm = true
                }) {
                    Text("Continuer", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetFirstConfirm = false }) {
                    Text("Annuler")
                }
            },
        )
    }

    if (showResetFinalConfirm) {
        AlertDialog(
            onDismissRequest = { showResetFinalConfirm = false },
            title = { Text("Dernière confirmation") },
            text = { Text("Êtes-vous VRAIMENT sûr ? Aucune récupération ne sera possible après cette étape.") },
            confirmButton = {
                TextButton(onClick = {
                    showResetFinalConfirm = false
                    viewModel.resetVault()
                    onVaultReset()
                }) {
                    Text("Supprimer définitivement", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetFinalConfirm = false }) {
                    Text("Annuler")
                }
            },
        )
    }

    if (showSupport) {
        Dialog(onDismissRequest = { showSupport = false }) {
            Surface {
                SupportScreen()
            }
        }
    }
}