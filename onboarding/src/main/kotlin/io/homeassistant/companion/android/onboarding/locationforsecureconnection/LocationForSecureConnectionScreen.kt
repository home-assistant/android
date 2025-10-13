package io.homeassistant.companion.android.onboarding.locationforsecureconnection

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.composable.HAHint
import io.homeassistant.companion.android.common.compose.composable.HARadioGroup
import io.homeassistant.companion.android.common.compose.composable.RadioOption
import io.homeassistant.companion.android.common.compose.composable.rememberSelectedOption
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.MaxButtonWidth
import io.homeassistant.companion.android.compose.HAPreviews
import io.homeassistant.companion.android.compose.composable.HATopBar
import io.homeassistant.companion.android.compose.rememberLocationPermission
import io.homeassistant.companion.android.onboarding.R
import kotlinx.coroutines.launch

private val MaxContentWidth = MaxButtonWidth

private enum class SelectionKey {
    MOST_SECURE,
    LESS_SECURE,
}

@Composable
internal fun LocationForSecureConnectionScreen(
    viewModel: LocationForSecureConnectionViewModel,
    onGoToNextScreen: (allowInsecureConnection: Boolean) -> Unit,
    onHelpClick: () -> Unit,
    onShowSnackbar: suspend (message: String, action: String?) -> Boolean,
    modifier: Modifier = Modifier,
) {
    LocationForSecureConnectionScreen(
        onAllowInsecureConnection = { allowInsecureConnection ->
            viewModel.allowInsecureConnection(allowInsecureConnection)
            onGoToNextScreen(allowInsecureConnection)
        },
        onHelpClick = onHelpClick,
        onShowSnackbar = onShowSnackbar,
        modifier = modifier,
    )
}

@Composable
internal fun LocationForSecureConnectionScreen(
    onAllowInsecureConnection: (allowInsecureConnection: Boolean) -> Unit,
    onHelpClick: () -> Unit,
    onShowSnackbar: suspend (message: String, action: String?) -> Boolean,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { HATopBar(onHelpClick = onHelpClick) },
    ) { contentPadding ->
        LocationForSecureConnectionContent(
            onAllowInsecureConnection = onAllowInsecureConnection,
            onShowSnackbar = onShowSnackbar,
            modifier = Modifier.padding(contentPadding),
        )
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun LocationForSecureConnectionContent(
    onAllowInsecureConnection: (allowInsecureConnection: Boolean) -> Unit,
    onShowSnackbar: suspend (message: String, action: String?) -> Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = HADimens.SPACE4),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE6),
    ) {
        val coroutineScope = rememberCoroutineScope()
        var selectedOption by rememberSelectedOption<SelectionKey>()

        val lessSecureOption = RadioOption(
            selectionKey = SelectionKey.LESS_SECURE,
            headline = stringResource(R.string.location_secure_connection_less_secure),
        )
        val errorText = stringResource(R.string.location_secure_connection_discard_permission)

        val permissions = rememberLocationPermission(
            onPermissionResult = { permissionGranted ->
                if (permissionGranted) {
                    onAllowInsecureConnection(false)
                } else {
                    coroutineScope.launch {
                        onShowSnackbar(errorText, null)
                    }
                    selectedOption = lessSecureOption
                }
            },
        )

        Header()
        Spacer(modifier = Modifier.weight(1f))
        HARadioGroup(
            options = listOf(
                RadioOption(
                    selectionKey = SelectionKey.MOST_SECURE,
                    headline = stringResource(R.string.location_secure_connection_most_secure),
                ),
                lessSecureOption,
            ),
            onSelect = {
                selectedOption = it
            },
            selectedOption = selectedOption,
        )
        HAHint(text = stringResource(R.string.location_secure_connection_hint))
        Spacer(modifier = Modifier.weight(1f))
        HAAccentButton(
            text = stringResource(R.string.location_secure_connection_next),
            enabled = selectedOption != null,
            onClick = {
                if (selectedOption?.selectionKey == SelectionKey.MOST_SECURE) {
                    permissions.launchMultiplePermissionRequest()
                } else {
                    onAllowInsecureConnection(true)
                }
            },
            modifier = Modifier.fillMaxWidth().padding(bottom = HADimens.SPACE6),
        )
    }
}

@Composable
private fun ColumnScope.Header() {
    Image(
        modifier = Modifier.padding(top = HADimens.SPACE6),
        // Use painterResource instead of vector resource for API < 24 since it has gradients
        painter = painterResource(R.drawable.ic_location_secure),
        contentDescription = null,
    )

    Text(
        text = stringResource(R.string.location_secure_connection_title),
        style = HATextStyle.Headline,
        modifier = Modifier.widthIn(max = MaxContentWidth),
    )

    Text(
        text = stringResource(R.string.location_secure_connection_content),
        style = HATextStyle.Body,
        modifier = Modifier.widthIn(max = MaxContentWidth),
    )
}

@HAPreviews
@Composable
private fun LocationForSecureConnectionScreenPreview() {
    HAThemeForPreview {
        LocationForSecureConnectionScreen(
            onAllowInsecureConnection = {},
            onHelpClick = {},
            onShowSnackbar = { _, _ -> true },
        )
    }
}
