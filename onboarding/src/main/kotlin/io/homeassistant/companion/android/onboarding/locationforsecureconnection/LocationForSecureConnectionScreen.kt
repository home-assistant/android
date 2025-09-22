package io.homeassistant.companion.android.onboarding.locationforsecureconnection

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.composable.HARadioGroup
import io.homeassistant.companion.android.common.compose.composable.RadioOption
import io.homeassistant.companion.android.common.compose.composable.rememberSelectedOption
import io.homeassistant.companion.android.common.compose.theme.HABrandColors
import io.homeassistant.companion.android.common.compose.theme.HARadius
import io.homeassistant.companion.android.common.compose.theme.HASpacing
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
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
    onGoToNextScreen: () -> Unit,
    onHelpClick: () -> Unit,
    onShowSnackbar: suspend (message: String, action: String?) -> Boolean,
    modifier: Modifier = Modifier,
) {
    LocationForSecureConnectionScreen(
        onAllowInsecureConnection = viewModel::allowInsecureConnection,
        onGoToNextScreen = onGoToNextScreen,
        onHelpClick = onHelpClick,
        onShowSnackbar = onShowSnackbar,
        modifier = modifier,
    )
}

@Composable
internal fun LocationForSecureConnectionScreen(
    onAllowInsecureConnection: (allowInsecureConnection: Boolean) -> Unit,
    onGoToNextScreen: () -> Unit,
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
            onGoToNextScreen = onGoToNextScreen,
            onShowSnackbar = onShowSnackbar,
            modifier = Modifier.padding(contentPadding),
        )
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun LocationForSecureConnectionContent(
    onAllowInsecureConnection: (allowInsecureConnection: Boolean) -> Unit,
    onGoToNextScreen: () -> Unit,
    onShowSnackbar: suspend (message: String, action: String?) -> Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = HASpacing.M),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(
            HASpacing.XL,
        ),
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
                    onGoToNextScreen()
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
        Hint()
        Spacer(modifier = Modifier.weight(1f))
        HAAccentButton(
            text = stringResource(R.string.location_secure_connection_next),
            enabled = selectedOption != null,
            onClick = {
                if (selectedOption?.selectionKey == SelectionKey.MOST_SECURE) {
                    permissions.launchMultiplePermissionRequest()
                } else {
                    onAllowInsecureConnection(true)
                    onGoToNextScreen()
                }
            },
            modifier = Modifier.padding(bottom = HASpacing.XL),
        )
    }
}

@Composable
private fun ColumnScope.Header() {
    Image(
        modifier = Modifier.padding(top = HASpacing.XL),
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

// TODO this might become a generic component if we reuse it in other places
@Composable
private fun Hint(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .width(MaxButtonWidth)
            .background(
                color = LocalHAColorScheme.current.colorFillNeutralNormalResting, // TODO update color
                shape = RoundedCornerShape(
                    HARadius.XL,
                ),
            )
            .padding(HASpacing.M),
        horizontalArrangement = Arrangement.spacedBy(HASpacing.XS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            // Use painterResource instead of vector resource for API < 24
            painter = painterResource(R.drawable.ic_casita),
            colorFilter = ColorFilter.tint(HABrandColors.Blue),
            contentDescription = null,
        )
        Text(
            text = stringResource(R.string.location_secure_connection_hint),
            style = HATextStyle.Body.copy(textAlign = TextAlign.Start),
        )
    }
}

@HAPreviews
@Composable
private fun LocationForSecureConnectionScreenPreview() {
    HAThemeForPreview {
        LocationForSecureConnectionScreen(
            onAllowInsecureConnection = {},
            onGoToNextScreen = {},
            onHelpClick = {},
            onShowSnackbar = { _, _ -> true },
        )
    }
}
