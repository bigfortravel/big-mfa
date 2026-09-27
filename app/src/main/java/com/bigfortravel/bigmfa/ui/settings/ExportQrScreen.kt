// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.ui.settings

import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.bigfortravel.bigmfa.R
import com.bigfortravel.bigmfa.qr.QrCodeGenerator
import com.bigfortravel.bigmfa.vault.model.VaultAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@Composable
fun ExportQrScreen(accounts: List<VaultAccount>, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isExportingZip by remember { mutableStateOf(false) }
    val zipErrorMessage = stringResource(R.string.export_qr_zip_error)
    val shareTitle = stringResource(R.string.export_qr_share_title)

    if (accounts.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.export_qr_no_accounts),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        return
    }

    val pagerState = rememberPagerState(pageCount = { accounts.size })

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.export_qr_title, pagerState.currentPage + 1, accounts.size),
            style = MaterialTheme.typography.headlineSmall,
        )

        Spacer(modifier = Modifier.height(16.dp))

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) { page ->
            val account = accounts[page]
            val uri = remember(account.id) { buildOtpAuthUriForAccount(account) }
            val bitmap = remember(account.id) { QrCodeGenerator.generateQrCodeBitmap(uri, 1000) }

            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(text = account.name, style = MaterialTheme.typography.titleLarge)
                if (account.issuer.isNotBlank()) {
                    Text(
                        text = account.issuer,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "QR code de ${account.name}",
                    modifier = Modifier.size(220.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(accounts.size) { index ->
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (pagerState.currentPage == index) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                        ),
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                isExportingZip = true
                scope.launch {
                    try {
                        val zipFile = withContext(Dispatchers.IO) {
                            exportAccountsAsZip(context, accounts)
                        }
                        shareZipFile(context, zipFile, shareTitle)
                    } catch (_: Exception) {
                        Toast.makeText(context, zipErrorMessage, Toast.LENGTH_SHORT).show()
                    } finally {
                        isExportingZip = false
                    }
                }
            },
            enabled = !isExportingZip,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isExportingZip) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(stringResource(R.string.export_qr_zip_button))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.export_qr_close_button))
        }
    }
}

private fun buildOtpAuthUriForAccount(account: VaultAccount): String {
    val type = account.type
    val encodedName = java.net.URLEncoder.encode(account.name, "UTF-8")
    val label = if (account.issuer.isNotBlank()) {
        "${java.net.URLEncoder.encode(account.issuer, "UTF-8")}:$encodedName"
    } else {
        encodedName
    }

    val builder = StringBuilder("otpauth://$type/$label")
    builder.append("?secret=").append(account.secret)
    if (account.issuer.isNotBlank()) {
        builder.append("&issuer=").append(java.net.URLEncoder.encode(account.issuer, "UTF-8"))
    }
    builder.append("&algorithm=").append(account.algorithm)
    builder.append("&digits=").append(account.digits)
    if (type == "hotp") {
        builder.append("&counter=").append(account.counter ?: 0L)
    } else {
        builder.append("&period=").append(account.period ?: 30)
    }
    return builder.toString()
}

private fun exportAccountsAsZip(context: android.content.Context, accounts: List<VaultAccount>): File {
    val zipFile = File(context.cacheDir, "big-mfa-qr-export-${System.currentTimeMillis()}.zip")

    ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
        accounts.forEachIndexed { index, account ->
            val uri = buildOtpAuthUriForAccount(account)
            val bitmap = QrCodeGenerator.generateQrCodeBitmap(uri, 1000)
            val safeFileName = account.name.replace(Regex("[^A-Za-z0-9_-]"), "_")

            zos.putNextEntry(ZipEntry("${index + 1}-$safeFileName.png"))
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, zos)
            zos.closeEntry()
        }
    }

    return zipFile
}

private fun shareZipFile(context: android.content.Context, zipFile: File, shareTitle: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zipFile)
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "application/zip"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, shareTitle))
}