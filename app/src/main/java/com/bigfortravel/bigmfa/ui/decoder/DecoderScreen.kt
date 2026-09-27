// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.ui.decoder

import androidx.compose.ui.res.stringResource
import com.bigfortravel.bigmfa.R
import androidx.compose.foundation.layout.width
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.bigfortravel.bigmfa.otp.GoogleAuthMigrationParser
import com.bigfortravel.bigmfa.qr.QrImageDecoder
import com.bigfortravel.bigmfa.ui.common.PasswordTextField

@Composable
fun DecoderScreen(viewModel: DecoderViewModel, modifier: Modifier = Modifier) {
    val secretText by viewModel.secretText.collectAsState()
    val generatedCode by viewModel.generatedCode.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val context = LocalContext.current

    var migratedAccounts by remember { mutableStateOf<List<GoogleAuthMigrationParser.MigratedAccount>?>(null) }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            val inputStream = context.contentResolver.openInputStream(uri)
            val bitmap = inputStream?.use { BitmapFactory.decodeStream(it) }
            if (bitmap != null) {
                QrImageDecoder.decode(bitmap) { rawValue ->
                    if (rawValue != null) {
                        if (GoogleAuthMigrationParser.isMigrationUri(rawValue)) {
                            try {
                                migratedAccounts = GoogleAuthMigrationParser.parse(rawValue)
                            } catch (_: Exception) {
                                Toast.makeText(context, context.getString(R.string.decoder_no_qr_found), Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            viewModel.onQrImageDecoded(rawValue)
                        }
                    } else {
                        Toast.makeText(context, context.getString(R.string.decoder_no_qr_found), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        OutlinedButton(
            onClick = {
                imagePicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Filled.QrCode, contentDescription = null)
                Spacer(modifier = Modifier.height(0.dp).width(8.dp))
                Text(stringResource(R.string.decoder_import_qr))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.decoder_secret_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(4.dp))

        PasswordTextField(
            value = secretText,
            onValueChange = { viewModel.onSecretTextChanged(it) },
            label = "",
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = generatedCode?.let { formatCodeWithSpace(it) } ?: stringResource(R.string.decoder_code_placeholder),
                    style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace),
                )
                if (generatedCode != null) {
                    IconButton(onClick = {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Code Big MFA", generatedCode))
                        Toast.makeText(context, context.getString(R.string.decoder_code_copied), Toast.LENGTH_SHORT).show()
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            val current = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                            if (current == generatedCode) {
                                clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                            }
                        }, 30_000)
                    }) {
                        Icon(imageVector = Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.decoder_copy_content_description))
                    }
                }
            }
        }

        val currentError = errorMessage
        if (currentError != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = currentError.asString(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { viewModel.generateCode() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.decoder_generate_code))
        }
    }

    val currentMigratedAccounts = migratedAccounts
    if (currentMigratedAccounts != null) {
        AlertDialog(
            onDismissRequest = { migratedAccounts = null },
            title = { Text(stringResource(R.string.decoder_migration_detected_title)) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.decoder_migration_detected_desc, currentMigratedAccounts.size),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.heightIn(max = 350.dp)) {
                        items(currentMigratedAccounts.size) { index ->
                            val account = currentMigratedAccounts[index]
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.onSecretTextChanged(bytesToBase32(account.secret))
                                        migratedAccounts = null
                                    }
                                    .padding(vertical = 8.dp),
                            ) {
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
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { migratedAccounts = null }) {
                    Text(stringResource(R.string.decoder_migration_cancel))
                }
            },
        )
    }
}

private fun formatCodeWithSpace(code: String): String {
    if (code.length <= 3) return code
    val mid = code.length / 2
    return "${code.substring(0, mid)} ${code.substring(mid)}"
}

private fun bytesToBase32(bytes: ByteArray): String =
    org.bouncycastle.util.encoders.Base32.toBase32String(bytes)