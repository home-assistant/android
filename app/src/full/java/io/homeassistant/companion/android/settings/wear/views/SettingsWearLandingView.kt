package io.homeassistant.companion.android.settings.wear.views

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.settings.views.SettingsRow
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
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
        ) {
            Row(
                modifier = Modifier
                    .height(48.dp)
                    .padding(start = 72.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(id = commonR.string.manage_wear_device, deviceName),
                    style = MaterialTheme.typography.body2,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.primary
                )
            }
            when {
                !hasData -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                }
                isAuthed -> {
                    SettingsRow(
                        primaryText = stringResource(commonR.string.favorite),
                        secondaryText = stringResource(commonR.string.set_favorites_on_device),
                        mdiIcon = CommunityMaterial.Icon3.cmd_star,
                        enabled = true,
                        onSensorClicked = navigateFavorites
                    )
                    SettingsRow(
                        primaryText = stringResource(commonR.string.template_tile),
                        secondaryText = stringResource(commonR.string.set_favorites_on_device),
                        mdiIcon = CommunityMaterial.Icon3.cmd_text_box,
                        enabled = true,
                        onSensorClicked = navigateTemplateTile
                    )
                }
                else -> {
                    Button(
                        onClick = loginWearOs,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, start = 16.dp, end = 16.dp)
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
