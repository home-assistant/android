package io.homeassistant.companion.android.common.compose.composable

import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme

/**
 * A composable function that displays a switch based on [Switch].
 * Due to the limitation of the underlying API in material this composable only
 * adjust the color of the switch using [io.homeassistant.companion.android.common.compose.theme.HAColorScheme].
 *
 * @param checked whether or not this switch is checked
 * @param onCheckedChange called when this switch is clicked. If `null`, then this switch will not be
 * interactable, unless something else handles its input events and updates its state.
 * @param modifier the [Modifier] to be applied to this switch
 * @param enabled controls the enabled state of this switch. When `false`, this component will not be
 * interactable, and it will appear visually disabled and disabled to accessibility services.
 */
@Composable
fun HASwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = switchColors(),
        enabled = enabled,
        modifier = modifier,
    )
}

@Composable
private fun switchColors(): SwitchColors {
    val scheme = LocalHAColorScheme.current
    return with(scheme) {
        SwitchColors(
            checkedThumbColor = colorSurfaceDefault,
            checkedTrackColor = colorFillPrimaryLoudResting,
            checkedBorderColor = colorFillPrimaryLoudResting,
            // We don't support icon yet in switch
            checkedIconColor = Color.Unspecified,

            uncheckedThumbColor = colorFillNeutralLoudResting,
            uncheckedTrackColor = colorSurfaceDefault,
            uncheckedBorderColor = colorBorderNeutralNormal,
            // We don't support icon yet in switch
            uncheckedIconColor = Color.Unspecified,

            disabledCheckedThumbColor = colorSurfaceDefault,
            disabledCheckedTrackColor = colorFillDisabledLoudResting,
            disabledCheckedBorderColor = colorFillDisabledLoudResting,
            // We don't support icon yet in switch
            disabledCheckedIconColor = Color.Unspecified,

            disabledUncheckedThumbColor = colorFillDisabledLoudResting,
            disabledUncheckedTrackColor = colorSurfaceDefault,
            disabledUncheckedBorderColor = colorBorderNeutralQuiet,
            disabledUncheckedIconColor = Color.Unspecified,
        )
    }
}
