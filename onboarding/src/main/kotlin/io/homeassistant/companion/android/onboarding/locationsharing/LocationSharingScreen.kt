package io.homeassistant.companion.android.onboarding.locationsharing

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.composable.HAPlainButton
import io.homeassistant.companion.android.common.compose.theme.HASpacing
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.compose.HAPreviews
import io.homeassistant.companion.android.compose.composable.HATopBar
import io.homeassistant.companion.android.onboarding.R

internal val locationPermissions = listOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

@Composable
internal fun LocationSharingScreen(
    onHelpClick: () -> Unit,
    viewModel: LocationSharingViewModel,
    modifier: Modifier = Modifier,
) {
    LocationSharingScreen(
        onHelpClick = onHelpClick,
        onGoToNextScreen = viewModel::onGoToNextScreen,
        modifier = modifier,
    )
}

@Composable
internal fun LocationSharingScreen(
    onHelpClick: () -> Unit,
    onGoToNextScreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { HATopBar(onHelpClick = onHelpClick) },
    ) { contentPadding ->
        LocationSharingContent(
            onGoToNextScreen = onGoToNextScreen,
            modifier = Modifier.padding(contentPadding),
        )
    }
}

@Composable
@OptIn(ExperimentalPermissionsApi::class)
private fun LocationSharingContent(onGoToNextScreen: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = HASpacing.M),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HASpacing.XL),
    ) {
        Image(
            modifier = Modifier.padding(top = HASpacing.XL),
            imageVector = ImageVector.vectorResource(R.drawable.ic_location_tracking),
            contentDescription = null,
        )

        Text(
            text = stringResource(R.string.location_sharing_title),
            style = HATextStyle.Headline,
        )

        Spacer(modifier = Modifier.weight(1f))

        // TODO check if location is enabled
        // TODO request battery optimizations disable

        val activity = LocalActivity.current

        val permissions = rememberMultiplePermissionsState(
            locationPermissions,
            onPermissionsResult = {
                // TODO handle logic of denying permissions

                if (activity?.isIgnoringBatteryOptimizations() == false) {
                    activity.startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            "package:${activity.packageName}".toUri(),
                        ),
                    )
                }
                onGoToNextScreen()
            },
        )

        HAAccentButton(
            text = stringResource(R.string.location_sharing_share),
            onClick = {
                permissions.launchMultiplePermissionRequest()
            },
            modifier = Modifier.padding(bottom = HASpacing.XL),
        )

        HAPlainButton(
            text = stringResource(R.string.location_sharing_no_share),
            onClick = {
                permissions.launchMultiplePermissionRequest()
            },
            modifier = Modifier.padding(bottom = HASpacing.XL),
        )
    }
}

private fun Activity.isIgnoringBatteryOptimizations(): Boolean {
    return Build.VERSION.SDK_INT <= Build.VERSION_CODES.M ||
        getSystemService<PowerManager>()
            ?.isIgnoringBatteryOptimizations(packageName ?: "")
            ?: false
}

@HAPreviews
@Composable
private fun LocationSharingScreenPreview() {
    HAThemeForPreview {
        LocationSharingScreen(
            onHelpClick = {},
            onGoToNextScreen = {},
            modifier = Modifier,
        )
    }
}
