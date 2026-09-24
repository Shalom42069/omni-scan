package com.omniscan.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColors(
    primary = Color(0xFF7CF5C4),
    onPrimary = Color(0xFF00382A),
    secondary = Color(0xFF57DBB0),
    background = Color(0xFF0B1210),
    surface = Color(0xFF121A17),
    onBackground = Color(0xFFE6EDE9),
    onSurface = Color(0xFFE6EDE9)
)

private val LightColors = lightColors(
    primary = Color(0xFF10715A),
    secondary = Color(0xFF2E9C7C),
    background = Color(0xFFF6FBF8),
    surface = Color(0xFFFFFFFF)
)

/** Farbe für dezente/sekundäre Beschriftungen, ersetzt onSurfaceVariant aus Material3. */
val MaterialTheme.mutedText: Color
    @Composable get() = this.colors.onSurface.copy(alpha = 0.6f)

/** Farbe für Karten-Hintergründe, ersetzt surfaceVariant aus Material3. */
val MaterialTheme.cardBackground: Color
    @Composable get() = if (this.colors.isLight) Color(0xFFEFF5F2) else Color(0xFF1B2622)

@Composable
fun OmniScanTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) DarkColors else LightColors
    MaterialTheme(colors = scheme, content = content)
}
