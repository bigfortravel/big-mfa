// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import android.widget.Toast
import com.bigfortravel.bigmfa.R
import com.bigfortravel.bigmfa.ui.common.PasswordTextField
import com.bigfortravel.bigmfa.ui.common.UiText
import com.bigfortravel.bigmfa.ui.common.bigMfaSwitchColors
import com.bigfortravel.bigmfa.ui.theme.BigMfaSuccess
import com.bigfortravel.bigmfa.vault.BackupImporter
import com.bigfortravel.bigmfa.vault.BackupV3Codec
import com.bigfortravel.bigmfa.vault.VaultContentWriter
import com.bigfortravel.bigmfa.vault.VaultExporter
import com.bigfortravel.bigmfa.vault.VaultRepository
import com.bigfortravel.bigmfa.vault.VaultUnlocker
import com.bigfortravel.bigmfa.vault.model.VaultAccount
import java.io.File

@Composable
fun SettingsScreen(
    viewModel: BiometricSetupViewModel,
    accounts: List<VaultAccount>,
    session: VaultUnlocker.UnlockResult.Success,
    onDataDeleted: () -> Unit,
    onAccountsImported: (List<VaultAccount>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showBiometricSetup by remember { mutableStateOf(false) }
    var showChangePassword by remember { mutableStateOf(false) }
    val context = LocalContext.current

    when {
        showBiometricSetup -> {
            BiometricSetupSubScreen(
                viewModel = viewModel,
                onDone = { showBiometricSetup = false },
                modifier = modifier,
            )
        }
        showChangePassword -> {
            val changePasswordViewModel: ChangePasswordViewModel = viewModel {
                val repo = VaultRepository(File(context.filesDir, "vault.json"))
                ChangePasswordViewModel(VaultUnlocker(repo))
            }
            ChangePasswordSubScreen(
                viewModel = changePasswordViewModel,
                onDone = { showChangePassword = false },
                modifier = modifier,
            )
        }
        else -> {
            SettingsMenu(
                viewModel = viewModel,
                accounts = accounts,
                session = session,
                onDataDeleted = onDataDeleted,
                onAccountsImported = onAccountsImported,
                onBiometricRowClicked = { showBiometricSetup = true },
                onChangePasswordClicked = { showChangePassword = true },
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun SettingsMenu(
    viewModel: BiometricSetupViewModel,
    accounts: List<VaultAccount>,
    session: VaultUnlocker.UnlockResult.Success,
    onDataDeleted: () -> Unit,
    onAccountsImported: (List<VaultAccount>) -> Unit,
    onBiometricRowClicked: () -> Unit,
    onChangePasswordClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isCurrentlyEnabled by viewModel.isBiometricEnabled.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineSmall,
        )

        Spacer(modifier = Modifier.height(16.dp))

        SecurityStatusCard()

        Spacer(modifier = Modifier.height(16.dp))

        var showLanguageDialog by remember { mutableStateOf(false) }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .clickable { showLanguageDialog = true },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.settings_language_row_title), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = com.bigfortravel.bigmfa.ui.common.currentLanguageLabel(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (showLanguageDialog) {
            com.bigfortravel.bigmfa.ui.common.LanguageSelectorDialog(onDismiss = { showLanguageDialog = false })
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(stringResource(R.string.biometric_row_title), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = if (isCurrentlyEnabled) {
                        stringResource(R.string.biometric_row_enabled)
                    } else {
                        stringResource(R.string.biometric_row_disabled)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = isCurrentlyEnabled,
                colors = bigMfaSwitchColors(),
                onCheckedChange = {
                    if (isCurrentlyEnabled) {
                        viewModel.disableBiometric()
                        Toast.makeText(context, context.getString(R.string.biometric_deactivated_toast), Toast.LENGTH_SHORT).show()
                    } else {
                        onBiometricRowClicked()
                    }
                },
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onChangePasswordClicked,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.settings_change_password_button))
        }

        Spacer(modifier = Modifier.height(24.dp))

        ExportSection(accounts = accounts, session = session, onAccountsImported = onAccountsImported)

        Spacer(modifier = Modifier.height(24.dp))

        DangerZoneSection(session = session, onDataDeleted = onDataDeleted)

        Spacer(modifier = Modifier.height(24.dp))

        PrivacyRgpdCard()

        Spacer(modifier = Modifier.height(24.dp))

        AboutSection()

        Spacer(modifier = Modifier.height(24.dp))

        AutofillSection()

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun AboutSection() {
    val context = LocalContext.current
    var showLicenseDialog by remember { mutableStateOf(false) }
    var showNoticesDialog by remember { mutableStateOf(false) }

    Column {
        Text(
            text = stringResource(R.string.settings_about_title),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.settings_about_version_label))
            Text("1.0", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.settings_about_license_label))
            Text(
                stringResource(R.string.settings_about_license_value),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = { showLicenseDialog = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.settings_about_view_license_button))
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = { showNoticesDialog = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.settings_about_view_notices_button))
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = {
                val intent = android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://github.com/bigfortravel/big-mfa"),
                )
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.settings_about_source_button))
        }
    }

    if (showLicenseDialog) {
        RawTextDialog(
            rawResId = R.raw.license,
            onDismiss = { showLicenseDialog = false },
        )
    }

    if (showNoticesDialog) {
        RawTextDialog(
            rawResId = R.raw.notices,
            onDismiss = { showNoticesDialog = false },
        )
    }
}

