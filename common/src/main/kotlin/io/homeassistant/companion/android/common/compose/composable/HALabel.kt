package io.homeassistant.companion.android.common.compose.composable

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.compose.theme.HABorderWidth
import io.homeassistant.companion.android.common.compose.theme.HAColorScheme
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HARadius
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme

/** Variant options for [HALabel] controlling border and text color. */
enum class LabelVariant {
    PRIMARY,
    NEUTRAL,
    DANGER,
    WARNING,
    SUCCESS,
}

/** Size options for [HALabel]. */
enum class LabelSize(internal val minHeight: Dp) {
    SMALL(24.dp),
    MEDIUM(32.dp),
}

/**
 * Displays an outlined label chip with colored border and text.
 *
 * @param text The label text to display.
 * @param modifier Optional [Modifier] to be applied to the label.
 * @param variant The color variant controlling border and text color.
 * @param size The size of the label.
 */
@Composable
fun HALabel(
    text: String,
    modifier: Modifier = Modifier,
    variant: LabelVariant = LabelVariant.PRIMARY,
    size: LabelSize = LabelSize.MEDIUM,
) {
    val colorScheme = LocalHAColorScheme.current
    val borderColor = colorScheme.labelBorderColor(variant)
    val textColor = colorScheme.labelTextColor(variant)

    Row(
        modifier = modifier
            .heightIn(min = size.minHeight)
            .border(
                width = HABorderWidth.S,
                color = borderColor,
                shape = RoundedCornerShape(HARadius.M),
            )
            .padding(horizontal = HADimens.SPACE2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = HATextStyle.BodyMedium,
            color = textColor,
        )
    }
}

private fun HAColorScheme.labelBorderColor(variant: LabelVariant): Color = when (variant) {
    LabelVariant.PRIMARY -> colorBorderPrimaryNormal
    LabelVariant.NEUTRAL -> colorBorderNeutralNormal
    LabelVariant.DANGER -> colorBorderDangerNormal
    LabelVariant.WARNING -> colorBorderWarningNormal
    LabelVariant.SUCCESS -> colorBorderSuccessNormal
}

private fun HAColorScheme.labelTextColor(variant: LabelVariant): Color = when (variant) {
    LabelVariant.PRIMARY -> colorOnPrimaryNormal
    LabelVariant.NEUTRAL -> colorOnNeutralNormal
    LabelVariant.DANGER -> colorOnDangerNormal
    LabelVariant.WARNING -> colorOnWarningNormal
    LabelVariant.SUCCESS -> colorOnSuccessNormal
}

@PreviewLightDark
@Composable
private fun HALabelPreview() {
    HAThemeForPreview {
        Column(verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2)) {
            LabelVariant.entries.forEach { variant ->
                HALabel(text = "Label", variant = variant)
            }
            LabelVariant.entries.forEach { variant ->
                HALabel(text = "Label", variant = variant, size = LabelSize.SMALL)
            }
        }
    }
}
