package com.mochistitch.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════════════════════════
// MochiStitch Color Palette — Warm Amber / Manga Utility
// Seed: earthy amber-brown untuk nuansa komik/manga
// ═══════════════════════════════════════════════════════════════

// ── Light Theme ──────────────────────────────────────────────
private val LightPrimary = Color(0xFF7A5230)
private val LightOnPrimary = Color(0xFFFFFFFF)
private val LightPrimaryContainer = Color(0xFFFFE0C4)
private val LightOnPrimaryContainer = Color(0xFF3D1A00)

private val LightSecondary = Color(0xFF6B5B4E)
private val LightOnSecondary = Color(0xFFFFFFFF)
private val LightSecondaryContainer = Color(0xFFF5DECA)
private val LightOnSecondaryContainer = Color(0xFF251A10)

private val LightTertiary = Color(0xFF5B6B4E)
private val LightOnTertiary = Color(0xFFFFFFFF)
private val LightTertiaryContainer = Color(0xFFDCE7C8)
private val LightOnTertiaryContainer = Color(0xFF18200D)

private val LightError = Color(0xFFBA1A1A)
private val LightOnError = Color(0xFFFFFFFF)
private val LightErrorContainer = Color(0xFFFFDAD6)
private val LightOnErrorContainer = Color(0xFF410002)

private val LightSurface = Color(0xFFFFFAF7)
private val LightSurfaceVariant = Color(0xFFF5E6D9)
private val LightSurfaceContainer = Color(0xFFFAF0E8)
private val LightSurfaceContainerHigh = Color(0xFFF9EFE7)
private val LightSurfaceContainerHighest = Color(0xFFF3E8DF)
private val LightOnSurface = Color(0xFF1F1B18)
private val LightOnSurfaceVariant = Color(0xFF52463C)
private val LightOutline = Color(0xFF84766A)
private val LightOutlineVariant = Color(0xFFD6C4B5)
private val LightBackground = Color(0xFFFFFAF7)
private val LightInverseSurface = Color(0xFF342E2A)
private val LightInverseOnSurface = Color(0xFFF5EDE7)
private val LightInversePrimary = Color(0xFFEEBFA0)

// ── Dark Theme ───────────────────────────────────────────────
private val DarkPrimary = Color(0xFFFFB87A)
private val DarkOnPrimary = Color(0xFF5A2D00)
private val DarkPrimaryContainer = Color(0xFF7A4520)
private val DarkOnPrimaryContainer = Color(0xFFFFE0C4)

private val DarkSecondary = Color(0xFFD9C2B0)
private val DarkOnSecondary = Color(0xFF3B2E23)
private val DarkSecondaryContainer = Color(0xFF524337)
private val DarkOnSecondaryContainer = Color(0xFFF5DECA)

private val DarkTertiary = Color(0xFFBFCBAE)
private val DarkOnTertiary = Color(0xFF2D361F)
private val DarkTertiaryContainer = Color(0xFF434D36)
private val DarkOnTertiaryContainer = Color(0xFFDCE7C8)

private val DarkError = Color(0xFFFFB4AB)
private val DarkOnError = Color(0xFF690005)
private val DarkErrorContainer = Color(0xFF93000A)
private val DarkOnErrorContainer = Color(0xFFFFDAD6)

private val DarkSurface = Color(0xFF1F1B18)
private val DarkSurfaceVariant = Color(0xFF3D332B)
private val DarkSurfaceContainer = Color(0xFF2A231D)
private val DarkSurfaceContainerHigh = Color(0xFF342C25)
private val DarkSurfaceContainerHighest = Color(0xFF3F362E)
private val DarkOnSurface = Color(0xFFE8DED6)
private val DarkOnSurfaceVariant = Color(0xFFB8AA9F)
private val DarkOutline = Color(0xFF84766A)
private val DarkOutlineVariant = Color(0xFF3D332B)
private val DarkBackground = Color(0xFF1F1B18)
private val DarkInverseSurface = Color(0xFFE8DED6)
private val DarkInverseOnSurface = Color(0xFF342E2A)
private val DarkInversePrimary = Color(0xFF8B5E3C)

val LightMochiStitchColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    background = LightBackground,
    inverseSurface = LightInverseSurface,
    inverseOnSurface = LightInverseOnSurface,
    inversePrimary = LightInversePrimary
)

val DarkMochiStitchColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    background = DarkBackground,
    inverseSurface = DarkInverseSurface,
    inverseOnSurface = DarkInverseOnSurface,
    inversePrimary = DarkInversePrimary
)

@Composable
fun MochiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkMochiStitchColorScheme else LightMochiStitchColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

