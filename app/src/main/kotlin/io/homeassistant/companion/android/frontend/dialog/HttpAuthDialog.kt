package io.homeassistant.companion.android.frontend.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HACheckbox
import io.homeassistant.companion.android.common.compose.composable.HAPlainButton
import io.homeassistant.companion.android.common.compose.composable.HATextField
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.common.compose.theme.MaxButtonWidth

/**
 * Native dialog for HTTP Basic Auth requests from the WebView.
 *
 * Displays username and password fields with a "Remember" checkbox and a password visibility toggle.
 *
 * When [isAuthError] is true, a "credentials were rejected" notice is shown.
 *
 * @param message Formatted message with scheme and host
 * @param isAuthError Whether to show the "credentials rejected" indicator
 * @param onProceed Called with username, password, and remember flag when user confirms
 * @param onCancel Called when user cancels or dismisses the dialog
 */
@Composable
internal fun HttpAuthDialog(
    message: String,
    isAuthError: Boolean,
    onProceed: (username: String, password: String, remember: Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var rememberCredentials by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(text = stringResource(commonR.string.auth_request), style = HATextStyle.HeadlineMedium) },
        text = {
            AuthDialogContent(
                message = message,
                isAuthError = isAuthError,
                username = username,
                onUsernameChange = { username = it },
                password = password,
                onPasswordChange = { password = it },
                passwordVisible = passwordVisible,
                onTogglePasswordVisibility = { passwordVisible = !passwordVisible },
                rememberCredentials = rememberCredentials,
                onToggleRemember = { rememberCredentials = !rememberCredentials },
            )
        },
        confirmButton = {
            HAPlainButton(
                text = stringResource(commonR.string.ok),
                onClick = { onProceed(username, password, rememberCredentials) },
            )
        },
        dismissButton = {
            HAPlainButton(stringResource(commonR.string.cancel), onCancel)
        },
    )
}

@Composable
private fun AuthDialogContent(
    message: String,
    isAuthError: Boolean,
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    passwordVisible: Boolean,
    onTogglePasswordVisibility: () -> Unit,
    rememberCredentials: Boolean,
    onToggleRemember: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
        modifier = Modifier.verticalScroll(rememberScrollState()).widthIn(max = MaxButtonWidth),
    ) {
        Text(
            text = message,
            style = HATextStyle.Body.copy(
                textAlign = TextAlign.Start,
            ),
        )
        HATextField(
            value = username,
            onValueChange = onUsernameChange,
            label = { Text(stringResource(commonR.string.username), style = HATextStyle.UserInput) },
            singleLine = true,
        )
        PasswordField(
            password = password,
            onPasswordChange = onPasswordChange,
            passwordVisible = passwordVisible,
            onToggleVisibility = onTogglePasswordVisibility,
        )
        if (isAuthError) {
            Text(
                text = stringResource(commonR.string.auth_request_rejected),
                style = HATextStyle.Body.copy(
                    color = LocalHAColorScheme.current.colorOnDangerNormal,
                    textAlign = TextAlign.Start,
                ),
            )
        }
        RememberRow(
            checked = rememberCredentials,
            onToggle = onToggleRemember,
        )
    }
}

@Composable
private fun PasswordField(
    password: String,
    onPasswordChange: (String) -> Unit,
    passwordVisible: Boolean,
    onToggleVisibility: () -> Unit,
) {
    HATextField(
        value = password,
        onValueChange = onPasswordChange,
        label = { Text(stringResource(commonR.string.password), style = HATextStyle.UserInput) },
        singleLine = true,
        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = onToggleVisibility) {
                Icon(
                    painter = painterResource(
                        if (passwordVisible) R.drawable.ic_visibility_off else R.drawable.ic_visibility,
                    ),
                    contentDescription = stringResource(
                        if (passwordVisible) commonR.string.hide_password else commonR.string.show_password,
                    ),
                )
            }
        },
    )
}

@Composable
private fun RememberRow(checked: Boolean, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .toggleable(checked, role = Role.Checkbox, indication = null, interactionSource = null) { onToggle() },
    ) {
        HACheckbox(checked = checked, onCheckedChange = { onToggle() })
        Text(text = stringResource(commonR.string.remember), style = HATextStyle.Body)
    }
}

@Preview
@Composable
private fun PreviewHttpAuthDialog() {
    HAThemeForPreview {
        HttpAuthDialog("Hello", isAuthError = false, onProceed = { _, _, _ -> }, onCancel = {})
    }
}

@Preview
@Composable
private fun PreviewHttpAuthDialogWithError() {
    HAThemeForPreview {
        HttpAuthDialog("Hello", isAuthError = true, onProceed = { _, _, _ -> }, onCancel = {})
    }
}
