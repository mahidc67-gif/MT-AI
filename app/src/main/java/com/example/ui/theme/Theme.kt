package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.model.ThemePreference

private val MtAiDarkColorScheme = darkColorScheme(
    primary = MtCyanPrimary,
    onPrimary = Color(0xFF001F29),
    primaryContainer = Color(0xFF0B354D),
    onPrimaryContainer = Color(0xFFB3F4FF),
    secondary = MtIndigoAccent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF25295E),
    onSecondaryContainer = Color(0xFFE0E7FF),
    tertiary = MtVioletGlow,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF34225E),
    onTertiaryContainer = Color(0xFFEDE9FE),
    background = CosmicBg,
    onBackground = CosmicTextPrimary,
    surface = CosmicSurface,
    onSurface = CosmicTextPrimary,
    surfaceVariant = CosmicSurfaceVariant,
    onSurfaceVariant = CosmicTextSecondary,
    surfaceContainerLow = CosmicSurface,
    surfaceContainer = CosmicSurfaceVariant,
    surfaceContainerHigh = CosmicSurfaceElevated,
    outline = CosmicOutline,
    outlineVariant = Color(0xFF1D2A45),
    error = StatusRose,
    onError = Color.White
)

private val MtAiLightColorScheme = lightColorScheme(
    primary = MtCyanDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD2F7FF),
    onPrimaryContainer = Color(0xFF003642),
    secondary = MtIndigoAccent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E7FF),
    onSecondaryContainer = Color(0xFF1E1B4B),
    tertiary = MtVioletGlow,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEDE9FE),
    onTertiaryContainer = Color(0xFF2E1065),
    background = DaylightBg,
    onBackground = DaylightTextPrimary,
    surface = DaylightSurface,
    onSurface = DaylightTextPrimary,
    surfaceVariant = DaylightSurfaceVariant,
    onSurfaceVariant = DaylightTextSecondary,
    surfaceContainerLow = DaylightSurface,
    surfaceContainer = DaylightSurfaceVariant,
    surfaceContainerHigh = Color(0xFFDBE4F3),
    outline = DaylightOutline,
    outlineVariant = Color(0xFFE2E8F0),
    error = Color(0xFFDC2626),
    onError = Color.White
)

private val MtAiShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun MtAiTheme(
    themePreference: ThemePreference = ThemePreference.DARK,
    content: @Composable () -> Unit
) {
    val isDark = when (themePreference) {
        ThemePreference.DARK -> true
        ThemePreference.LIGHT -> false
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
    }
    val colorScheme = if (isDark) MtAiDarkColorScheme else MtAiLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MtAiTypography,
        shapes = MtAiShapes,
        content = content
    )
}
