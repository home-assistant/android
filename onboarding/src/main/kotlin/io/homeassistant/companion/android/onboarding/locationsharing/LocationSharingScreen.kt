package io.homeassistant.companion.android.onboarding.locationsharing

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.composable.HAPlainButton
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.MaxButtonWidth
import io.homeassistant.companion.android.compose.HAPreviews
import io.homeassistant.companion.android.compose.composable.HATopBar
import io.homeassistant.companion.android.compose.rememberLocationPermission
import io.homeassistant.companion.android.onboarding.R

private val MaxContentWidth = MaxButtonWidth

@Composable
internal fun LocationSharingScreen(
    onHelpClick: () -> Unit,
    onGoToNextScreen: () -> Unit,
    viewModel: LocationSharingViewModel,
    modifier: Modifier = Modifier,
) {
    LocationSharingScreen(
        onHelpClick = onHelpClick,
        onGoToNextScreen = onGoToNextScreen,
        onLocationSharingResponse = viewModel::setupLocationSensor,
        modifier = modifier,
    )
}

@Composable
internal fun LocationSharingScreen(
    onHelpClick: () -> Unit,
    onGoToNextScreen: () -> Unit,
    onLocationSharingResponse: (enabled: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { HATopBar(onHelpClick = onHelpClick) },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { contentPadding ->
        LocationSharingContent(
            onGoToNextScreen = onGoToNextScreen,
            onLocationSharingResponse = onLocationSharingResponse,
            modifier = Modifier.padding(contentPadding),
        )
    }
}

@Composable
private fun LocationSharingContent(
    onGoToNextScreen: () -> Unit,
    onLocationSharingResponse: (enabled: Boolean) -> Unit,
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
        Image(
            modifier = Modifier.padding(top = HADimens.SPACE6),
            // Use painterResource instead of vector resource for API < 24 since it has gradients
            painter = painterResource(R.drawable.ic_location_tracking),
            contentDescription = null,
        )

        Text(
            text = stringResource(commonR.string.location_sharing_title),
            style = HATextStyle.Headline,
            modifier = Modifier.widthIn(max = MaxContentWidth),
        )

        Text(
            text = stringResource(commonR.string.location_sharing_content),
            style = HATextStyle.Body,
            modifier = Modifier.widthIn(max = MaxContentWidth),
        )

        Spacer(modifier = Modifier.weight(1f))

        BottomButtons(
            onGoToNextScreen = onGoToNextScreen,
            onLocationSharingResponse = onLocationSharingResponse,
        )
    }
}

@Composable
@OptIn(ExperimentalPermissionsApi::class)
private fun BottomButtons(onGoToNextScreen: () -> Unit, onLocationSharingResponse: (enabled: Boolean) -> Unit) {
    val permissions = rememberLocationPermission(
        onPermissionResult = {
            // We ignore the result and proceed even if the user rejected the permission
            onGoToNextScreen()
        },
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4),
    ) {
        HAAccentButton(
            text = stringResource(commonR.string.location_sharing_share),
            onClick = {
                onLocationSharingResponse(true)
                permissions.launchMultiplePermissionRequest()
            },
            modifier = Modifier.fillMaxWidth(),
        )

        HAPlainButton(
            text = stringResource(commonR.string.location_sharing_no_share),
            onClick = {
                onLocationSharingResponse(false)
                onGoToNextScreen()
            },
            modifier = Modifier.fillMaxWidth().padding(bottom = HADimens.SPACE6),
        )
    }
}

@HAPreviews
@Composable
private fun LocationSharingScreenPreview() {
    HAThemeForPreview {
        LocationSharingScreen(
            onHelpClick = {},
            onGoToNextScreen = {},
            onLocationSharingResponse = {},
            modifier = Modifier,
        )
    }
}