@Composable
private fun AutofillSection() {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
        return
    }

    val context = LocalContext.current
    var isAutofillEnabled by remember { mutableStateOf(checkAutofillEnabled(context)) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        isAutofillEnabled = checkAutofillEnabled(context)
    }

    Column {
        Text(
            text = stringResource(R.string.settings_autofill_title),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (isAutofillEnabled) {
                stringResource(R.string.settings_autofill_status_enabled)
            } else {
                stringResource(R.string.settings_autofill_status_disabled)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (isAutofillEnabled) BigMfaSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.settings_autofill_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (isAutofillEnabled) {
            OutlinedButton(
                onClick = {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE)
                    intent.data = android.net.Uri.parse("package:${context.packageName}")
                    launcher.launch(intent)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_autofill_disable_button))
            }
        } else {
            Button(
                onClick = {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE)
                    intent.data = android.net.Uri.parse("package:${context.packageName}")
                    launcher.launch(intent)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_autofill_enable_button))
            }
        }
    }
}

@androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.O)
private fun checkAutofillEnabled(context: android.content.Context): Boolean {
    val autofillManager = context.getSystemService(android.view.autofill.AutofillManager::class.java)
    return autofillManager?.hasEnabledAutofillServices() == true
}

@Composable
private fun RawTextDialog(rawResId: Int, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val content = remember(rawResId) {
        context.resources.openRawResource(rawResId).bufferedReader().use { it.readText() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("") },
        text = {
            Text(
                text = content,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_about_dialog_close))
            }
        },
    )
}

