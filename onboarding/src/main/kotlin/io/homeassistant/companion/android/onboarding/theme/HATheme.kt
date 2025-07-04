package io.homeassistant.companion.android.onboarding.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun HATheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        content = content,
        colorScheme = if (darkTheme) DarkAndroidColorScheme else LightAndroidColorScheme,
    )
}
