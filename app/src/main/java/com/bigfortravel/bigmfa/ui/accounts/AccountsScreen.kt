// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.ui.accounts

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bigfortravel.bigmfa.R
import com.bigfortravel.bigmfa.otp.TotpGenerator
import com.bigfortravel.bigmfa.vault.model.VaultAccount
import kotlinx.coroutines.delay
import org.bouncycastle.util.encoders.Base32

@Composable
fun AccountsScreen(
    accounts: List<VaultAccount>,
    onRenameConfirmed: (
        account: VaultAccount,
        newName: String,
        newIssuer: String,
        newAutofillPackageName: String?,
        newAutofillWebDomain: String?,
    ) -> Unit = { _, _, _, _, _ -> },
    onDeleteConfirmed: (VaultAccount) -> Unit = {},
    onGenerateHotpCode: (VaultAccount) -> String = { "" },
    modifier: Modifier = Modifier,
) {
    var searchQuery by remember { mutableStateOf("") }
    var accountBeingRenamed by remember { mutableStateOf<VaultAccount?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        if (accounts.isNotEmpty()) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.accounts_search_placeholder)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }

        val filteredAccounts = if (searchQuery.isBlank()) {
            accounts
        } else {
            accounts.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                        it.issuer.contains(searchQuery, ignoreCase = true)
            }
        }

        when {
            accounts.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.accounts_empty_state),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            filteredAccounts.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.accounts_no_results, searchQuery),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            else -> {
                var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
                LaunchedEffect(Unit) {
                    while (true) {
                        delay(1000)
                        nowMillis = System.currentTimeMillis()
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    items(filteredAccounts, key = { it.id }) { account ->
                        AccountRow(
                            account = account,
                            nowMillis = nowMillis,
                            onRenameRequested = { accountBeingRenamed = account },
                            onDeleteConfirmed = onDeleteConfirmed,
                            onGenerateHotpCode = onGenerateHotpCode,
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    val accountToRename = accountBeingRenamed
    if (accountToRename != null) {
        RenameAccountDialog(
            account = accountToRename,
            onConfirm = { newName, newIssuer, newPackageName, newWebDomain ->
                onRenameConfirmed(accountToRename, newName, newIssuer, newPackageName, newWebDomain)
                accountBeingRenamed = null
            },
            onDismiss = { accountBeingRenamed = null },
        )
    }
}

private enum class AutofillLinkType { NONE, APP, WEB }

@Composable
private fun RenameAccountDialog(
    account: VaultAccount,
    onConfirm: (newName: String, newIssuer: String, newPackageName: String?, newWebDomain: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(account.name) }
    var issuer by remember { mutableStateOf(account.issuer) }
    var error by remember { mutableStateOf<String?>(null) }
    val requiredErrorMessage = stringResource(R.string.accounts_name_required_error)

    val initialLinkType = when {
        account.autofillPackageName != null -> AutofillLinkType.APP
        account.autofillWebDomain != null -> AutofillLinkType.WEB
        else -> AutofillLinkType.NONE
    }
    var linkType by remember { mutableStateOf(initialLinkType) }
    var packageNameText by remember { mutableStateOf(account.autofillPackageName ?: "") }
    var webDomainText by remember { mutableStateOf(account.autofillWebDomain ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.accounts_rename_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text(stringResource(R.string.accounts_name_label)) },
                    singleLine = true,
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.size(8.dp))
                OutlinedTextField(
                    value = issuer,
                    onValueChange = { issuer = it },
                    label = { Text(stringResource(R.string.accounts_issuer_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) {
                    Text(
                        text = error ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Spacer(modifier = Modifier.size(16.dp))
                Text(
                    text = stringResource(R.string.accounts_autofill_section_title),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.size(4.dp))
                Text(
                    text = stringResource(R.string.accounts_autofill_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.size(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = linkType == AutofillLinkType.NONE,
                        onClick = { linkType = AutofillLinkType.NONE },
                    )
                    Text(stringResource(R.string.accounts_autofill_none))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = linkType == AutofillLinkType.APP,
                        onClick = { linkType = AutofillLinkType.APP },
                    )
                    Text(stringResource(R.string.accounts_autofill_type_app))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = linkType == AutofillLinkType.WEB,
                        onClick = { linkType = AutofillLinkType.WEB },
                    )
                    Text(stringResource(R.string.accounts_autofill_type_web))
                }

                when (linkType) {
                    AutofillLinkType.APP -> {
                        Spacer(modifier = Modifier.size(8.dp))
                        OutlinedTextField(
                            value = packageNameText,
                            onValueChange = { packageNameText = it },
                            label = { Text(stringResource(R.string.accounts_autofill_package_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    AutofillLinkType.WEB -> {
                        Spacer(modifier = Modifier.size(8.dp))
                        OutlinedTextField(
                            value = webDomainText,
                            onValueChange = { webDomainText = it },
                            label = { Text(stringResource(R.string.accounts_autofill_domain_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    AutofillLinkType.NONE -> {}
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val trimmedName = name.trim()
                if (trimmedName.isEmpty()) {
                    error = requiredErrorMessage
                    return@TextButton
                }
                val finalPackageName = if (linkType == AutofillLinkType.APP) packageNameText.trim().ifEmpty { null } else null
                val finalWebDomain = if (linkType == AutofillLinkType.WEB) webDomainText.trim().ifEmpty { null } else null
                onConfirm(trimmedName, issuer.trim(), finalPackageName, finalWebDomain)
            }) {
                Text(stringResource(R.string.accounts_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.accounts_cancel))
            }
        },
    )
}

@Composable
private fun AccountRow(
    account: VaultAccount,
    nowMillis: Long,
    onRenameRequested: () -> Unit,
    onDeleteConfirmed: (VaultAccount) -> Unit,
    onGenerateHotpCode: (VaultAccount) -> String,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = account.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            if (account.issuer.isNotBlank()) {
                Text(
                    text = account.issuer,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }

        if (account.type == "totp") {
            CompactTotpControls(account, nowMillis)
        } else {
            CompactHotpControls(account, onGenerateHotpCode)
        }

        IconButton(onClick = onRenameRequested, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = stringResource(R.string.accounts_rename_content_description, account.name),
                modifier = Modifier.size(18.dp),
            )
        }
        IconButton(onClick = { showDeleteDialog = true }, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = stringResource(R.string.accounts_delete_content_description, account.name),
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
            )
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.accounts_delete_confirm_title, account.name)) },
            text = { Text(stringResource(R.string.accounts_delete_irreversible)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDeleteConfirmed(account)
                }) {
                    Text(stringResource(R.string.accounts_delete_button), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.accounts_cancel))
                }
            },
        )
    }
}

@Composable
private fun CompactTotpControls(account: VaultAccount, nowMillis: Long) {
    val context = LocalContext.current
    val period = account.period ?: 30
    val algorithm = mapAlgorithm(account.algorithm)
    val unreadableText = stringResource(R.string.accounts_unreadable)
    val copyContentDescription = stringResource(R.string.accounts_copy_content_description)

    val result = runCatching {
        val secretBytes = Base32.decode(account.secret.toByteArray(Charsets.US_ASCII))
        TotpGenerator.generate(secretBytes, nowMillis, period, account.digits, algorithm)
    }

    val code = result.getOrNull()
    if (code == null) {
        Text(text = unreadableText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        return
    }

    val secondsRemaining = TotpGenerator.secondsRemaining(nowMillis, period)
    val formattedCode = formatCodeWithSpace(code)
    val isUrgent = secondsRemaining <= 5
    val ringColor = if (isUrgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = formattedCode,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.clickable { copyToClipboardWithAutoClear(context, code) },
        )

        IconButton(onClick = { copyToClipboardWithAutoClear(context, code) }, modifier = Modifier.size(28.dp)) {
            Icon(imageVector = Icons.Filled.ContentCopy, contentDescription = copyContentDescription, modifier = Modifier.size(16.dp))
        }

        Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            androidx.compose.material3.CircularProgressIndicator(
                progress = { secondsRemaining.toFloat() / period.toFloat() },
                modifier = Modifier.size(28.dp),
                color = ringColor,
                strokeWidth = 2.dp,
            )
            Text(text = "$secondsRemaining", style = MaterialTheme.typography.labelSmall, color = ringColor)
        }
    }
}

@Composable
private fun CompactHotpControls(account: VaultAccount, onGenerateHotpCode: (VaultAccount) -> String) {
    val context = LocalContext.current
    var lastCode by remember(account.id) { mutableStateOf<String?>(null) }
    val generateLabel = stringResource(R.string.accounts_generate_button)
    val copyContentDescription = stringResource(R.string.accounts_copy_content_description)

    if (lastCode == null) {
        OutlinedButton(
            onClick = { lastCode = onGenerateHotpCode(account) },
            modifier = Modifier.size(width = 96.dp, height = 32.dp),
        ) {
            Text(generateLabel, style = MaterialTheme.typography.labelSmall)
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = formatCodeWithSpace(lastCode ?: ""),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.clickable {
                    copyToClipboardWithAutoClear(context, lastCode ?: "")
                },
            )
            IconButton(
                onClick = { copyToClipboardWithAutoClear(context, lastCode ?: "") },
                modifier = Modifier.size(28.dp),
            ) {
                Icon(imageVector = Icons.Filled.ContentCopy, contentDescription = copyContentDescription, modifier = Modifier.size(16.dp))
            }
        }
    }
}

private fun copyToClipboardWithAutoClear(context: android.content.Context, code: String) {
    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Code Big MFA", code))
    Toast.makeText(context, "Code copié — effacé dans 30s", Toast.LENGTH_SHORT).show()

    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
        val currentClip = clipboard.primaryClip
        val currentText = currentClip?.getItemAt(0)?.text?.toString()
        if (currentText == code) {
            clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }, 30_000)
}

private fun mapAlgorithm(raw: String): TotpGenerator.Algorithm = when (raw.uppercase()) {
    "SHA-256", "SHA256" -> TotpGenerator.Algorithm.SHA256
    "SHA-512", "SHA512" -> TotpGenerator.Algorithm.SHA512
    else -> TotpGenerator.Algorithm.SHA1
}

private fun formatCodeWithSpace(code: String): String {
    if (code.length <= 3) return code
    val mid = code.length / 2
    return "${code.substring(0, mid)} ${code.substring(mid)}"
}