package io.homeassistant.companion.android.common.compose.composable

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme

/**
 * Composable function that displays a circular progress indicator with Home Assistant styling.
 * This is used to indicate an indeterminate loading state.
 *
 * @param modifier The modifier to be applied to the indicator.
 * @param strokeWidth The stroke width of the indicator. Defaults to [ProgressIndicatorDefaults.CircularStrokeWidth].
 */
@Composable
fun HALoading(modifier: Modifier = Modifier, strokeWidth: Dp = ProgressIndicatorDefaults.CircularStrokeWidth) {
    CircularProgressIndicator(
        modifier = modifier,
        strokeWidth = strokeWidth,
        color = LocalHAColorScheme.current.colorBorderPrimaryLoud,
        trackColor = LocalHAColorScheme.current.colorBorderNeutralQuiet,
        gapSize = (-10).dp, // Remove the strokeCap of the background
    )
}

/**
 * Composable function that displays a circular progress indicator with Home Assistant styling.
 *
 * @param progress A lambda function that returns the current progress value (between 0.0f and 1.0f).
 * @param modifier Optional [Modifier] to be applied to the progress indicator.
 * @param strokeWidth Optional stroke width for the progress indicator. Defaults to [ProgressIndicatorDefaults.CircularStrokeWidth].
 */
@Composable
fun HAProgress(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = ProgressIndicatorDefaults.CircularStrokeWidth,
) {
    CircularProgressIndicator(
        progress = progress,
        modifier = modifier,
        strokeWidth = strokeWidth,
        color = LocalHAColorScheme.current.colorBorderPrimaryLoud,
        trackColor = LocalHAColorScheme.current.colorBorderNeutralQuiet,
        gapSize = (-10).dp, // Remove the strokeCap of the background
    )
}
