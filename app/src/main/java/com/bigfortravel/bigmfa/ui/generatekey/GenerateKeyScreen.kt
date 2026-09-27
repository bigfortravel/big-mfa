// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.ui.generatekey

import androidx.compose.foundation.clickable
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bigfortravel.bigmfa.R
import com.bigfortravel.bigmfa.qr.QrCodeGenerator
import com.bigfortravel.bigmfa.ui.addaccount.OptionDropdown
import com.bigfortravel.bigmfa.ui.theme.BigMfaSuccess

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenerateKeyScreen(viewModel: GenerateKeyViewModel, modifier: Modifier = Modifier) {
    val secret by viewModel.secret.collectAsState()
    val context = LocalContext.current

    var name by remember { mutableStateOf("") }
    var issuer by remember { mutableStateOf("") }
    var showAdvanced by remember { mutableStateOf(false) }
    var isHotp by remember { mutableStateOf(false) }
    var algorithm by remember { mutableStateOf("SHA-1") }
    var digits by remember { mutableStateOf(6) }
    var period by remember { mutableStateOf("30") }

    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(Unit) {
        if (secret.isEmpty()) viewModel.generateNewKey()
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
            text = stringResource(R.string.generate_key_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = formatSecretForDisplay(secret),
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                color = BigMfaSuccess,
                modifier = Modifier.padding(16.dp),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = { viewModel.generateNewKey(); qrBitmap = null },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.generate_key_generate))
            }
            OutlinedButton(
                onClick = {
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Clé Big MFA", secret))
                    Toast.makeText(context, context.getString(R.string.generate_key_copied), Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.generate_key_copy))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.add_account_name_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = issuer,
            onValueChange = { issuer = it },
            label = { Text(stringResource(R.string.add_account_issuer_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .then(
                    Modifier.clickable { showAdvanced = !showAdvanced },
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.add_account_advanced_options),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp),
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

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                val uri = viewModel.buildOtpAuthUri(
                    name = name,
                    issuer = issuer,
                    isHotp = isHotp,
                    algorithm = algorithm,
                    digits = digits,
                    period = period.toIntOrNull() ?: 30,
                )
                qrBitmap = QrCodeGenerator.generateQrCodeBitmap(uri)
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.generate_key_show_qr))
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    val bitmapToShow = qrBitmap
    if (bitmapToShow != null) {
        AlertDialog(
            onDismissRequest = { qrBitmap = null },
            title = { Text(stringResource(R.string.generate_key_qr_title)) },
            text = {
                Image(
                    bitmap = bitmapToShow.asImageBitmap(),
                    contentDescription = stringResource(R.string.generate_key_qr_title),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = { qrBitmap = null }) {
                    Text(stringResource(R.string.generate_key_close))
                }
            },
        )
    }
}

private fun formatSecretForDisplay(secret: String): String =
    secret.chunked(4).joinToString(" ")