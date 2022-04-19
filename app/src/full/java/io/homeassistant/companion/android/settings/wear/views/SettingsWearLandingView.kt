package io.homeassistant.companion.android.settings.wear.views

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.util.wearDeviceName
import io.homeassistant.companion.android.common.R as commonR

@Composable
fun SettingWearLandingView(
    deviceName: String,
    hasData: Boolean,
    isAuthed: Boolean,
    navigateFavorites: () -> Unit,
    navigateTemplateTile: () -> Unit,
    loginWearOs: () -> Unit,
    onBackClicked: () -> Unit
) {
    Scaffold(
        topBar = {
            SettingsWearTopAppBar(
                title = { Text(stringResource(commonR.string.wear_settings)) },
                onBackClicked = onBackClicked,
                docsLink = WEAR_DOCS_LINK
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(all = 16.dp)
        ) {
            Text(
                text = stringResource(id = commonR.string.manage_wear_device, deviceName),
                textAlign = TextAlign.Center
            )
            when {
                !hasData -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                }
                isAuthed -> {
                    Button(
                        onClick = navigateFavorites,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                    ) {
                        Text(stringResource(commonR.string.set_favorites_on_device))
                    }
                    Button(
                        onClick = navigateTemplateTile,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                    ) {
                        Text(stringResource(commonR.string.template_tile))
                    }
                }
                else -> {
                    Button(
                        onClick = loginWearOs,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                    ) {
                        Text(stringResource(commonR.string.login_wear_os_device))
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun PreviewSettingWearLandingView() {
    SettingWearLandingView(
        deviceName = wearDeviceName,
        hasData = true,
        isAuthed = true,
        navigateFavorites = {},
        navigateTemplateTile = {},
        loginWearOs = {},
        onBackClicked = {}
    )
}
