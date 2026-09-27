// SPDX-License-Identifier: GPL-3.0-only
package com.bigfortravel.bigmfa.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable

/**
 * Couleurs de switch avec un bon contraste en état ETEINT -- les
 * couleurs par défaut de Material3 (gris très sombre sur gris très
 * sombre) sont presque invisibles sur notre thème sombre custom.
 * Utilisé PARTOUT où un Switch apparaît dans l'app, un seul endroit à
 * ajuster si besoin plus tard.
 */
@Composable
fun bigMfaSwitchColors(): SwitchColors = SwitchDefaults.colors(
    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
    checkedTrackColor = MaterialTheme.colorScheme.primary,
    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
    uncheckedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
)