package io.homeassistant.companion.android.common.compose.composable

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.VisualTransformation
import io.homeassistant.companion.android.common.compose.theme.HARadius
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.common.compose.theme.MaxButtonWidth

/**
 * A Home Assistant themed [OutlinedTextField]. This composable is the equivalent of the `ha-input` on the frontend.
 *
 * **WARNING**: This composable is not yet ready to use in production and needs one round of iteration with the design team.
 *
 * @param value the input text to be shown in the text field
 * @param onValueChange the callback that is triggered when the value is updated.
 * @param modifier the [Modifier] to be applied to this text field
 * @param enabled controls the enabled state of this text field
 * @param isError indicates if the text field's current value is in error. If set to true, the
 * text field will adjust based on the color of the theme [io.homeassistant.companion.android.common.compose.theme.HATheme]
 * @param label the optional label to be displayed inside the text field container.
 * @param placeholder the optional placeholder to be displayed when the text field is in focus and
 * the input text is empty.
 * @param supportingText the optional supporting text to be displayed below the text field
 * @param trailingIcon the optional trailing icon to be displayed at the end of the text field
 * container it will be colored according to the theme [io.homeassistant.companion.android.common.compose.theme.HATheme]
 * @param leadingIcon the optional leading icon to be displayed at the beginning of the text field
 * container it will be colored according to the theme [io.homeassistant.companion.android.common.compose.theme.HATheme]
 * @param keyboardOptions software keyboard options that contains configuration such as
 * [KeyboardType] and [ImeAction]
 * @param keyboardActions when the input service emits an IME action, the corresponding callback
 * is called. Note that this IME action may be different from what you specified in
 * [KeyboardOptions.imeAction]
 * @param maxLines the maximum height in terms of maximum number of visible lines. If [singleLine]
 * is set to `true`, this value will be ignored
 * @param singleLine when `true`, this text field becomes a single horizontally scrolling text field
 * instead of wrapping onto multiple lines. [maxLines] will be ignored and automatically set to 1
 */
@Composable
fun HATextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    maxLines: Int = Int.MAX_VALUE,
    singleLine: Boolean = maxLines == 1,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    // TODO probably replace the text composable by strings to control the applied style
    OutlinedTextField(
        enabled = enabled,
        value = value,
        onValueChange = onValueChange,
        supportingText = supportingText,
        trailingIcon = trailingIcon,
        leadingIcon = leadingIcon,
        shape = RoundedCornerShape(size = HARadius.M),
        maxLines = maxLines,
        singleLine = singleLine,
        // The color is controlled from the [colors] attribute
        textStyle = HATextStyle.UserInput.copy(color = Color.Unspecified),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        placeholder = placeholder,
        isError = isError,
        visualTransformation = visualTransformation,
        label = label,
        colors = LocalHAColorScheme.current.textField(),
        modifier = modifier
            .widthIn(max = MaxButtonWidth)
            .fillMaxSize(),
    )
}
