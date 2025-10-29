package io.homeassistant.companion.android.common.compose.composable

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonColors
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HARadius
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.common.compose.theme.MaxButtonWidth

/**
 * Represents a single option within a radio button group.
 *
 * @param T The type of the value associated with this option.
 * @property selectionKey The unique key identifying this option, of type [T].
 * @property headline The primary text displayed for this option.
 * @property secondary An optional secondary text displayed below the headline.
 * @property enabled Whether this option is currently enabled and can be selected. Defaults to `true`.
 */
@Immutable
data class RadioOption<T>(
    val selectionKey: T,
    val headline: String,
    val secondary: String? = null,
    val enabled: Boolean = true,
)

/**
 * Remembers the selected [RadioOption] in a [MutableState].
 * This is a helper function to simplify dealing with the complex generic type [RadioOption].
 *
 * @param T The type of the selection key in [RadioOption].
 * @param option The initially selected [RadioOption], or `null` if no option is selected.
 * @return A [MutableState] holding the currently selected [RadioOption], or `null`.
 */
@Composable
fun <T> rememberSelectedOption(option: RadioOption<T>? = null): MutableState<RadioOption<T>?> =
    remember(option) { mutableStateOf<RadioOption<T>?>(option) }

/**
 * A composable function that displays a group of radio buttons.
 * Each radio button is represented by a [RadioOption] and can have a headline, secondary text,
 * and an enabled state. Only one radio button can be selected at a time.
 *
 * @param T The type of the value associated with each radio option.
 * @param options A list of [RadioOption] objects representing the choices available.
 * @param onSelect A callback function that is invoked when a radio option is selected.
 *                 It receives the selected [RadioOption] as a parameter.
 * @param modifier An optional [Modifier] to be applied to the radio group.
 * @param selectionKey The key associated to the currently selected [RadioOption]. If `null`, no option is selected.
 * @param spaceBy The vertical spacing between radio buttons. Defaults to [HADimens.SPACE6].
 */
@Composable
fun <T> HARadioGroup(
    @SuppressLint("ComposeUnstableCollections") options: List<RadioOption<T>>,
    onSelect: (RadioOption<T>) -> Unit,
    modifier: Modifier = Modifier,
    selectionKey: T? = null,
    spaceBy: Dp = HADimens.SPACE6,
) {
    Column(
        modifier = modifier
            .widthIn(max = MaxButtonWidth)
            .selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(spaceBy),
    ) {
        options.forEach { option ->
            HARadioButton(
                headline = option.headline,
                secondary = option.secondary,
                isSelected = (selectionKey == option.selectionKey),
                onSelect = {
                    onSelect(option)
                },
                enabled = option.enabled,
            )
        }
    }
}

/**
 * A composable function that displays a group of radio buttons.
 * Each radio button is represented by a [RadioOption] and can have a headline, secondary text,
 * and an enabled state. Only one radio button can be selected at a time.
 *
 * @param T The type of the value associated with each radio option.
 * @param options A list of [RadioOption] objects representing the choices available.
 * @param onSelect A callback function that is invoked when a radio option is selected.
 *                 It receives the selected [RadioOption] as a parameter.
 * @param modifier An optional [Modifier] to be applied to the radio group.
 * @param selectedOption The currently selected [RadioOption]. If `null`, no option is selected.
 * @param spaceBy The vertical spacing between radio buttons. Defaults to [HADimens.SPACE6].
 */
@Composable
fun <T> HARadioGroup(
    @SuppressLint("ComposeUnstableCollections") options: List<RadioOption<T>>,
    onSelect: (RadioOption<T>) -> Unit,
    modifier: Modifier = Modifier,
    selectedOption: RadioOption<T>? = null,
    spaceBy: Dp = HADimens.SPACE6,
) {
    HARadioGroup(
        options = options,
        onSelect = onSelect,
        modifier = modifier,
        selectionKey = selectedOption?.selectionKey,
        spaceBy = spaceBy,
    )
}

@Composable
private fun HARadioButton(
    headline: String,
    onSelect: () -> Unit,
    isSelected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    secondary: String? = null,
) {
    val shape = RoundedCornerShape(HARadius.XL)
    val backgroundColor = when {
        !enabled -> LocalHAColorScheme.current.colorFillDisabledNormalResting
        isSelected -> LocalHAColorScheme.current.colorFillPrimaryNormalActive
        else -> null
    }

    Row(
        modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = LocalHAColorScheme.current.colorBorderNeutralQuiet,
                shape = shape,
            )
            .clip(shape)
            .then(
                if (backgroundColor != null) {
                    Modifier.background(
                        color = backgroundColor,
                        shape = shape,
                    )
                } else {
                    Modifier
                },
            )
            .selectable(
                enabled = enabled,
                selected = isSelected,
                interactionSource = null,
                indication = ripple(color = LocalHAColorScheme.current.colorFillNeutralQuietHover),
                onClick = onSelect,
                role = Role.RadioButton,
            )
            .padding(HADimens.SPACE3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = isSelected,
            enabled = enabled,
            onClick = null,
            colors = radioButtonColors(),
        )
        Column(modifier = Modifier.padding(start = HADimens.SPACE2)) {
            val textColor = if (enabled) {
                LocalHAColorScheme.current.colorTextPrimary
            } else {
                LocalHAColorScheme.current.colorTextDisabled
            }
            Text(
                text = headline,
                style = HATextStyle.Body.copy(textAlign = TextAlign.Start),
                color = textColor,
            )
            secondary?.let {
                Text(
                    text = secondary,
                    style = HATextStyle.BodyMedium.copy(textAlign = TextAlign.Start),
                    color = textColor,
                )
            }
        }
    }
}

@Composable
private fun radioButtonColors(): RadioButtonColors {
    return RadioButtonColors(
        selectedColor = LocalHAColorScheme.current.colorOnPrimaryNormal,
        unselectedColor = LocalHAColorScheme.current.colorOnNeutralNormal,
        disabledUnselectedColor = LocalHAColorScheme.current.colorOnDisabledNormal,
        disabledSelectedColor = LocalHAColorScheme.current.colorOnDisabledNormal,
    )
}
