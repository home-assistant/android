package io.homeassistant.companion.android.common.compose.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.compose.theme.HABorderWidth
import io.homeassistant.companion.android.common.compose.theme.HAColorScheme
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HARadius
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme

/** Minimum height of a chip, keeping the label and its icon comfortably tappable. */
private val ChipMinHeight = 32.dp

/** Size of the trailing icon, matching the label line height. */
private val ChipIconSize = 18.dp

/**
 * Displays a selectable chip with an optional trailing icon, for example to add or remove an
 * item from a selection.
 *
 * Unselected chips are outlined, selected chips are filled, both using the Home Assistant color
 * scheme so they read correctly in light and dark themes.
 *
 * @param text The label of the chip.
 * @param onClick Invoked when the chip is clicked.
 * @param modifier Optional [Modifier] to be applied to the chip.
 * @param selected Whether the chip is part of the current selection.
 * @param enabled Whether the chip can be clicked.
 * @param trailingIcon Optional icon displayed after the label, usually hinting at what the click does.
 * @param trailingIconContentDescription Content description of [trailingIcon] for accessibility purposes.
 */
@Composable
fun HAInputChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    trailingIcon: ImageVector? = null,
    trailingIconContentDescription: String? = null,
) {
    val colors = LocalHAColorScheme.current.inputChipColors(selected = selected, enabled = enabled)

    Row(
        modifier = modifier
            .heightIn(min = ChipMinHeight)
            .clip(RoundedCornerShape(HARadius.M))
            .background(colors.containerColor)
            .border(
                width = HABorderWidth.S,
                color = colors.borderColor,
                shape = RoundedCornerShape(HARadius.M),
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = HADimens.SPACE2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = HATextStyle.BodyMedium,
            color = colors.labelColor,
        )
        if (trailingIcon != null) {
            Icon(
                imageVector = trailingIcon,
                contentDescription = trailingIconContentDescription,
                modifier = Modifier.padding(start = HADimens.SPACE2).size(ChipIconSize),
                tint = colors.trailingIconColor,
            )
        }
    }
}

private data class HAInputChipColors(
    val containerColor: Color,
    val borderColor: Color,
    val labelColor: Color,
    val trailingIconColor: Color,
)

private fun HAColorScheme.inputChipColors(selected: Boolean, enabled: Boolean): HAInputChipColors = when {
    !enabled -> HAInputChipColors(
        containerColor = Color.Transparent,
        borderColor = colorBorderNeutralQuiet,
        labelColor = colorTextDisabled,
        trailingIconColor = colorTextDisabled,
    )

    selected -> HAInputChipColors(
        containerColor = colorFillPrimaryNormalResting,
        borderColor = Color.Transparent,
        labelColor = colorTextPrimary,
        trailingIconColor = colorTextPrimary,
    )

    else -> HAInputChipColors(
        containerColor = Color.Transparent,
        borderColor = colorBorderNeutralNormal,
        labelColor = colorTextPrimary,
        trailingIconColor = colorTextPrimary,
    )
}

@PreviewLightDark
@Composable
private fun HAInputChipPreview() {
    HAThemeForPreview {
        Column(verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2)) {
            HAInputChip(
                text = "My super name to be displayed in a chip",
                onClick = {},
                trailingIcon = Icons.Default.Add,
                trailingIconContentDescription = null,
            )
            HAInputChip(
                text = "brightness",
                onClick = {},
                selected = true,
                trailingIcon = Icons.Default.Close,
                trailingIconContentDescription = null,
            )
            HAInputChip(text = "no icon", onClick = {})
            HAInputChip(
                text = "disabled",
                onClick = {},
                enabled = false,
                trailingIcon = Icons.Default.Add,
                trailingIconContentDescription = null,
            )
        }
    }
}
