package io.homeassistant.companion.android.common.compose.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.PreviewLightDark
import io.homeassistant.companion.android.common.compose.theme.HAColorScheme
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme

/**
 * Displays a floating action button (FAB) with a single icon, themed with the Home
 * Assistant color scheme.
 *
 * @param icon The [ImageVector] icon to be displayed in the button.
 * @param onClick The lambda function to be executed when the button is clicked.
 * @param contentDescription The content description for accessibility purposes.
 * @param modifier Optional [androidx.compose.ui.Modifier] to be applied to the button.
 * @param variant The [ButtonVariant] that determines the button's color scheme. Defaults to [ButtonVariant.PRIMARY].
 */
@Composable
fun HAFloatingActionButton(
    icon: ImageVector,
    onClick: () -> Unit,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.PRIMARY,
) {
    val colors = LocalHAColorScheme.current.fabColorsFromVariant(variant)
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = colors.containerColor,
        contentColor = colors.contentColor,
        content = {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(HADimens.SPACE6),
            )
        },
    )
}

private data class HAFabColors(val containerColor: Color, val contentColor: Color)

private fun HAColorScheme.fabColorsFromVariant(variant: ButtonVariant): HAFabColors = when (variant) {
    ButtonVariant.PRIMARY -> HAFabColors(
        containerColor = colorFillPrimaryLoudResting,
        contentColor = colorOnPrimaryLoud,
    )

    ButtonVariant.NEUTRAL -> HAFabColors(
        containerColor = colorFillNeutralLoudResting,
        contentColor = colorOnNeutralLoud,
    )

    ButtonVariant.DANGER -> HAFabColors(
        containerColor = colorFillDangerLoudResting,
        contentColor = colorOnDangerLoud,
    )

    ButtonVariant.WARNING -> HAFabColors(
        containerColor = colorFillWarningLoudResting,
        contentColor = colorOnWarningLoud,
    )

    ButtonVariant.SUCCESS -> HAFabColors(
        containerColor = colorFillSuccessLoudResting,
        contentColor = colorOnSuccessLoud,
    )
}

@PreviewLightDark
@Composable
private fun HAFloatingActionButtonPreview() {
    HAThemeForPreview {
        Column(verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2)) {
            ButtonVariant.entries.forEach { variant ->
                HAFloatingActionButton(
                    icon = Icons.Default.Add,
                    variant = variant,
                    contentDescription = null,
                    onClick = {},
                )
            }
        }
    }
}
