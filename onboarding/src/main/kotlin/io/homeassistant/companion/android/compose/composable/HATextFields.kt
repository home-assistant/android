package io.homeassistant.companion.android.compose.composable

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.homeassistant.companion.android.theme.HARadius
import io.homeassistant.companion.android.theme.HATextStyle
import io.homeassistant.companion.android.theme.MaxButtonWidth

@Composable
fun HAOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    trailingIcon: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        trailingIcon = trailingIcon,
        shape = RoundedCornerShape(size = HARadius.L),
        textStyle = HATextStyle.UserInput,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        placeholder = placeholder,
        modifier = modifier
            .widthIn(max = MaxButtonWidth)
            .fillMaxSize(),
    )
}
