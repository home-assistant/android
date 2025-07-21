package io.homeassistant.companion.android.onboarding.manualserver

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Storage
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.homeassistant.companion.android.compose.HAPreviews
import io.homeassistant.companion.android.compose.composable.HAButton
import io.homeassistant.companion.android.compose.composable.HAOutlinedTextField
import io.homeassistant.companion.android.compose.composable.HATopBar
import io.homeassistant.companion.android.onboarding.R
import io.homeassistant.companion.android.onboarding.theme.HAColors
import io.homeassistant.companion.android.onboarding.theme.HASpacing
import io.homeassistant.companion.android.onboarding.theme.HATextStyle
import io.homeassistant.companion.android.onboarding.theme.HATheme

@Composable
fun ManualServerScreen(
    onHelpClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ManualServerViewModel = hiltViewModel(),
) {
    val serverUrl by viewModel.serverUrlFlow.collectAsStateWithLifecycle()
    val isServerUrlValid by viewModel.isServerUrlValidFlow.collectAsStateWithLifecycle()

    ManualServerScreen(
        onConnectClick = viewModel::onConnectClick,
        onHelpClick = onHelpClick,
        onBackClick = onBackClick,
        serverUrl = serverUrl,
        onServerUrlChange = viewModel::onServerUrlChange,
        isServerUrlValid = isServerUrlValid,
        modifier = modifier,
    )
}

@Composable
fun ManualServerScreen(
    onConnectClick: () -> Unit,
    onHelpClick: () -> Unit,
    onBackClick: () -> Unit,
    serverUrl: String,
    onServerUrlChange: (String) -> Unit,
    isServerUrlValid: Boolean,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { HATopBar(onHelpClick = onHelpClick, onBackClick = onBackClick) },
    ) { contentPadding ->
        ManualServerContent(
            onConnectClick = onConnectClick,
            serverUrl = serverUrl,
            onServerUrlChange = onServerUrlChange,
            isServerUrlValid = isServerUrlValid,
            modifier = Modifier
                .padding(contentPadding),

        )
    }
}

@Composable
private fun ManualServerContent(
    onConnectClick: () -> Unit,
    serverUrl: String,
    onServerUrlChange: (String) -> Unit,
    isServerUrlValid: Boolean,
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
        Icon(
            modifier = Modifier
                .padding(top = HASpacing.XL)
                // TODO should be in a variable (we have 120 and 125 in the illustrations) double checks
                .size(120.dp),
            imageVector = Icons.Default.Storage,
            contentDescription = null,
            tint = HAColors.Brand.Blue,
        )
        Text(
            text = stringResource(R.string.manual_server_title),
            style = HATextStyle.Headline,
        )

        ServerUrlTextField(
            serverUrl = serverUrl,
            onServerUrlChange = onServerUrlChange,
            isServerUrlValid = isServerUrlValid,
            onConnectClick = onConnectClick,
        )

        Spacer(modifier = Modifier.weight(1f))

        HAButton(
            text = stringResource(R.string.manual_server_connect),
            onClick = onConnectClick,
            enabled = isServerUrlValid,
            modifier = Modifier
                .padding(vertical = HASpacing.XL),
        )
    }
}

@Composable
private fun ServerUrlTextField(
    serverUrl: String,
    onServerUrlChange: (String) -> Unit,
    isServerUrlValid: Boolean,
    onConnectClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }

    HAOutlinedTextField(
        value = serverUrl,
        onValueChange = onServerUrlChange,
        trailingIcon = {
            if (serverUrl.isNotEmpty()) {
                IconButton(onClick = { onServerUrlChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        // TODO check the color
                        tint = OutlinedTextFieldDefaults.colors().cursorColor,
                        contentDescription = stringResource(R.string.manual_server_clear_url),
                    )
                }
            }
        },
        placeholder = {
            Text(
                text = "http://homeassistant.local:8123",
                style = HATextStyle.UserInput,
                color = HAColors.Neutral60, // TODO use the theme instead
            )
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(
            onDone = {
                if (isServerUrlValid) {
                    onConnectClick()
                }
                // This is going to hide the keyboard in any case
                defaultKeyboardAction(ImeAction.Done)
            },
        ),
        modifier = modifier.focusRequester(focusRequester),
    )
    // Request focus on the text field when the screen is shown
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@HAPreviews
@Composable
private fun ManualServerScreenPreview() {
    HATheme {
        ManualServerScreen(
            onConnectClick = {},
            onHelpClick = {},
            onBackClick = {},
            serverUrl = "hello",
            onServerUrlChange = {},
            isServerUrlValid = true,
        )
    }
}
