package com.mochistitch.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mochistitch.core.settings.ThemeMode

private val AppShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(32.dp)
)
val StudioLightScheme = lightColorScheme(
    primary = Color(0xFF0E7C6B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA9E8D8),
    onPrimaryContainer = Color(0xFF06382F),
    secondary = Color(0xFFB7791F),
    secondaryContainer = Color(0xFFFFDF9E),
    onSecondaryContainer = Color(0xFF241A00),
    tertiary = Color(0xFF3A6EA5),
    background = Color(0xFFF2F7F5),
    surface = Color(0xFFF2F7F5),
    surfaceVariant = Color(0xFFDCE7E2),
    surfaceContainerHigh = Color(0xFFE4EDE9),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outlineVariant = Color(0xFFB9C8C2)
)

val StudioDarkScheme = darkColorScheme(
    primary = Color(0xFF7FD8C7),
    onPrimary = Color(0xFF06382F),
    primaryContainer = Color(0xFF0B5B4E),
    onPrimaryContainer = Color(0xFFA9E8D8),
    secondary = Color(0xFFFFC44D),
    secondaryContainer = Color(0xFF5A4300),
    onSecondaryContainer = Color(0xFFFFDF9E),
    tertiary = Color(0xFF9CC3FF),
    background = Color(0xFF0C1412),
    surface = Color(0xFF0C1412),
    surfaceVariant = Color(0xFF1A2421),
    surfaceContainerHigh = Color(0xFF222E2A),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outlineVariant = Color(0xFF3A4A45)
)

@Composable
fun StudioTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) StudioDarkScheme else StudioLightScheme,
        shapes = AppShapes,
        content = content
    )
}
