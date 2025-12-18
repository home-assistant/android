package io.homeassistant.companion.android.common.compose.composable

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.compose.theme.HABrandColors
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HARadius
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme

/**
 * Displays a horizontal banner component with rounded corners and neutral background
 * This composable provides a container for informational content with consistent styling
 * using [io.homeassistant.companion.android.common.compose.theme.HAColorScheme].
 *
 * @param modifier Optional [androidx.compose.ui.Modifier] to be applied to the banner.
 * @param content The composable content to be displayed inside the banner, provided as a [RowScope] lambda.
 */
@Composable
fun HABanner(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier
            .background(
                color = LocalHAColorScheme.current.colorFillNeutralNormalResting,
                shape = RoundedCornerShape(
                    HARadius.XL,
                ),
            )
            .padding(HADimens.SPACE4),
        horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * Displays a hint banner with a Home Assistant icon and text.
 * This is a specialized variant of [HABanner] designed for displaying informational hints to the user.
 *
 * @param text The hint text to be displayed.
 * @param modifier Optional [androidx.compose.ui.Modifier] to be applied to the banner.
 * @param onClose Optional if present it adds a close Icon on the right
 */
@Composable
fun HAHint(text: String, modifier: Modifier = Modifier, onClose: (() -> Unit)? = null) {
    HABanner(modifier = modifier) {
        Image(
            // Use painterResource instead of vector resource for API < 24
            painter = painterResource(R.drawable.ic_casita),
            colorFilter = ColorFilter.tint(HABrandColors.Blue),
            contentDescription = null,
            modifier = Modifier.align(Alignment.Top)
                // We want to align the close button with casita icon
                .padding(vertical = HADimens.SPACE2),
        )
        Text(
            text = text,
            style = HATextStyle.Body.copy(textAlign = TextAlign.Start),
            modifier = Modifier.weight(1f),
        )
        onClose?.let {
            IconButton(
                onClick = { onClose() },
                modifier = Modifier.align(Alignment.Top),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.cancel),
                    tint = LocalHAColorScheme.current.colorOnNeutralQuiet,
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun HAHintPreview() {
    HAThemeForPreview {
        HAHint(
            "Simple content, but not too long",
            onClose = {
            },
        )
    }
}
