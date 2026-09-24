package com.omniscan.app.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7CF5C4),
    onPrimary = Color(0xFF00382A),
    secondary = Color(0xFF57DBB0),
    background = Color(0xFF0B1210),
    surface = Color(0xFF121A17),
    surfaceVariant = Color(0xFF1B2622)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF10715A),
    secondary = Color(0xFF2E9C7C),
    background = Color(0xFFF6FBF8),
    surface = Color(0xFFFFFFFF)
)

@Composable
fun OmniScanTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
