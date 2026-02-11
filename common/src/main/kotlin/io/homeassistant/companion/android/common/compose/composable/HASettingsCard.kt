package io.homeassistant.companion.android.common.compose.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.PreviewLightDark
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HARadius
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme

/**
 * A settings card container providing the standard surface styling used in settings screens:
 * rounded corners, low surface background, and inner padding.
 *
 * This composable only provides the container styling. The caller controls the inner layout
 * (Row, Column, etc.) via the [content] slot.
 *
 * @param content The composable content displayed inside the card.
 */
@Composable
fun HASettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(HARadius.XL))
            .background(LocalHAColorScheme.current.colorSurfaceLow)
            .padding(HADimens.SPACE4),
    ) {
        content()
    }
}

@PreviewLightDark
@Composable
private fun HASettingsCardPreview() {
    HAThemeForPreview {
        HASettingsCard {
            Text(text = "Settings card content", style = HATextStyle.Body)
        }
    }
}