@Composable
private fun SecurityStatusCard() {
    Column {
        Text(
            text = stringResource(R.string.settings_security_status_title),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))

        AssistChip(
            onClick = {},
            label = { Text(stringResource(R.string.settings_masvs_badge)) },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = BigMfaSuccess.copy(alpha = 0.15f),
                labelColor = BigMfaSuccess,
            ),
        )

        Spacer(modifier = Modifier.height(12.dp))

        val items = listOf(
            stringResource(R.string.settings_encryption_label) to "AES-256-GCM-SIV",
            stringResource(R.string.settings_kdf_label) to "Argon2id",
            stringResource(R.string.settings_inactivity_label) to stringResource(R.string.settings_inactivity_value),
            stringResource(R.string.settings_bruteforce_label) to stringResource(R.string.settings_bruteforce_value),
            stringResource(R.string.settings_lockout_label) to stringResource(R.string.settings_lockout_value),
            stringResource(R.string.settings_salt_label) to stringResource(R.string.settings_salt_value),
            stringResource(R.string.settings_integrity_label) to stringResource(R.string.settings_integrity_value),
            stringResource(R.string.settings_hardware_label) to stringResource(R.string.settings_hardware_value),
        )

        items.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                pair.forEach { (label, value) ->
                    Card(modifier = Modifier.weight(1f)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = value,
                                style = MaterialTheme.typography.bodyMedium,
                                color = BigMfaSuccess,
                            )
                        }
                    }
                }
                if (pair.size == 1) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        Text(
            text = stringResource(R.string.settings_bruteforce_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

private enum class ExportFormat { NATIVE, CHROME_COMPATIBLE }

@Composable
private fun ExportSection(
    accounts: List<VaultAccount>,
    session: VaultUnlocker.UnlockResult.Success,
    onAccountsImported: (List<VaultAccount>) -> Unit,
) {
    val context = LocalContext.current
    var exportFormat by remember { mutableStateOf<ExportFormat?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showExportQr by remember { mutableStateOf(false) }

    Column {
        Text(
            text = stringResource(R.string.settings_backup_title),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = { exportFormat = ExportFormat.NATIVE },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                Text(stringResource(R.string.settings_export_native_title))
                Text(
                    text = stringResource(R.string.settings_export_native_subtitle),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = { exportFormat = ExportFormat.CHROME_COMPATIBLE },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                Text(stringResource(R.string.settings_export_chrome_title))
                Text(
                    text = stringResource(R.string.settings_export_chrome_subtitle),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = { showExportQr = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                Text(stringResource(R.string.settings_export_qr_title))
                Text(
                    text = stringResource(R.string.settings_export_qr_subtitle),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = { showImportDialog = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.settings_import_button))
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.settings_export_footer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    val format = exportFormat
    if (format != null) {
        ExportPasswordDialog(
            accounts = accounts,
            format = format,
            onDismiss = { exportFormat = null },
        )
    }

    if (showImportDialog) {
        ImportDialog(
            accounts = accounts,
            session = session,
            onDismiss = { showImportDialog = false },
            onImported = { updatedAccounts ->
                showImportDialog = false
                onAccountsImported(updatedAccounts)
            },
        )
    }

    if (showExportQr) {
        Dialog(
            onDismissRequest = { showExportQr = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(modifier = Modifier.fillMaxSize()) {
                com.bigfortravel.bigmfa.ui.settings.ExportQrScreen(
                    accounts = accounts,
                    onCancel = { showExportQr = false },
                )
            }
        }
    }
}

@Composable
private fun ExportPasswordDialog(
    accounts: List<VaultAccount>,
    format: ExportFormat,
    onDismiss: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<UiText?>(null) }
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.settings_export_share_title)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (format == ExportFormat.NATIVE) {
                    stringResource(R.string.settings_export_native_dialog_title)
                } else {
                    stringResource(R.string.settings_export_chrome_dialog_title)
                },
            )
        },
        text = {
            Column {
                Text(
                    text = if (format == ExportFormat.NATIVE) {
                        stringResource(R.string.settings_export_native_dialog_desc)
                    } else {
                        stringResource(R.string.settings_export_chrome_dialog_desc)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                PasswordTextField(
                    value = password,
                    onValueChange = { password = it; error = null },
                    label = stringResource(R.string.settings_export_password_label),
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                PasswordTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it; error = null },
                    label = stringResource(R.string.settings_export_confirm_label),
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) {
                    Text(
                        text = error?.asString() ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (password.length < 8) {
                    error = UiText.Resource(R.string.settings_export_min_chars_error)
                    return@TextButton
                }
                if (password != confirmPassword) {
                    error = UiText.Resource(R.string.settings_export_mismatch_error)
                    return@TextButton
                }
                try {
                    val (content, fileName) = if (format == ExportFormat.NATIVE) {
                        val exporter = VaultExporter()
                        exporter.export(accounts, password.toCharArray()) to
                                "big-mfa-export-${System.currentTimeMillis()}.json"
                    } else {
                        BackupV3Codec.encode(accounts, password.toCharArray()) to
                                "big-mfa-chrome-export-${System.currentTimeMillis()}.json"
                    }

                    val file = File(context.cacheDir, fileName)
                    file.writeText(content)

                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file,
                    )
                    val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(android.content.Intent.createChooser(shareIntent, shareTitle))
                    onDismiss()
                } catch (_: Exception) {
                    error = UiText.Resource(R.string.settings_export_generic_error)
                }
            }) {
                Text(stringResource(R.string.settings_export_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_export_cancel))
            }
        },
    )
}

@Composable
private fun ImportDialog(
    accounts: List<VaultAccount>,
    session: VaultUnlocker.UnlockResult.Success,
    onDismiss: () -> Unit,
    onImported: (List<VaultAccount>) -> Unit,
) {
    val context = LocalContext.current
    var selectedFileContent by remember { mutableStateOf<String?>(null) }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<UiText?>(null) }
    val successToastTemplate = stringResource(R.string.settings_import_success_toast)

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            try {
                val content = context.contentResolver.openInputStream(uri)?.use {
                    it.bufferedReader().readText()
                }
                selectedFileContent = content
                error = null
            } catch (_: Exception) {
                error = UiText.Resource(R.string.settings_import_read_error)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_import_dialog_title)) },
        text = {
            Column {
                if (selectedFileContent == null) {
                    Text(
                        text = stringResource(R.string.settings_import_dialog_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { filePicker.launch("application/json") },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.settings_import_choose_file))
                    }
                } else {
                    Text(
                        text = stringResource(R.string.settings_import_file_selected_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    PasswordTextField(
                        value = password,
                        onValueChange = { password = it; error = null },
                        label = stringResource(R.string.settings_import_password_label),
                        isError = error != null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error?.asString() ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            if (selectedFileContent != null) {
                TextButton(
                    onClick = {
                        val content = selectedFileContent ?: return@TextButton
                        try {
                            val importer = BackupImporter()
                            val merged = importer.importAndMerge(content, password.toCharArray(), accounts)

                            val writer = VaultContentWriter(
                                VaultRepository(File(context.filesDir, "vault.json")),
                            )
                            writer.writeAccounts(merged, session.contentKey, session.hmacKey)

                            Toast.makeText(
                                context,
                                successToastTemplate.format(merged.size - accounts.size),
                                Toast.LENGTH_SHORT,
                            ).show()
                            onImported(merged)
                        } catch (e: BackupImporter.ImportException) {
                            error = e.uiMessage
                        } catch (_: Exception) {
                            error = UiText.Resource(R.string.settings_import_generic_error)
                        }
                    },
                ) {
                    Text(stringResource(R.string.settings_import_button_confirm))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_export_cancel))
            }
        },
    )
}

@Composable
private fun DangerZoneSection(
    session: VaultUnlocker.UnlockResult.Success,
    onDataDeleted: () -> Unit,
) {
    var showFirstConfirm by remember { mutableStateOf(false) }
    var showFinalConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column {
        Text(
            text = stringResource(R.string.settings_danger_zone_title),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { showFirstConfirm = true },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.settings_delete_all_button))
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.settings_delete_all_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showFirstConfirm) {
        AlertDialog(
            onDismissRequest = { showFirstConfirm = false },
            title = { Text(stringResource(R.string.settings_delete_confirm_title)) },
            text = { Text(stringResource(R.string.settings_delete_confirm_desc)) },
            confirmButton = {
                TextButton(onClick = {
                    showFirstConfirm = false
                    showFinalConfirm = true
                }) {
                    Text(stringResource(R.string.settings_delete_continue), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showFirstConfirm = false }) {
                    Text(stringResource(R.string.settings_delete_cancel))
                }
            },
        )
    }

    if (showFinalConfirm) {
        AlertDialog(
            onDismissRequest = { showFinalConfirm = false },
            title = { Text(stringResource(R.string.settings_delete_final_title)) },
            text = { Text(stringResource(R.string.settings_delete_final_desc)) },
            confirmButton = {
                TextButton(onClick = {
                    showFinalConfirm = false
                    val vaultFile = File(context.filesDir, "vault.json")
                    val repository = VaultRepository(vaultFile)
                    val unlocker = VaultUnlocker(repository)
                    unlocker.resetVault()
                    onDataDeleted()
                }) {
                    Text(stringResource(R.string.settings_delete_final_button), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showFinalConfirm = false }) {
                    Text(stringResource(R.string.settings_delete_cancel))
                }
            },
        )
    }
}

@Composable
private fun PrivacyRgpdCard() {
    Column {
        AssistChip(
            onClick = {},
            label = { Text(stringResource(R.string.settings_rgpd_badge)) },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                labelColor = MaterialTheme.colorScheme.primary,
            ),
        )

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.settings_rgpd_title),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))

        val rows = listOf(
            stringResource(R.string.settings_rgpd_controller_label) to stringResource(R.string.settings_rgpd_controller_value),
            stringResource(R.string.settings_rgpd_data_label) to stringResource(R.string.settings_rgpd_data_value),
            stringResource(R.string.settings_rgpd_storage_label) to stringResource(R.string.settings_rgpd_storage_value),
            stringResource(R.string.settings_rgpd_transfer_label) to stringResource(R.string.settings_rgpd_transfer_value),
            stringResource(R.string.settings_rgpd_thirdparty_label) to stringResource(R.string.settings_rgpd_thirdparty_value),
            stringResource(R.string.settings_rgpd_retention_label) to stringResource(R.string.settings_rgpd_retention_value),
            stringResource(R.string.settings_rgpd_legal_label) to stringResource(R.string.settings_rgpd_legal_value),
            stringResource(R.string.settings_rgpd_access_label) to stringResource(R.string.settings_rgpd_access_value),
            stringResource(R.string.settings_rgpd_erasure_label) to stringResource(R.string.settings_rgpd_erasure_value),
            stringResource(R.string.settings_rgpd_portability_label) to stringResource(R.string.settings_rgpd_portability_value),
            stringResource(R.string.settings_rgpd_dpo_label) to stringResource(R.string.settings_rgpd_dpo_value),
            stringResource(R.string.settings_rgpd_eu_label) to stringResource(R.string.settings_rgpd_eu_value),
            stringResource(R.string.settings_rgpd_cm_label) to stringResource(R.string.settings_rgpd_cm_value),
        )

        rows.forEach { (label, value) ->
            Column(modifier = Modifier.padding(bottom = 10.dp)) {
                Text(
                    text = label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(text = value, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Text(
            text = stringResource(R.string.settings_rgpd_footer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BiometricSetupSubScreen(
    viewModel: BiometricSetupViewModel,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    var passwordText by remember { mutableStateOf("") }
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val hardwareEligible = remember { viewModel.checkHardwareEligibility() }

    LaunchedEffect(uiState) {
        if (uiState is BiometricSetupViewModel.UiState.Enabled) {
            Toast.makeText(context, context.getString(R.string.biometric_activated_toast), Toast.LENGTH_SHORT).show()
            onDone()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Text(text = stringResource(R.string.biometric_setup_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(24.dp))

        if (!hardwareEligible) {
            Text(
                text = stringResource(R.string.biometric_hardware_insufficient),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            when (uiState) {
                is BiometricSetupViewModel.UiState.AwaitingBiometricPrompt -> {
                    Text(
                        text = stringResource(R.string.biometric_password_confirmed_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { activity?.let { viewModel.onBiometricPromptRequested(it) } },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.biometric_continue_button))
                    }
                }
                else -> {
                    PasswordTextField(
                        value = passwordText,
                        onValueChange = {
                            passwordText = it
                            viewModel.resetError()
                        },
                        label = stringResource(R.string.biometric_confirm_password_label),
                        isError = uiState is BiometricSetupViewModel.UiState.Error,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            val passwordChars = passwordText.toCharArray()
                            passwordText = ""
                            viewModel.onConfirmPassword(passwordChars)
                        },
                        enabled = uiState !is BiometricSetupViewModel.UiState.Loading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.biometric_confirm_button))
                    }
                }
            }

            if (uiState is BiometricSetupViewModel.UiState.Error) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = (uiState as BiometricSetupViewModel.UiState.Error).message.asString(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.biometric_cancel),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 8.dp)
                .clickable(onClick = onDone),
        )
    }
}

@Composable
private fun ChangePasswordSubScreen(
    viewModel: ChangePasswordViewModel,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    val context = LocalContext.current

    LaunchedEffect(uiState) {
        if (uiState is ChangePasswordViewModel.UiState.Success) {
            Toast.makeText(context, context.getString(R.string.change_password_success_toast), Toast.LENGTH_SHORT).show()
            onDone()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Text(text = stringResource(R.string.change_password_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.change_password_current_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PasswordTextField(
            value = currentPassword,
            onValueChange = { currentPassword = it; viewModel.resetError() },
            label = "",
            isError = uiState is ChangePasswordViewModel.UiState.Error,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.change_password_new_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PasswordTextField(
            value = newPassword,
            onValueChange = { newPassword = it; viewModel.resetError() },
            label = "",
            isError = uiState is ChangePasswordViewModel.UiState.Error,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.change_password_confirm_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PasswordTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it; viewModel.resetError() },
            label = "",
            isError = uiState is ChangePasswordViewModel.UiState.Error,
            modifier = Modifier.fillMaxWidth(),
        )

        if (uiState is ChangePasswordViewModel.UiState.Error) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = (uiState as ChangePasswordViewModel.UiState.Error).message.asString(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                viewModel.onChangePasswordClicked(
                    currentPassword.toCharArray(),
                    newPassword.toCharArray(),
                    confirmPassword.toCharArray(),
                )
                currentPassword = ""
                newPassword = ""
                confirmPassword = ""
            },
            enabled = uiState !is ChangePasswordViewModel.UiState.Loading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.change_password_button))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.change_password_cancel),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 8.dp)
                .clickable(onClick = onDone),
        )
    }
}