package io.homeassistant.companion.android.theme

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.MaterialTheme

@Composable
fun WearAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = wearColorScheme,
        content = content,
    )
}
