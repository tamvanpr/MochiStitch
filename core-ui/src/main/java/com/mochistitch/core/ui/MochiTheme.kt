package com.mochistitch.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.mochistitch.core.settings.ThemeMode

val LightMochiStitchColorScheme = lightColorScheme(
    primary = Color(0xFF2E6B40),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB9E9C2),
    onPrimaryContainer = Color(0xFF0B3D20),
    secondary = Color(0xFF4E6353),
    secondaryContainer = Color(0xFFD2E8D6),
    onSecondaryContainer = Color(0xFF0C1F14),
    background = Color(0xFFF6FBF4),
    surface = Color(0xFFF6FBF4),
    surfaceVariant = Color(0xFFDFE9DD),
    surfaceContainerHigh = Color(0xFFE7EFE5),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outlineVariant = Color(0xFFC1CCC0)
)

val DarkMochiStitchColorScheme = darkColorScheme(
    primary = Color(0xFF96D6A2),
    onPrimary = Color(0xFF0B3D20),
    primaryContainer = Color(0xFF245433),
    onPrimaryContainer = Color(0xFFB9E9C2),
    secondary = Color(0xFFB6CCB9),
    secondaryContainer = Color(0xFF33473A),
    onSecondaryContainer = Color(0xFFD2E8D6),
    background = Color(0xFF0E1510),
    surface = Color(0xFF0E1510),
    surfaceVariant = Color(0xFF1B241D),
    surfaceContainerHigh = Color(0xFF222C24),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outlineVariant = Color(0xFF3D4A3F)
)

@Composable
fun MochiTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) DarkMochiStitchColorScheme else LightMochiStitchColorScheme,
        content = content
    )
}
