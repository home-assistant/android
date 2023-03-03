package io.homeassistant.companion.android.onboarding.phoneinstall

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.rememberScalingLazyListState
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.home.views.TimeText
import io.homeassistant.companion.android.views.ThemeLazyColumn
import io.homeassistant.companion.android.common.R as commonR

@Composable
fun PhoneInstallView(
    onInstall: () -> Unit,
    onRefresh: () -> Unit,
    onAdvanced: () -> Unit
) {
    val scrollState = rememberScalingLazyListState()
    Scaffold(
        positionIndicator = {
            if (scrollState.isScrollInProgress) {
                PositionIndicator(scalingLazyListState = scrollState)
            }
        },
        timeText = { TimeText(visible = !scrollState.isScrollInProgress) }
    ) {
        Box(modifier = Modifier.background(MaterialTheme.colors.background)) {
            ThemeLazyColumn(state = scrollState) {
                item {
                    Image(
                        painter = painterResource(R.drawable.app_icon),
                        contentDescription = null,
                        modifier = Modifier.size(48.dp)
                    )
                }
                item {
                    Text(
                        text = stringResource(commonR.string.install_phone_to_continue),
                        style = MaterialTheme.typography.title3,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                    )
                }
                item {
                    Button(
                        onClick = onInstall,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(commonR.string.install))
                    }
                }
                item {
                    Button(
                        onClick = onRefresh,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.secondaryButtonColors()
                    ) {
                        Text(stringResource(commonR.string.refresh))
                    }
                }
                item {
                    Button(
                        onClick = onAdvanced,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        colors = ButtonDefaults.secondaryButtonColors()
                    ) {
                        Text(stringResource(commonR.string.advanced))
                    }
                }
            }
        }
    }
}

@Preview(device = Devices.WEAR_OS_LARGE_ROUND)
@Composable
fun PhoneInstallViewPreview() {
    PhoneInstallView(
        onInstall = { },
        onRefresh = { },
        onAdvanced = { }
    )
}
