package com.quietledger.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color

val Cream = Color(0xFFFBF8F3)
val Ink = Color(0xFF25211D)
val WarmGray = Color(0xFF746C63)
val Line = Color(0xFFE7E1D9)
val Accent = Color(0xFF6C5845)
val SoftAccent = Color(0xFFF0EAE2)

private val LightSuccess = Color(0xFF236A4A)
private val LightLoss = Color(0xFF9B3F38)
private val DarkBackground = Color(0xFF171411)
private val DarkSurface = Color(0xFF1F1B17)
private val DarkSurfaceVariant = Color(0xFF2D2823)
private val DarkText = Color(0xFFECE5DE)

private val lightColors = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    tertiary = LightSuccess,
    onTertiary = Color.White,
    error = LightLoss,
    onError = Color.White,
    background = Cream,
    onBackground = Ink,
    surface = Cream,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF3EFE9),
    onSurfaceVariant = WarmGray,
    outline = Line
)

private val darkColors = darkColorScheme(
    primary = Color(0xFFD8C2AB),
    onPrimary = Color(0xFF3D2E20),
    tertiary = Color(0xFF8BD8AE),
    onTertiary = Color(0xFF003821),
    error = Color(0xFFFFB4AA),
    onError = Color(0xFF5F160F),
    background = DarkBackground,
    onBackground = DarkText,
    surface = DarkSurface,
    onSurface = DarkText,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFD1C6BA),
    outline = Color(0xFF4D453D)
)

enum class ThemeMode(val label: String) {
    SYSTEM("Use device setting"),
    LIGHT("Light"),
    DARK("Dark");

    companion object {
        fun fromStored(value: String): ThemeMode =
            entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

@Composable
fun QuietLedgerTheme(themeMode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = if (dark) darkColors else lightColors
    MaterialTheme(colorScheme = colors, typography = Typography(), content = content)
}
