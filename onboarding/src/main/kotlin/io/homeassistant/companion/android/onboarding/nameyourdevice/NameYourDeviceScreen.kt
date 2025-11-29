package io.homeassistant.companion.android.onboarding.nameyourdevice

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.composable.HATextField
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.compose.HAPreviews
import io.homeassistant.companion.android.compose.composable.HATopBar
import io.homeassistant.companion.android.onboarding.R

@VisibleForTesting internal const val DEVICE_NAME_TEXT_FIELD_TAG = "device_name_text_field"

@Composable
internal fun NameYourDeviceScreen(
    onBackClick: () -> Unit,
    onHelpClick: () -> Unit,
    viewModel: NameYourDeviceViewModel,
    modifier: Modifier = Modifier,
) {
    val deviceName by viewModel.deviceNameFlow.collectAsStateWithLifecycle()
    val saveClickable by viewModel.isSaveClickableFlow.collectAsStateWithLifecycle(false)
    val isSaving by viewModel.isSavingFlow.collectAsStateWithLifecycle()

    NameYourDeviceScreen(
        onBackClick = onBackClick,
        onHelpClick = onHelpClick,
        deviceName = deviceName,
        onDeviceNameChange = viewModel::onDeviceNameChange,
        saveClickable = saveClickable,
        deviceNameEditable = !isSaving,
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
    deviceNameEditable: Boolean,
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { HATopBar(onHelpClick = onHelpClick, onBackClick = onBackClick) },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { contentPadding ->
        NameYourDeviceContent(
            deviceName = deviceName,
            onDeviceNameChange = onDeviceNameChange,
            saveClickable = saveClickable,
            deviceNameEditable = deviceNameEditable,
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
    deviceNameEditable: Boolean,
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = HADimens.SPACE4)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE6),
    ) {
        Image(
            modifier = Modifier.padding(top = HADimens.SPACE6),
            // Use painterResource instead of vector resource for API < 24 since it has gradients
            painter = painterResource(R.drawable.ic_name_tag),
            contentDescription = null,
        )
        Text(
            text = stringResource(commonR.string.name_your_device_title),
            style = HATextStyle.Headline,
        )
        Text(
            text = stringResource(commonR.string.name_your_device_content),
            style = HATextStyle.Body,
        )

        DeviceNameTextField(
            deviceName = deviceName,
            onDeviceNameChange = onDeviceNameChange,
            saveClickable = saveClickable,
            onSaveClick = onSaveClick,
            deviceNameEditable = deviceNameEditable,
        )

        Spacer(modifier = Modifier.weight(1f))

        HAAccentButton(
            text = stringResource(commonR.string.name_your_device_save),
            onClick = onSaveClick,
            enabled = saveClickable,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = HADimens.SPACE6),
        )
    }
}

@Composable
private fun DeviceNameTextField(
    deviceName: String,
    onDeviceNameChange: (String) -> Unit,
    saveClickable: Boolean,
    deviceNameEditable: Boolean,
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }

    HATextField(
        value = deviceName,
        onValueChange = onDeviceNameChange,
        trailingIcon = {
            if (deviceName.isNotEmpty()) {
                IconButton(onClick = { onDeviceNameChange("") }, enabled = deviceNameEditable) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(commonR.string.clear_text),
                    )
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(
            onDone = {
                focusRequester.freeFocus()
                if (saveClickable) {
                    onSaveClick()
                }
                // This is going to hide the keyboard and clear focus on the text field
                defaultKeyboardAction(ImeAction.Done)
            },
        ),
        enabled = deviceNameEditable,
        maxLines = 1,
        modifier = modifier.testTag(DEVICE_NAME_TEXT_FIELD_TAG),
    )
}

@HAPreviews
@Composable
private fun NameYourDeviceScreenPreview() {
    HAThemeForPreview {
        NameYourDeviceScreen(
            onHelpClick = {},
            onBackClick = {},
            deviceName = "Superman",
            onDeviceNameChange = {},
            saveClickable = true,
            deviceNameEditable = true,
            onSaveClick = {},
        )
    }
}
