package io.homeassistant.companion.android.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun HATheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalHAColorScheme provides if (darkTheme) DarkHAColorScheme else LightHAColorScheme,
    ) {
        MaterialTheme(
            content = content,
            colorScheme = if (darkTheme) DarkAndroidColorScheme else LightAndroidColorScheme,
        )
    }
}
