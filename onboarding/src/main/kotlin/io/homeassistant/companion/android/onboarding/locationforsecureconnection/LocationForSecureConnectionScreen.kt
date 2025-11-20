package io.homeassistant.companion.android.onboarding.locationforsecureconnection

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import io.homeassistant.companion.android.common.R as commonR
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

/**
 * Public screen so it can be used in other places than the onboarding,
 * to update the choice of the user.
 */
@Composable
fun LocationForSecureConnectionScreen(
    viewModel: LocationForSecureConnectionViewModel,
    onGoToNextScreen: (allowInsecureConnection: Boolean) -> Unit,
    onHelpClick: () -> Unit,
    onShowSnackbar: suspend (message: String, action: String?) -> Boolean,
    modifier: Modifier = Modifier,
    onBackClick: (() -> Unit)? = null,
    isStandaloneScreen: Boolean = false,
) {
    val initialAllowInsecureConnection by viewModel.allowInsecureConnection.collectAsState(null)

    LocationForSecureConnectionScreen(
        initialAllowInsecureConnection = initialAllowInsecureConnection,
        onBackClick = onBackClick,
        isStandaloneScreen = isStandaloneScreen,
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
    initialAllowInsecureConnection: Boolean?,
    onAllowInsecureConnection: (allowInsecureConnection: Boolean) -> Unit,
    onHelpClick: () -> Unit,
    onShowSnackbar: suspend (message: String, action: String?) -> Boolean,
    modifier: Modifier = Modifier,
    onBackClick: (() -> Unit)? = null,
    isStandaloneScreen: Boolean = false,
) {
    Scaffold(
        modifier = modifier,
        topBar = { HATopBar(onBackClick = onBackClick, onHelpClick = onHelpClick) },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { contentPadding ->
        LocationForSecureConnectionContent(
            isStandaloneScreen = isStandaloneScreen,
            initialAllowInsecureConnection = initialAllowInsecureConnection,
            onAllowInsecureConnection = onAllowInsecureConnection,
            onShowSnackbar = onShowSnackbar,
            modifier = Modifier.padding(contentPadding),
        )
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun LocationForSecureConnectionContent(
    isStandaloneScreen: Boolean,
    initialAllowInsecureConnection: Boolean?,
    onAllowInsecureConnection: (allowInsecureConnection: Boolean) -> Unit,
    onShowSnackbar: suspend (message: String, action: String?) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = HADimens.SPACE4),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE6),
    ) {
        val coroutineScope = rememberCoroutineScope()

        val mostSecureOption = RadioOption(
            selectionKey = SelectionKey.MOST_SECURE,
            headline = stringResource(commonR.string.connection_security_most_secure),
        )
        val lessSecureOption = RadioOption(
            selectionKey = SelectionKey.LESS_SECURE,
            headline = stringResource(commonR.string.connection_security_less_secure),
        )

        var selectedOption by rememberSelectedOption(
            when (initialAllowInsecureConnection) {
                true -> lessSecureOption
                false -> mostSecureOption
                null -> null
            },
        )
        val errorText = stringResource(commonR.string.location_secure_connection_discard_permission)

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
                mostSecureOption,
                lessSecureOption,
            ),
            onSelect = {
                selectedOption = it
                coroutineScope.launch {
                    scrollState.animateScrollTo(scrollState.maxValue, SpringSpec(stiffness = Spring.StiffnessMediumLow))
                }
            },
            selectedOption = selectedOption,
        )
        HAHint(
            text = stringResource(commonR.string.location_secure_connection_hint),
            modifier = Modifier.width(MaxContentWidth),
        )
        Spacer(modifier = Modifier.weight(1f))
        HAAccentButton(
            text = if (isStandaloneScreen) {
                stringResource(
                    commonR.string.location_secure_connection_save,
                )
            } else {
                stringResource(commonR.string.location_secure_connection_next)
            },
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
        text = stringResource(commonR.string.location_secure_connection_title),
        style = HATextStyle.Headline,
        modifier = Modifier.widthIn(max = MaxContentWidth),
    )

    Text(
        text = stringResource(commonR.string.location_secure_connection_content),
        style = HATextStyle.Body,
        modifier = Modifier.widthIn(max = MaxContentWidth),
    )
}

@HAPreviews
@Composable
private fun LocationForSecureConnectionScreenPreview() {
    HAThemeForPreview {
        LocationForSecureConnectionScreen(
            initialAllowInsecureConnection = null,
            onAllowInsecureConnection = {},
            onHelpClick = {},
            onShowSnackbar = { _, _ -> true },
        )
    }
}
