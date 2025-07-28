package io.homeassistant.companion.android.onboarding.locationsharing

import android.Manifest
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import io.homeassistant.companion.android.compose.HAPreviews
import io.homeassistant.companion.android.compose.composable.HAButton
import io.homeassistant.companion.android.compose.composable.HATopBar
import io.homeassistant.companion.android.onboarding.R
import io.homeassistant.companion.android.onboarding.theme.HASpacing
import io.homeassistant.companion.android.onboarding.theme.HATextStyle
import io.homeassistant.companion.android.onboarding.theme.HATheme

@Composable
fun LocationSharingScreen(
    onHelpClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LocationSharingViewModel = hiltViewModel(),
) {
    LocationSharingScreen(
        onHelpClick = onHelpClick,
        onBackClick = onBackClick,
        modifier = Modifier,
    )
}

@Composable
fun LocationSharingScreen(onHelpClick: () -> Unit, onBackClick: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = { HATopBar(onHelpClick = onHelpClick, onBackClick = onBackClick) },
    ) { contentPadding ->
        LocationSharingContent(
            modifier = Modifier.padding(contentPadding),
        )
    }
}

@Composable
@OptIn(ExperimentalPermissionsApi::class)
private fun LocationSharingContent(modifier: Modifier = Modifier) {
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

        val permissions = rememberMultiplePermissionsState(
            listOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
            onPermissionsResult = {},
        )
        // rememberLauncherForActivityResult()

        HAButton(
            text = stringResource(R.string.location_sharing_next),
            onClick = {
                permissions.launchMultiplePermissionRequest()
            },
            modifier = Modifier.padding(bottom = HASpacing.XL),
        )
    }
}

// @Composable
// fun rememberBatteryOptimization() {
//     val context = LocalContext.current
//     if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M ||
//             context.getSystemService<PowerManager>()
//                 ?.isIgnoringBatteryOptimizations(context.packageName ?: "")
//             ?: false
//     }
// }

@HAPreviews
@Composable
private fun LocationSharingScreenPreview() {
    HATheme {
        LocationSharingScreen(
            onHelpClick = {},
            onBackClick = {},
            modifier = Modifier,
        )
    }
}
