// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.ui.addaccount

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bigfortravel.bigmfa.R
import com.bigfortravel.bigmfa.otp.GoogleAuthMigrationParser
import com.bigfortravel.bigmfa.otp.OtpAuthUriParser
import com.bigfortravel.bigmfa.otp.TotpGenerator
import com.bigfortravel.bigmfa.ui.scanner.QrScannerScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAccountScreen(viewModel: AddAccountViewModel, modifier: Modifier = Modifier) {
    val uiState by viewModel.uiState.collectAsState()
    var name by remember { mutableStateOf("") }
    var issuer by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var showScanner by remember { mutableStateOf(false) }
    var scanError by remember { mutableStateOf<String?>(null) }

    var showAdvanced by remember { mutableStateOf(false) }
    var isHotp by remember { mutableStateOf(false) }
    var algorithm by remember { mutableStateOf("SHA-1") }
    var digits by remember { mutableStateOf(6) }
    var period by remember { mutableStateOf("30") }

    var migratedAccounts by remember { mutableStateOf<List<GoogleAuthMigrationParser.MigratedAccount>?>(null) }
    var selectedIndices by remember { mutableStateOf(setOf<Int>()) }

    val qrInvalidMessage = stringResource(R.string.add_account_qr_invalid)

    if (showScanner) {
        Column(modifier = modifier.fillMaxSize()) {
            QrScannerScreen(
                onQrCodeScanned = { rawValue ->
                    if (GoogleAuthMigrationParser.isMigrationUri(rawValue)) {
                        try {
                            val accounts = GoogleAuthMigrationParser.parse(rawValue)
                            migratedAccounts = accounts
                            selectedIndices = accounts.indices.toSet()
                            scanError = null
                        } catch (_: Exception) {
                            scanError = qrInvalidMessage
                        }
                    } else {
                        try {
                            val parsed = OtpAuthUriParser.parse(rawValue)
                            name = parsed.name
                            issuer = parsed.issuer
                            secret = bytesToBase32(parsed.secret)
                            isHotp = parsed.type == "hotp"
                            algorithm = when (parsed.algorithm) {
                                TotpGenerator.Algorithm.SHA256 -> "SHA-256"
                                TotpGenerator.Algorithm.SHA512 -> "SHA-512"
                                else -> "SHA-1"
                            }
                            digits = parsed.digits
                            period = parsed.period.toString()
                            scanError = null
                        } catch (_: Exception) {
                            scanError = qrInvalidMessage
                        }
                    }
                    showScanner = false
                },
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { showScanner = false },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(stringResource(R.string.add_account_cancel_scan))
            }
        }
        return
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
            text = stringResource(R.string.add_account_title),
            style = MaterialTheme.typography.headlineSmall,
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = { showScanner = true },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 14.dp),
        ) {
            Text(text = stringResource(R.string.add_account_scan_qr), style = MaterialTheme.typography.bodyLarge)
        }

        if (scanError != null) {
            Text(
                text = scanError ?: "",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it; viewModel.resetError() },
            label = { Text(stringResource(R.string.add_account_name_label)) },
            singleLine = true,
            isError = uiState is AddAccountViewModel.UiState.Error,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = issuer,
            onValueChange = { issuer = it; viewModel.resetError() },
            label = { Text(stringResource(R.string.add_account_issuer_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = secret,
            onValueChange = { secret = it; viewModel.resetError() },
            label = { Text(stringResource(R.string.add_account_secret_label)) },
            singleLine = true,
            isError = uiState is AddAccountViewModel.UiState.Error,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clickable { showAdvanced = !showAdvanced },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.add_account_advanced_options),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f).padding(vertical = 8.dp),
            )
            Text(if (showAdvanced) "▲" else "▼", color = MaterialTheme.colorScheme.primary)
        }

        if (showAdvanced) {
            Spacer(modifier = Modifier.height(8.dp))

            OptionDropdown(
                label = stringResource(R.string.add_account_type_label),
                selectedText = if (isHotp) {
                    stringResource(R.string.add_account_type_hotp)
                } else {
                    stringResource(R.string.add_account_type_totp)
                },
                options = listOf(
                    stringResource(R.string.add_account_type_totp) to false,
                    stringResource(R.string.add_account_type_hotp) to true,
                ),
                onOptionSelected = { isHotp = it },
            )

            Spacer(modifier = Modifier.height(12.dp))

            OptionDropdown(
                label = stringResource(R.string.add_account_algorithm_label),
                selectedText = when (algorithm) {
                    "SHA-256" -> "SHA-256"
                    "SHA-512" -> "SHA-512"
                    else -> stringResource(R.string.add_account_algorithm_sha1)
                },
                options = listOf(
                    stringResource(R.string.add_account_algorithm_sha1) to "SHA-1",
                    "SHA-256" to "SHA-256",
                    "SHA-512" to "SHA-512",
                ),
                onOptionSelected = { algorithm = it },
            )

            Spacer(modifier = Modifier.height(12.dp))

            OptionDropdown(
                label = stringResource(R.string.add_account_digits_label),
                selectedText = if (digits == 6) {
                    stringResource(R.string.add_account_digits_default)
                } else {
                    stringResource(R.string.add_account_digits_n, digits)
                },
                options = listOf(
                    stringResource(R.string.add_account_digits_default) to 6,
                    stringResource(R.string.add_account_digits_n, 7) to 7,
                    stringResource(R.string.add_account_digits_n, 8) to 8,
                ),
                onOptionSelected = { digits = it },
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = period,
                onValueChange = { new -> if (new.all { it.isDigit() }) period = new },
                label = { Text(stringResource(R.string.add_account_period_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (uiState is AddAccountViewModel.UiState.Error) {
            Text(
                text = (uiState as AddAccountViewModel.UiState.Error).message.asString(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Spacer(modifier = Modifier.height(5.dp))

        Button(
            onClick = {
                viewModel.onSaveClicked(
                    name = name,
                    issuer = issuer,
                    secret = secret,
                    isHotp = isHotp,
                    algorithm = algorithm,
                    digits = digits,
                    period = period.toIntOrNull() ?: 30,
                )
            },
            enabled = uiState !is AddAccountViewModel.UiState.Loading,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                disabledContentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (uiState is AddAccountViewModel.UiState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(stringResource(R.string.add_account_save))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    val currentMigratedAccounts = migratedAccounts
    if (currentMigratedAccounts != null) {
        MigrationImportDialog(
            accounts = currentMigratedAccounts,
            selectedIndices = selectedIndices,
            onSelectionChanged = { selectedIndices = it },
            onImportConfirmed = {
                currentMigratedAccounts.forEachIndexed { index, account ->
                    if (index in selectedIndices) {
                        viewModel.onSaveClicked(
                            name = account.name,
                            issuer = account.issuer,
                            secret = bytesToBase32(account.secret),
                            isHotp = account.isHotp,
                            algorithm = when (account.algorithm) {
                                TotpGenerator.Algorithm.SHA256 -> "SHA-256"
                                TotpGenerator.Algorithm.SHA512 -> "SHA-512"
                                else -> "SHA-1"
                            },
                            digits = account.digits,
                            period = 30,
                        )
                    }
                }
                migratedAccounts = null
            },
            onDismiss = { migratedAccounts = null },
        )
    }
}

@Composable
private fun MigrationImportDialog(
    accounts: List<GoogleAuthMigrationParser.MigratedAccount>,
    selectedIndices: Set<Int>,
    onSelectionChanged: (Set<Int>) -> Unit,
    onImportConfirmed: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_account_migration_detected_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.add_account_migration_detected_desc, accounts.size),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelectionChanged(
                                if (selectedIndices.size == accounts.size) emptySet() else accounts.indices.toSet(),
                            )
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = selectedIndices.size == accounts.size,
                        onCheckedChange = {
                            onSelectionChanged(if (it) accounts.indices.toSet() else emptySet())
                        },
                    )
                    Text(stringResource(R.string.add_account_migration_select_all))
                }

                LazyColumn(modifier = Modifier.heightIn(max = 350.dp)) {
                    items(accounts.size) { index ->
                        val account = accounts[index]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectionChanged(
                                        if (index in selectedIndices) selectedIndices - index else selectedIndices + index,
                                    )
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = index in selectedIndices,
                                onCheckedChange = {
                                    onSelectionChanged(
                                        if (it) selectedIndices + index else selectedIndices - index,
                                    )
                                },
                            )
                            Column {
                                Text(account.name, style = MaterialTheme.typography.bodyMedium)
                                if (account.issuer.isNotBlank()) {
                                    Text(
                                        account.issuer,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onImportConfirmed, enabled = selectedIndices.isNotEmpty()) {
                Text(stringResource(R.string.add_account_migration_import_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.add_account_migration_cancel))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> OptionDropdown(
    label: String,
    selectedText: String,
    options: List<Pair<String, T>>,
    onOptionSelected: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            OutlinedTextField(
                value = selectedText,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { (text, value) ->
                    DropdownMenuItem(
                        text = { Text(text) },
                        onClick = {
                            onOptionSelected(value)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

private fun bytesToBase32(bytes: ByteArray): String =
    org.bouncycastle.util.encoders.Base32.toBase32String(bytes)