package com.wgs.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val GoldPrimary = Color(0xFFFFB300)
private val GoldOnPrimary = Color(0xFF1A1200)
private val GoldSecondary = Color(0xFF6B5E2D)
private val BackgroundDark = Color(0xFF141210)
private val SurfaceDark = Color(0xFF1E1B17)
private val SurfaceVariant = Color(0xFF2C2820)

private val DarkColorScheme = darkColorScheme(
    primary = GoldPrimary,
    onPrimary = GoldOnPrimary,
    secondary = GoldSecondary,
    background = BackgroundDark,
    surface = SurfaceDark,
    surfaceVariant = SurfaceVariant,
    onBackground = Color(0xFFEDE1C8),
    onSurface = Color(0xFFEDE1C8),
    onSurfaceVariant = Color(0xFFCDBF97),
    error = Color(0xFFFF5449),
    onError = Color(0xFF2D0000)
)

@Composable
fun WGSTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
