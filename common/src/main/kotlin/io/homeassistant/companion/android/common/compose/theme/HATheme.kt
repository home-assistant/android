package io.homeassistant.companion.android.common.compose.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

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
        MaterialTheme(content = content)
    }
}
