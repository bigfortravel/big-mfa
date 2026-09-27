// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.ui.support

import androidx.compose.foundation.layout.Spacer
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bigfortravel.bigmfa.R
import com.bigfortravel.bigmfa.qr.QrCodeGenerator

object OfficialLinks {
    const val PAYPAL_URL = "https://paypal.me/djeuzong3000"
    const val KOFI_URL = "https://ko-fi.com/djeuzong3000"
    const val WEBSITE_URL = "https://bigfortravel.com"
    const val CONTACT_EMAIL = "contact@bigfortravel.com"
}

@Composable
fun SupportScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val contactSubject = stringResource(R.string.support_contact_subject)

    val paypalQr = remember { QrCodeGenerator.generateQrCodeBitmap(OfficialLinks.PAYPAL_URL, 400) }
    val kofiQr = remember { QrCodeGenerator.generateQrCodeBitmap(OfficialLinks.KOFI_URL, 400) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Text(text = "❤️", style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.support_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.support_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(24.dp))

        DonationCard(
            title = "PayPal",
            qrBitmap = paypalQr,
            buttonText = stringResource(R.string.support_paypal_button),
            urlDisplay = "paypal.me/djeuzong3000",
            onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(OfficialLinks.PAYPAL_URL)))
            },
        )

        Spacer(modifier = Modifier.height(16.dp))

        DonationCard(
            title = "Ko-fi",
            qrBitmap = kofiQr,
            buttonText = stringResource(R.string.support_kofi_button),
            urlDisplay = "ko-fi.com/djeuzong3000",
            onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(OfficialLinks.KOFI_URL)))
            },
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.support_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(16.dp),
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedButton(
            onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(OfficialLinks.WEBSITE_URL)))
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.support_official_site))
        }
        Text(
            text = "bigfortravel.com",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:${OfficialLinks.CONTACT_EMAIL}")
                    putExtra(Intent.EXTRA_SUBJECT, contactSubject)
                }
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.support_contact_us))
        }
        Text(
            text = OfficialLinks.CONTACT_EMAIL,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun DonationCard(
    title: String,
    qrBitmap: android.graphics.Bitmap,
    buttonText: String,
    urlDisplay: String,
    onClick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Image(
                bitmap = qrBitmap.asImageBitmap(),
                contentDescription = "QR code $title",
                modifier = Modifier.size(180.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
                Text(buttonText)
            }
            Text(
                text = urlDisplay,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}