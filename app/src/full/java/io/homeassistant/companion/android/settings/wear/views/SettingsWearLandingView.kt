package io.homeassistant.companion.android.settings.wear.views

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    loginWearOs: () -> Unit
) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(commonR.string.wear_settings)) },
                actions = {
                    IconButton(onClick = {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(WEAR_DOCS_LINK)
                        )
                        context.startActivity(intent)
                    }) {
                        Icon(
                            Icons.Filled.HelpOutline,
                            contentDescription = stringResource(id = commonR.string.help)
                        )
                    }
                }
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, top = 10.dp, end = 20.dp)
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
                            .padding(top = 10.dp, end = 10.dp)
                    ) {
                        Text(stringResource(commonR.string.set_favorites_on_device))
                    }
                    Button(
                        onClick = navigateTemplateTile,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, end = 10.dp)
                    ) {
                        Text(stringResource(commonR.string.template_tile))
                    }
                }
                else -> {
                    Button(
                        onClick = loginWearOs,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, end = 10.dp)
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
        loginWearOs = {}
    )
}
