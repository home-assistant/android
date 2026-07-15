package io.homeassistant.companion.android.settings.wear.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import io.homeassistant.companion.android.common.compose.theme.HATheme
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
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
    HATheme {
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
}

@Composable
internal fun SettingsWearOnboardingViewContent(
    infoTextTitleResource: Int,
    shouldDisplayRemoteAppInstallButton: Boolean,
    onInstallOnWearDeviceClicked: () -> Unit,
    onBackClicked: () -> Unit,
) {
    Scaffold(
        containerColor = LocalHAColorScheme.current.colorSurfaceDefault,
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
                    color = LocalHAColorScheme.current.colorTextPrimary,
                ),
                modifier = Modifier.padding(start = 15.dp, top = 50.dp, end = 15.dp),
            )

            if (shouldDisplayRemoteAppInstallButton) {
                Button(
                    onClick = onInstallOnWearDeviceClicked,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 50.dp, start = 16.dp, end = 16.dp),
                ) {
                    Text(
                        text = stringResource(commonR.string.install_app),
                        style = MaterialTheme.typography.labelLarge,
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
