package io.homeassistant.companion.android.onboarding.nameyourdevice

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.composable.HAInput
import io.homeassistant.companion.android.common.compose.theme.HASpacing
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HATheme
import io.homeassistant.companion.android.compose.HAPreviews
import io.homeassistant.companion.android.compose.composable.HATopBar
import io.homeassistant.companion.android.onboarding.R

@Composable
internal fun NameYourDeviceScreen(
    onBackClick: () -> Unit,
    onHelpClick: () -> Unit,
    viewModel: NameYourDeviceViewModel,
    modifier: Modifier = Modifier,
) {
    val deviceName by viewModel.deviceNameFlow.collectAsStateWithLifecycle()
    val saveClickable by viewModel.isValidNameFlow.collectAsStateWithLifecycle()

    NameYourDeviceScreen(
        onBackClick = onBackClick,
        onHelpClick = onHelpClick,
        deviceName = deviceName,
        onDeviceNameChange = viewModel::onDeviceNameChange,
        saveClickable = saveClickable,
        onSaveClick = viewModel::onSaveClick,
        modifier = modifier,
    )
}

@Composable
internal fun NameYourDeviceScreen(
    onHelpClick: () -> Unit,
    onBackClick: () -> Unit,
    deviceName: String,
    onDeviceNameChange: (String) -> Unit,
    saveClickable: Boolean,
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { HATopBar(onHelpClick = onHelpClick, onBackClick = onBackClick) },
    ) { contentPadding ->
        NameYourDeviceContent(
            deviceName = deviceName,
            onDeviceNameChange = onDeviceNameChange,
            saveClickable = saveClickable,
            onSaveClick = onSaveClick,
            modifier = Modifier.padding(contentPadding),
        )
    }
}

@Composable
private fun NameYourDeviceContent(
    deviceName: String,
    onDeviceNameChange: (String) -> Unit,
    saveClickable: Boolean,
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = HASpacing.M)
            .windowInsetsPadding(WindowInsets.ime)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HASpacing.XL),
    ) {
        Image(
            modifier = Modifier.padding(top = HASpacing.XL),
            imageVector = ImageVector.vectorResource(R.drawable.ic_name_tag),
            contentDescription = null,
        )
        Text(
            text = stringResource(R.string.name_your_device_title),
            style = HATextStyle.Headline,
        )
        Text(
            text = stringResource(R.string.name_your_device_content),
            style = HATextStyle.Body,
        )

        DeviceNameTextField(
            deviceName = deviceName,
            onDeviceNameChange = onDeviceNameChange,
            saveClickable = saveClickable,
            onSaveClick = onSaveClick,
        )

        Spacer(modifier = Modifier.weight(1f))

        HAAccentButton(
            text = stringResource(R.string.name_your_device_save),
            onClick = onSaveClick,
            enabled = saveClickable,
            modifier = Modifier
                .padding(vertical = HASpacing.XL),
        )
    }
}

@Composable
private fun DeviceNameTextField(
    deviceName: String,
    onDeviceNameChange: (String) -> Unit,
    saveClickable: Boolean,
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }

    HAInput(
        value = deviceName,
        onValueChange = onDeviceNameChange,
        trailingIcon = {
            if (deviceName.isNotEmpty()) {
                IconButton(onClick = { onDeviceNameChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        // TODO check the color
                        tint = OutlinedTextFieldDefaults.colors().cursorColor,
                        contentDescription = stringResource(R.string.name_your_device_clear_name),
                    )
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(
            onDone = {
                if (saveClickable) {
                    onSaveClick()
                }
                // This is going to hide the keyboard in any case
                defaultKeyboardAction(ImeAction.Done)
            },
        ),
        modifier = modifier
            .focusRequester(focusRequester),
    )
    // Request focus on the text field when the screen is shown
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@HAPreviews
@Composable
private fun NameYourDeviceScreenPreview() {
    HATheme {
        NameYourDeviceScreen(
            onHelpClick = {},
            onBackClick = {},
            deviceName = "Superman",
            onDeviceNameChange = {},
            saveClickable = true,
            onSaveClick = {},
        )
    }
}
