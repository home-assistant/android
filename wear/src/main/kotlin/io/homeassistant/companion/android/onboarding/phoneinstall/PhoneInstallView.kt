package io.homeassistant.companion.android.onboarding.phoneinstall

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.tooling.preview.devices.WearDevices
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.theme.getFilledTonalButtonColors
import io.homeassistant.companion.android.views.ThemeLazyColumn

@Composable
fun PhoneInstallView(onInstall: () -> Unit, onRefresh: () -> Unit, onAdvanced: () -> Unit) {
    ThemeLazyColumn {
        item {
            Image(
                painter = painterResource(R.drawable.launcher_icon_round),
                contentDescription = null,
                modifier = Modifier.size(width = 48.dp, height = 72.dp).padding(top = 24.dp),
            )
        }
        item {
            Text(
                text = stringResource(commonR.string.install_phone_to_continue),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
            )
        }
        item {
            Button(
                onClick = onInstall,
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(
                        stringResource(commonR.string.install),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
            )
        }
        item {
            Button(
                onClick = onRefresh,
                modifier = Modifier.fillMaxWidth(),
                colors = getFilledTonalButtonColors(),
                label = {
                    Text(
                        stringResource(commonR.string.refresh),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
            )
        }
        item {
            Button(
                onClick = onAdvanced,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                colors = getFilledTonalButtonColors(),
                label = {
                    Text(
                        stringResource(commonR.string.advanced),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
            )
        }
    }
}

@Preview(device = WearDevices.LARGE_ROUND)
@Composable
fun PhoneInstallViewPreview() {
    PhoneInstallView(
        onInstall = { },
        onRefresh = { },
        onAdvanced = { },
    )
}
