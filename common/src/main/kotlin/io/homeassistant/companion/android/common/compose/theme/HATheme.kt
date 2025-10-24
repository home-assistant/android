package io.homeassistant.companion.android.common.compose.theme

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier

/**
 * A custom theme built on top of [androidx.compose.material3.MaterialTheme] with Home Assistant colors.
 *
 * @param darkTheme Whether to use the dark theme. Defaults to the system setting.
 * @param content The content of the theme.
 */
@Composable
fun HATheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalHAColorScheme provides if (darkTheme) DarkHAColorScheme else LightHAColorScheme,
    ) {
        MaterialTheme(
            content = content,
            colorScheme = MaterialTheme.colorScheme.copy(
                // Override the surface so that Composable like Scaffold use the right background color without
                // manually injecting the color.
                surface = LocalHAColorScheme.current.colorSurfaceDefault,
                background = LocalHAColorScheme.current.colorSurfaceDefault,
                // Used by ModalBottomSheetDefaults.containerColor
                surfaceContainerLow = LocalHAColorScheme.current.colorSurfaceDefault,
                // Used for text selection
                primary = LocalHAColorScheme.current.colorOnPrimaryNormal,
            ),
        )
    }
}

/**
 * Small wrapper around [HATheme] for previews/screenshot tests which:
 * - applies the theme
 * - adds a container with a background color around the content
 */
@SuppressLint("ComposeModifierMissing")
@Composable
fun HAThemeForPreview(content: @Composable BoxScope.() -> Unit) {
    HATheme {
        Box(modifier = Modifier.background(LocalHAColorScheme.current.colorSurfaceDefault), content = content)
    }
}
