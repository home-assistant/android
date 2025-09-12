package io.homeassistant.companion.android.onboarding.locationforsecureconnection

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.theme.HASpacing
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.MaxButtonWidth
import io.homeassistant.companion.android.compose.HAPreviews
import io.homeassistant.companion.android.compose.composable.HATopBar
import io.homeassistant.companion.android.onboarding.R
import io.homeassistant.companion.android.onboarding.locationsharing.locationPermissions

private val MaxContentWidth = MaxButtonWidth

@Composable
internal fun LocationForSecureConnectionScreen(
    viewModel: LocationForSecureConnectionViewModel,
    onHelpClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LocationForSecureConnectionScreen(
        onHelpClick = onHelpClick,
    )
}

@Composable
internal fun LocationForSecureConnectionScreen(onHelpClick: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = { HATopBar(onHelpClick = onHelpClick) },
    ) { contentPadding ->
        LocationForSecureConnectionContent(
            modifier = Modifier.padding(contentPadding),
        )
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun LocationForSecureConnectionContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = HASpacing.M),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(
            HASpacing.XL,
        ),
    ) {
        Image(
            modifier = Modifier.padding(top = HASpacing.XL),
            imageVector = ImageVector.vectorResource(R.drawable.ic_location_secure),
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

        Spacer(modifier = Modifier.weight(1f))

        val permissions = rememberMultiplePermissionsState(
            locationPermissions,
            onPermissionsResult = {
                // onGoToNextScreen()
            },
        )

        HAAccentButton(
            text = stringResource(R.string.location_sharing_share),
            enabled = selectedOption != null,
            onClick = {
                // onLocationSharingResponse(true)
                permissions.launchMultiplePermissionRequest()
            },
            modifier = Modifier.padding(bottom = HASpacing.XL),
        )
    }
}

private enum class Selection {
    MOST_SECURE,
    LESS_SECURE,
}

@HAPreviews
@Composable
private fun LocationForSecureConnectionScreenPreview() {
    HAThemeForPreview {
        LocationForSecureConnectionScreen(
            onHelpClick = {},
        )
    }
}
