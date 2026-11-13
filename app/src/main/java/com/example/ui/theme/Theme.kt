package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.example.data.ThemeMode

private val DarkColorScheme = darkColorScheme(
    primary = Indigo400,
    onPrimary = Color.White,
    primaryContainer = Indigo700,
    onPrimaryContainer = Indigo100,
    secondary = CyanAccent,
    onSecondary = DarkBgTop,
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = Indigo100,
    tertiary = VioletAccent,
    background = DarkBgTop,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkGlassBorder,
    error = RoseError,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = Indigo600,
    onPrimary = Color.White,
    primaryContainer = Indigo100,
    onPrimaryContainer = Indigo900,
    secondary = Indigo500,
    onSecondary = Color.White,
    secondaryContainer = LightSurfaceVariant,
    onSecondaryContainer = Indigo900,
    tertiary = VioletAccent,
    background = LightBgTop,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightGlassBorder,
    error = RoseError,
    onError = Color.White
)

data class ArLabsExtendedColors(
    val isDark: Boolean,
    val bgGradientTop: Color,
    val bgGradientBottom: Color,
    val glassSurface: Color,
    val glassSurfaceElevated: Color,
    val glassBorder: Color,
    val success: Color,
    val warning: Color,
    val highlightBg: Color
)

val LocalArLabsColors = staticCompositionLocalOf {
    ArLabsExtendedColors(
        isDark = true,
        bgGradientTop = DarkBgTop,
        bgGradientBottom = DarkBgBottom,
        glassSurface = Color(0xB8141B38),
        glassSurfaceElevated = Color(0xD91A2347),
        glassBorder = DarkGlassBorder,
        success = EmeraldSuccess,
        warning = AmberWarn,
        highlightBg = Color(0x4D6366F1)
    )
}

@Composable
fun ArLabsTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    val colorScheme = if (useDark) DarkColorScheme else LightColorScheme
    val extended = if (useDark) {
        ArLabsExtendedColors(
            isDark = true,
            bgGradientTop = DarkBgTop,
            bgGradientBottom = DarkBgBottom,
            glassSurface = Color(0xB8141B38),
            glassSurfaceElevated = Color(0xE01C254B),
            glassBorder = Color(0x3D818CF8),
            success = EmeraldSuccess,
            warning = AmberWarn,
            highlightBg = Color(0x526366F1)
        )
    } else {
        ArLabsExtendedColors(
            isDark = false,
            bgGradientTop = LightBgTop,
            bgGradientBottom = LightBgBottom,
            glassSurface = Color(0xCCFFFFFF),
            glassSurfaceElevated = Color(0xF0FFFFFF),
            glassBorder = Color(0x404F46E5),
            success = EmeraldSuccess,
            warning = AmberWarn,
            highlightBg = Color(0x2E4F46E5)
        )
    }

    CompositionLocalProvider(LocalArLabsColors provides extended) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
