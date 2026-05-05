package io.homeassistant.companion.android.common.compose.composable

import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme

/**
 * A composable function that displays a checkbox based on [Checkbox].
 *
 * Adjusts colors using [io.homeassistant.companion.android.common.compose.theme.HAColorScheme]
 * to match the Home Assistant design system.
 *
 * @param checked whether or not this checkbox is checked
 * @param onCheckedChange called when this checkbox is clicked. If `null`, then this checkbox will not be
 * interactable, unless something else handles its input events and updates its state.
 * @param modifier the [Modifier] to be applied to this checkbox
 * @param enabled controls the enabled state of this checkbox. When `false`, this component will not be
 * interactable, and it will appear visually disabled and disabled to accessibility services.
 */
@Composable
fun HACheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    // Always pass a non-null onCheckedChange to ensure consistent sizing.
    // When null, the underlying Checkbox skips the toggleable modifier,
    // which removes the minimum touch target and changes the rendered size.
    Checkbox(
        checked = checked,
        onCheckedChange = onCheckedChange ?: {},
        colors = checkboxColors(),
        enabled = enabled,
        modifier = modifier,
    )
}

@Composable
private fun checkboxColors(): CheckboxColors {
    val scheme = LocalHAColorScheme.current
    return with(scheme) {
        CheckboxColors(
            checkedCheckmarkColor = colorSurfaceDefault,
            uncheckedCheckmarkColor = colorSurfaceDefault,
            checkedBoxColor = colorFillPrimaryLoudResting,
            uncheckedBoxColor = colorSurfaceDefault,
            disabledCheckedBoxColor = colorFillDisabledLoudResting,
            disabledUncheckedBoxColor = colorSurfaceDefault,
            disabledIndeterminateBoxColor = colorFillDisabledLoudResting,
            checkedBorderColor = colorFillPrimaryLoudResting,
            uncheckedBorderColor = colorBorderNeutralNormal,
            disabledBorderColor = colorFillDisabledLoudResting,
            disabledUncheckedBorderColor = colorBorderNeutralQuiet,
            disabledIndeterminateBorderColor = colorFillDisabledLoudResting,
        )
    }
}
