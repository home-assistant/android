package io.shpro.companion.android.util.compose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

val colorPrimary = Color(0xFF0079a6)
val colorPrimaryDark = Color(0xFF0288D1)
val darkColorBackground = Color(0xFF1C1C1C)

const val STEP_SCREEN_MAX_WIDTH = 600

private val haLightColors = lightColors(
    primary = colorPrimary,
    primaryVariant = colorPrimaryDark,
    secondary = colorPrimary,
    secondaryVariant = colorPrimary,
    onPrimary = Color.White,
    onSecondary = Color.White
)
private val haDarkColors = darkColors(
    primary = colorPrimary,
    primaryVariant = colorPrimaryDark,
    secondary = colorPrimary,
    secondaryVariant = colorPrimary,
    background = darkColorBackground,
    onPrimary = Color.White,
    onSecondary = Color.White
)

/**
 * A Compose [MaterialTheme] version of the app's XML theme. This achieves the same goal as the
 * (now deprecated) [com.google.accompanist.themeadapter.material.MdcTheme].
 */
@Composable
fun HomeAssistantAppTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colors = if (isSystemInDarkTheme()) haDarkColors else haLightColors
    ) {
        // Copied from MdcTheme:
        CompositionLocalProvider(
            LocalContentColor provides MaterialTheme.colors.onBackground,
            content = content
        )
    }
}
