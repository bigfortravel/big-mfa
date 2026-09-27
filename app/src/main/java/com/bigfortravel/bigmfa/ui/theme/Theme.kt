package com.bigfortravel.bigmfa.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// Un seul thème sombre, comme côté Chrome — pas de mode clair pour
// l'instant, pas de couleur dynamique Material You (préserverait
// l'identité de marque plutôt que de la remplacer par le fond d'écran
// de l'utilisateur).
private val BigMfaDarkColorScheme = darkColorScheme(
    primary = BigMfaAccent,
    onPrimary = BigMfaText,
    secondary = BigMfaAccent2,
    onSecondary = BigMfaBackground,
    tertiary = BigMfaWarn,
    onTertiary = BigMfaBackground,
    background = BigMfaBackground,
    onBackground = BigMfaText,
    surface = BigMfaSurface,
    onSurface = BigMfaText,
    surfaceVariant = BigMfaSurface2,
    onSurfaceVariant = BigMfaMuted,
    outline = BigMfaBorder,
    error = BigMfaDanger,
    onError = BigMfaText,
)

@Composable
fun BigMFATheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BigMfaDarkColorScheme,
        typography = Typography,
        content = content,
    )
}