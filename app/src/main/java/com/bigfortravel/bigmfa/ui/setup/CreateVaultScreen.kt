// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.ui.setup

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.bigfortravel.bigmfa.R
import com.bigfortravel.bigmfa.ui.common.BigMfaAuthFooter
import com.bigfortravel.bigmfa.ui.common.PasswordTextField
import com.bigfortravel.bigmfa.ui.support.SupportScreen

@Composable
fun CreateVaultScreen(viewModel: CreateVaultViewModel, modifier: Modifier = Modifier) {
    val uiState by viewModel.uiState.collectAsState()
    var passwordText by remember { mutableStateOf("") }
    var confirmText by remember { mutableStateOf("") }
    var showSupport by remember { mutableStateOf(false) }
    var showRestore by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (showRestore) {
        RestoreBackupScreen(
            viewModel = viewModel,
            onCancel = { showRestore = false },
            modifier = modifier,
        )
        return
    }

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
            text = stringResource(R.string.create_vault_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.create_vault_min_chars),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(32.dp))

        PasswordTextField(
            value = passwordText,
            onValueChange = {
                passwordText = it
                viewModel.resetError()
            },
            label = stringResource(R.string.create_vault_password_label),
            isError = uiState is CreateVaultViewModel.UiState.Error,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(12.dp))

        PasswordTextField(
            value = confirmText,
            onValueChange = {
                confirmText = it
                viewModel.resetError()
            },
            label = stringResource(R.string.create_vault_confirm_label),
            isError = uiState is CreateVaultViewModel.UiState.Error,
            modifier = Modifier.fillMaxWidth(),
        )

        if (uiState is CreateVaultViewModel.UiState.Error) {
            Text(
                text = (uiState as CreateVaultViewModel.UiState.Error).message.asString(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                val passwordChars = passwordText.toCharArray()
                val confirmChars = confirmText.toCharArray()
                passwordText = ""
                confirmText = ""
                viewModel.onCreateClicked(passwordChars, confirmChars)
            },
            enabled = uiState !is CreateVaultViewModel.UiState.Loading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (uiState is CreateVaultViewModel.UiState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(stringResource(R.string.create_vault_create_button))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = { showRestore = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.create_vault_restore_button))
        }

        BigMfaAuthFooter(onSupportClicked = { showSupport = true })
    }

    if (showSupport) {
        Dialog(onDismissRequest = { showSupport = false }) {
            Surface {
                SupportScreen()
            }
        }
    }
}

@Composable
private fun RestoreBackupScreen(
    viewModel: CreateVaultViewModel,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var selectedFileContent by remember { mutableStateOf<String?>(null) }
    var backupPassword by remember { mutableStateOf("") }
    var newMasterPassword by remember { mutableStateOf("") }
    var confirmNewMasterPassword by remember { mutableStateOf("") }
    val fileReadErrorMessage = stringResource(R.string.restore_backup_file_read_error)

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            try {
                val content = context.contentResolver.openInputStream(uri)?.use {
                    it.bufferedReader().readText()
                }
                selectedFileContent = content
                viewModel.resetError()
            } catch (_: Exception) {
                Toast.makeText(context, fileReadErrorMessage, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            text = stringResource(R.string.restore_backup_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.restore_backup_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (selectedFileContent == null) {
            Button(
                onClick = { filePicker.launch("application/json") },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.restore_backup_choose_file))
            }
        } else {
            Text(
                text = stringResource(R.string.restore_backup_file_selected),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(16.dp))

            PasswordTextField(
                value = backupPassword,
                onValueChange = { backupPassword = it; viewModel.resetError() },
                label = stringResource(R.string.restore_backup_backup_password_label),
                isError = uiState is CreateVaultViewModel.UiState.Error,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.restore_backup_new_password_title),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.restore_backup_new_password_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))

            PasswordTextField(
                value = newMasterPassword,
                onValueChange = { newMasterPassword = it; viewModel.resetError() },
                label = stringResource(R.string.restore_backup_new_password_label),
                isError = uiState is CreateVaultViewModel.UiState.Error,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(12.dp))

            PasswordTextField(
                value = confirmNewMasterPassword,
                onValueChange = { confirmNewMasterPassword = it; viewModel.resetError() },
                label = stringResource(R.string.restore_backup_confirm_new_password_label),
                isError = uiState is CreateVaultViewModel.UiState.Error,
                modifier = Modifier.fillMaxWidth(),
            )

            if (uiState is CreateVaultViewModel.UiState.Error) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = (uiState as CreateVaultViewModel.UiState.Error).message.asString(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    val content = selectedFileContent ?: return@Button
                    viewModel.onRestoreClicked(
                        content,
                        backupPassword.toCharArray(),
                        newMasterPassword.toCharArray(),
                        confirmNewMasterPassword.toCharArray(),
                    )
                    backupPassword = ""
                },
                enabled = uiState !is CreateVaultViewModel.UiState.Loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (uiState is CreateVaultViewModel.UiState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(R.string.restore_backup_restore_button))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.restore_backup_cancel),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 8.dp)
                .clickable(onClick = onCancel),
        )
    }
}