package io.homeassistant.companion.android.theme

import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme

val Blue = Color(0xFF03A9F4)
val BlueDark = Color(0xFF0288D1)
val Yellow = Color(0xFFF6C344)
val Orange = Color(0xFFFF9800)
val Red = Color(0xFFD32F2F)

val md_theme_dark_primary = Color(0xFF03A9F4)
val md_theme_dark_onPrimary = Color(0xFF00344F)
val md_theme_dark_primaryContainer = Color(0xFF004B70)
val md_theme_dark_onPrimaryContainer = Color(0xFFCAE6FF)
val md_theme_dark_secondary = Color(0xFF96CCFF)
val md_theme_dark_onSecondary = Color(0xFF003353)
val md_theme_dark_secondaryContainer = Color(0xFF004A75)
val md_theme_dark_onSecondaryContainer = Color(0xFFCEE5FF)
val md_theme_dark_tertiary = Color(0xFFF6C344)
val md_theme_dark_onTertiary = Color(0xFF3F2E00)
val md_theme_dark_tertiaryContainer = Color(0xFF5B4300)
val md_theme_dark_onTertiaryContainer = Color(0xFFFFDF9B)
val md_theme_dark_error = Color(0xFFFFB4AB)
val md_theme_dark_onError = Color(0xFF690005)
val md_theme_dark_onBackground = Color(0xFFA6EEFF)
val md_theme_dark_onSurface = Color(0xFFA6EEFF)
val md_theme_dark_onSurfaceVariant = Color(0xFFC1C7CE)
val md_theme_dark_outline = Color(0xFF8B9198)
val md_theme_dark_outlineVariant = Color(0xFF41474D)

internal val wearColorPalette: ColorScheme = ColorScheme(
    primary = Blue,
    secondary = BlueDark,
    tertiary = Yellow,
    tertiaryDim = Orange,
    tertiaryContainer = Color(0xffffdf9b),
    error = Red,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onError = Color.Black,
    background = Color.Black
)

internal val wearColorScheme: ColorScheme = ColorScheme(
    primary = md_theme_dark_primary,
    onPrimary = md_theme_dark_onPrimary,
    primaryContainer = md_theme_dark_primaryContainer,
    onPrimaryContainer = md_theme_dark_onPrimaryContainer,
    secondary = md_theme_dark_secondary,
    onSecondary = md_theme_dark_onSecondary,
    secondaryContainer = md_theme_dark_secondaryContainer,
    onSecondaryContainer = md_theme_dark_onSecondaryContainer,
    tertiary = md_theme_dark_tertiary,
    onTertiary = md_theme_dark_onTertiary,
    tertiaryContainer = md_theme_dark_tertiaryContainer,
    onTertiaryContainer = md_theme_dark_onTertiaryContainer,
    error = md_theme_dark_error,
    onError = md_theme_dark_onError,
    background = Color.Black,
    onBackground = md_theme_dark_onBackground,
    surface = Color.Black,
    onSurface = md_theme_dark_onSurface,
    onSurfaceVariant = md_theme_dark_onSurfaceVariant,
    outline = md_theme_dark_outline,
    outlineVariant = md_theme_dark_outlineVariant
)
