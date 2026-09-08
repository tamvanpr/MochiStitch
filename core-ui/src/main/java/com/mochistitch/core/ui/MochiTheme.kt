package com.mochistitch.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════════════════════════
// MochiStitch Color Palette — Muted Forest & Sage Green
// Seed: Muted Forest Green (0xFF2E6B40) — tenang, profesional,
// nyaman untuk aplikasi komik / manga
// ═══════════════════════════════════════════════════════════════

// ── Light Theme ──────────────────────────────────────────────
private val LightPrimary = Color(0xFF2E6B40)
private val LightOnPrimary = Color(0xFFFFFFFF)
private val LightPrimaryContainer = Color(0xFFB1F3BD)
private val LightOnPrimaryContainer = Color(0xFF00210B)

private val LightSecondary = Color(0xFF526352)
private val LightOnSecondary = Color(0xFFFFFFFF)
private val LightSecondaryContainer = Color(0xFFD5E8D3)
private val LightOnSecondaryContainer = Color(0xFF101F12)

private val LightTertiary = Color(0xFF386567)
private val LightOnTertiary = Color(0xFFFFFFFF)
private val LightTertiaryContainer = Color(0xFFBCEBEB)
private val LightOnTertiaryContainer = Color(0xFF002021)

private val LightError = Color(0xFFBA1A1A)
private val LightOnError = Color(0xFFFFFFFF)
private val LightErrorContainer = Color(0xFFFFDAD6)
private val LightOnErrorContainer = Color(0xFF410002)

private val LightSurface = Color(0xFFF7FBF4)
private val LightSurfaceVariant = Color(0xFFDEE5DA)
private val LightSurfaceContainer = Color(0xFFEDF4EA)
private val LightSurfaceContainerHigh = Color(0xFFE7EEE4)
private val LightSurfaceContainerHighest = Color(0xFFE1E8DF)
private val LightOnSurface = Color(0xFF181D18)
private val LightOnSurfaceVariant = Color(0xFF424941)
private val LightOutline = Color(0xFF727970)
private val LightOutlineVariant = Color(0xFFC2C9BD)
private val LightBackground = Color(0xFFF7FBF4)
private val LightInverseSurface = Color(0xFF2D322C)
private val LightInverseOnSurface = Color(0xFFEEF2E9)
private val LightInversePrimary = Color(0xFF96D6A2)

// ── Dark Theme ───────────────────────────────────────────────
private val DarkPrimary = Color(0xFF96D6A2)
private val DarkOnPrimary = Color(0xFF003916)
private val DarkPrimaryContainer = Color(0xFF13522A)
private val DarkOnPrimaryContainer = Color(0xFFB1F3BD)

private val DarkSecondary = Color(0xFFB9CCB7)
private val DarkOnSecondary = Color(0xFF253425)
private val DarkSecondaryContainer = Color(0xFF3B4B3B)
private val DarkOnSecondaryContainer = Color(0xFFD5E8D3)

private val DarkTertiary = Color(0xFFA0CFD0)
private val DarkOnTertiary = Color(0xFF003738)
private val DarkTertiaryContainer = Color(0xFF1E4D4E)
private val DarkOnTertiaryContainer = Color(0xFFBCEBEB)

private val DarkError = Color(0xFFFFB4AB)
private val DarkOnError = Color(0xFF690005)
private val DarkErrorContainer = Color(0xFF93000A)
private val DarkOnErrorContainer = Color(0xFFFFDAD6)

private val DarkSurface = Color(0xFF101411)
private val DarkSurfaceVariant = Color(0xFF424941)
private val DarkSurfaceContainer = Color(0xFF1C211D)
private val DarkSurfaceContainerHigh = Color(0xFF272B27)
private val DarkSurfaceContainerHighest = Color(0xFF323631)
private val DarkOnSurface = Color(0xFFE0E4DC)
private val DarkOnSurfaceVariant = Color(0xFFC2C9BD)
private val DarkOutline = Color(0xFF8C9388)
private val DarkOutlineVariant = Color(0xFF424941)
private val DarkBackground = Color(0xFF101411)
private val DarkInverseSurface = Color(0xFFE0E4DC)
private val DarkInverseOnSurface = Color(0xFF2D322C)
private val DarkInversePrimary = Color(0xFF2E6B40)

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
