package com.mochistitch.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════════════════════════
// MochiStitch Color Palette — Utilitarian & Clean
// Seed: Amber/Warm tone untuk nuansa manga/comic
// ═══════════════════════════════════════════════════════════════

// Light Theme
private val LightPrimary = Color(0xFF8B5E3C)  // Warm amber-brown
private val LightOnPrimary = Color(0xFFFFFFFF)
private val LightPrimaryContainer = Color(0xFFFFE0C4)
private val LightOnPrimaryContainer = Color(0xFF3D1A00)

private val LightSecondary = Color(0xFF6B5B4E)
private val LightOnSecondary = Color(0xFFFFFFFF)
private val LightSecondaryContainer = Color(0xFFF5DECA)
private val LightOnSecondaryContainer = Color(0xFF251A10)

private val LightSurface = Color(0xFFFFFAF7)
private val LightSurfaceVariant = Color(0xFFF5E6D9)
private val LightOnSurface = Color(0xFF1F1B18)
private val LightOnSurfaceVariant = Color(0xFF52463C)

private val LightOutline = Color(0xFF84766A)
private val LightBackground = Color(0xFFFFFAF7)

// Dark Theme
private val DarkPrimary = Color(0xFFFFB87A)
private val DarkOnPrimary = Color(0xFF5A2D00)
private val DarkPrimaryContainer = Color(0xFF7A4520)
private val DarkOnPrimaryContainer = Color(0xFFFFE0C4)

private val DarkSecondary = Color(0xFFD9C2B0)
private val DarkOnSecondary = Color(0xFF3B2E23)
private val DarkSecondaryContainer = Color(0xFF524337)
private val DarkOnSecondaryContainer = Color(0xFFF5DECA)

private val DarkSurface = Color(0xFF1F1B18)
private val DarkSurfaceVariant = Color(0xFF3D332B)
private val DarkOnSurface = Color(0xFFE8DED6)
private val DarkOnSurfaceVariant = Color(0xFFB8AA9F)

private val DarkOutline = Color(0xFF84766A)
private val DarkBackground = Color(0xFF1F1B18)

val LightMochiStitchColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    background = LightBackground
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
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    background = DarkBackground
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
