package io.homeassistant.companion.android.onboarding.manualserver

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.compose.HAPreviews
import io.homeassistant.companion.android.compose.composable.HATopBar
import io.homeassistant.companion.android.onboarding.R
import java.net.URL
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

private val delayBeforeError = 500.milliseconds

@Composable
internal fun ManualServerScreen(
    onBackClick: () -> Unit,
    onConnectTo: (URL) -> Unit,
    onHelpClick: () -> Unit,
    viewModel: ManualServerViewModel,
    modifier: Modifier = Modifier,
) {
    val serverUrl by viewModel.serverUrlFlow.collectAsStateWithLifecycle()
    val isServerUrlValid by viewModel.isServerUrlValidFlow.collectAsStateWithLifecycle()

    ManualServerScreen(
        onConnectClick = {
            val url = URL(serverUrl)
            onConnectTo(url)
        },
        onHelpClick = onHelpClick,
        onBackClick = onBackClick,
        serverUrl = serverUrl,
        onServerUrlChange = viewModel::onServerUrlChange,
        isServerUrlValid = isServerUrlValid,
        modifier = modifier,
    )
}

@Composable
internal fun ManualServerScreen(
    isServerUrlValid: Boolean,
    onBackClick: () -> Unit,
    onConnectClick: () -> Unit,
    onHelpClick: () -> Unit,
    onServerUrlChange: (String) -> Unit,
    serverUrl: String,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { HATopBar(onHelpClick = onHelpClick, onBackClick = onBackClick) },
        contentWindowInsets = WindowInsets.safeDrawing,
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
    isServerUrlValid: Boolean,
    onConnectClick: () -> Unit,
    onServerUrlChange: (String) -> Unit,
    serverUrl: String,
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
            modifier = Modifier
                .padding(top = HADimens.SPACE6),
            // Use painterResource instead of vector resource for API < 24 since it has gradients
            painter = painterResource(R.drawable.ic_manual_server),
            contentDescription = null,
        )
        Text(
            text = stringResource(commonR.string.manual_server_title),
            style = HATextStyle.Headline,
        )

        ServerUrlTextField(
            serverUrl = serverUrl,
            onServerUrlChange = onServerUrlChange,
            isServerUrlValid = isServerUrlValid,
            onConnectClick = onConnectClick,
        )

        Spacer(modifier = Modifier.weight(1f))

        HAAccentButton(
            text = stringResource(commonR.string.manual_server_connect),
            onClick = onConnectClick,
            enabled = isServerUrlValid,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = HADimens.SPACE6),
        )
    }
}

@Composable
private fun ServerUrlTextField(
    isServerUrlValid: Boolean,
    onConnectClick: () -> Unit,
    onServerUrlChange: (String) -> Unit,
    serverUrl: String,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    var isError by remember { mutableStateOf(serverUrl.isNotEmpty() && !isServerUrlValid) }

    HATextField(
        value = serverUrl,
        onValueChange = onServerUrlChange,
        trailingIcon = {
            if (serverUrl.isNotEmpty()) {
                IconButton(onClick = { onServerUrlChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(commonR.string.clear_text),
                    )
                }
            }
        },
        placeholder = {
            Text(
                text = "http://homeassistant.local:8123",
                style = HATextStyle.UserInput,
                color = LocalHAColorScheme.current.colorOnNeutralNormal,
            )
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(
            onDone = {
                focusRequester.freeFocus()
                if (isServerUrlValid) {
                    onConnectClick()
                }
                // This is going to hide the keyboard and clear focus on the text field
                defaultKeyboardAction(ImeAction.Done)
            },
        ),
        supportingText = {
            if (isError) {
                Text(
                    text = stringResource(commonR.string.manual_server_wrong_url),
                    // TODO probably wrong style and color/token
                    style = HATextStyle.BodyMedium.copy(color = LocalHAColorScheme.current.colorBorderDangerNormal),
                )
            }
        },
        isError = isError,
        maxLines = 1,
        modifier = modifier.focusRequester(focusRequester),
    )
    // Request focus on the text field when the screen is shown
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // delay the display of the error if the user is typing
    LaunchedEffect(serverUrl) {
        if (serverUrl.isEmpty() || isServerUrlValid) {
            isError = false
            return@LaunchedEffect
        }
        delay(delayBeforeError)
        isError = true
    }
}

@HAPreviews
@Composable
private fun ManualServerScreenPreview() {
    HAThemeForPreview {
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
