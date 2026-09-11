package com.tejgokani.strata.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * STRATA is a full-black instrument panel by design (plan §8) — it does not adapt to a light
 * system theme; the brutalist data-vault aesthetic is a deliberate, singular visual identity.
 */
private val StrataColorScheme = darkColorScheme(
    background = StrataColors.Bg,
    surface = StrataColors.Surface,
    onBackground = StrataColors.Fg,
    onSurface = StrataColors.Fg,
    primary = StrataColors.Signal,
    onPrimary = StrataColors.Bg,
    secondary = StrataColors.Cold,
    error = StrataColors.Alert,
    outline = StrataColors.Hairline,
)

@Composable
fun StrataTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = StrataColorScheme,
        typography = StrataType.materialTypography(),
        content = content,
    )
}
