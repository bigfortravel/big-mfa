// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.ui.common

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.bigfortravel.bigmfa.R
import kotlin.math.roundToInt

/**
 * Langues actuellement disponibles -- code langue -> nom natif de la
 * langue elle-même (jamais traduit, une langue s'écrit toujours dans sa
 * propre écriture, peu importe la langue active de l'app).
 */
val SUPPORTED_LANGUAGES = listOf(
    "fr" to "Français",
    "en" to "English",
    "es" to "Español",
    "pt" to "Português",
    "de" to "Deutsch",
    "it" to "Italiano",
    "nl" to "Nederlands",
    "pl" to "Polski",
    "ro" to "Română",
    "sv" to "Svenska",
    "da" to "Dansk",
    "fi" to "Suomi",
    "cs" to "Čeština",
    "sk" to "Slovenčina",
    "el" to "Ελληνικά",
    "ru" to "Русский",
    "uk" to "Українська",
    "tr" to "Türkçe",
    "ja" to "日本語",
    "ko" to "한국어",
    "zh-CN" to "简体中文",
    "in" to "Bahasa Indonesia",
    "hr" to "Hrvatski",
    "sl" to "Slovenščina",
    "bg" to "Български",
    "zh-TW" to "繁體中文",
    "vi" to "Tiếng Việt",
    "ms" to "Bahasa Melayu",
    "th" to "ไทย",
    "fil" to "Filipino",
    "hi" to "हिन्दी",
    "ar" to "العربية",
    "iw" to "עברית",
    "fa" to "فارسی",
    "et" to "Eesti",
    "lv" to "Latviešu",
    "lt" to "Lietuvių",
    "sr" to "Српски",
    "af" to "Afrikaans",
)

/** Libellé courant -- réutilisé partout où la langue active doit
 *  s'afficher (footer, Paramètres). */
@Composable
fun currentLanguageLabel(): String {
    val currentTag = AppCompatDelegate.getApplicationLocales().takeIf { !it.isEmpty }?.get(0)?.language
    val languageName = SUPPORTED_LANGUAGES.firstOrNull { it.first == currentTag }?.second
    return if (languageName != null) "🌐 $languageName" else stringResource(R.string.language_option_auto)
}

/** Boîte de dialogue partagée -- réutilisée partout où changer de
 *  langue doit être possible (footer, Paramètres). */
@Composable
fun LanguageSelectorDialog(onDismiss: () -> Unit) {
    val currentTag = AppCompatDelegate.getApplicationLocales().takeIf { !it.isEmpty }?.get(0)?.language
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.language_dialog_title)) },
        text = {
            Row(modifier = Modifier.height(350.dp)) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(scrollState),
                ) {
                    LanguageOptionRow(
                        label = stringResource(R.string.language_option_auto),
                        selected = currentTag == null,
                        onClick = {
                            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
                            onDismiss()
                        },
                    )
                    SUPPORTED_LANGUAGES.forEach { (tag, name) ->
                        LanguageOptionRow(
                            label = name,
                            selected = currentTag == tag,
                            onClick = {
                                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
                                onDismiss()
                            },
                        )
                    }
                }
                ScrollbarIndicator(
                    scrollState = scrollState,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .fillMaxHeight()
                        .width(6.dp),
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        },
    )
}

/**
 * Barre grise persistante rappelant qu'il y a du contenu à faire défiler
 * -- pas seulement l'indicateur système transitoire (souvent invisible
 * ou trop bref sur certaines surcouches Android). Ne s'affiche que si le
 * contenu dépasse effectivement la hauteur visible.
 */
@Composable
private fun ScrollbarIndicator(scrollState: ScrollState, modifier: Modifier = Modifier) {
    if (scrollState.maxValue <= 0) return

    BoxWithConstraints(modifier = modifier) {
        val trackHeightPx = constraints.maxHeight.toFloat()
        val thumbHeightPx = trackHeightPx * 0.2f
        val progress = scrollState.value.toFloat() / scrollState.maxValue.toFloat()
        val offsetPx = (trackHeightPx - thumbHeightPx) * progress

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .background(
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f),
                    RoundedCornerShape(2.dp),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, offsetPx.roundToInt()) }
                .height(with(LocalDensity.current) { thumbHeightPx.toDp() })
                .background(
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    RoundedCornerShape(2.dp),
                ),
        )
    }
}

@Composable
private fun LanguageOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(text = label, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
fun BigMfaAuthFooter(onSupportClicked: () -> Unit = {}, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var showLanguageDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            TextButton(onClick = onSupportClicked) {
                Text(stringResource(R.string.footer_support))
            }
            TextButton(onClick = {
                Toast.makeText(context, "Contact — bientôt disponible", Toast.LENGTH_SHORT).show()
            }) {
                Text(stringResource(R.string.footer_contact))
            }
        }

        TextButton(
            onClick = { showLanguageDialog = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(currentLanguageLabel())
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = stringResource(R.string.footer_gplv3),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }

    if (showLanguageDialog) {
        LanguageSelectorDialog(onDismiss = { showLanguageDialog = false })
    }
}