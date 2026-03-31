package io.homeassistant.companion.android.settings.wear.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.settings.wear.SettingsWearViewModel
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme
import io.homeassistant.companion.android.util.safeBottomPaddingValues

@Composable
internal fun SettingsWearOnboardingView(
    settingsWearViewModel: SettingsWearViewModel,
    onInstallOnWearDeviceClicked: () -> Unit,
    onFinishInstallOnDevices: () -> Unit,
    onBackClicked: () -> Unit,
) {
    val uiState by settingsWearViewModel.settingsWearOnboardingViewUiState.collectAsStateWithLifecycle(
        SettingsWearViewModel.SettingsWearOnboardingViewUiState(),
    )

    if (uiState.installedOnDevices) {
        onFinishInstallOnDevices()
    }

    SettingsWearOnboardingViewContent(
        infoTextTitleResource = uiState.infoTextResourceId,
        shouldDisplayRemoteAppInstallButton = uiState.shouldShowRemoteInstallButton,
        onInstallOnWearDeviceClicked = onInstallOnWearDeviceClicked,
        onBackClicked = onBackClicked,
    )
}

@Composable
internal fun SettingsWearOnboardingViewContent(
    infoTextTitleResource: Int,
    shouldDisplayRemoteAppInstallButton: Boolean,
    onInstallOnWearDeviceClicked: () -> Unit,
    onBackClicked: () -> Unit,
) {
    Scaffold(
        topBar = {
            SettingsWearTopAppBar(
                title = {
                    Text(
                        stringResource(commonR.string.wear_os_settings_title),
                        fontWeight = FontWeight.Bold,
                    )
                },
                onBackClicked = onBackClicked,
                docsLink = WEAR_DOCS_LINK,
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(safeBottomPaddingValues())
                .padding(contentPadding),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(infoTextTitleResource),
                style = TextStyle(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Light,
                    color = colorResource(commonR.color.colorHeadline1),
                ),
                modifier = Modifier.padding(start = 15.dp, top = 50.dp, end = 15.dp),
            )

            if (shouldDisplayRemoteAppInstallButton) {
                Button(
                    onClick = onInstallOnWearDeviceClicked,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 50.dp),
                ) {
                    Text(
                        text = stringResource(commonR.string.install_app).uppercase(),
                        letterSpacing = 1.sp,
                        style = MaterialTheme.typography.body2,
                        color = colorResource(commonR.color.colorBackground),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun PreviewSettingWearAppMissingViewWithButton() {
    HomeAssistantAppTheme {
        SettingsWearOnboardingViewContent(
            infoTextTitleResource = commonR.string.message_checking,
            shouldDisplayRemoteAppInstallButton = false,
            onInstallOnWearDeviceClicked = {},
            onBackClicked = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun PreviewSettingWearAppMissingView() {
    HomeAssistantAppTheme {
        SettingsWearOnboardingViewContent(
            infoTextTitleResource = commonR.string.message_checking,
            shouldDisplayRemoteAppInstallButton = true,
            onInstallOnWearDeviceClicked = {},
            onBackClicked = {},
        )
    }
}
