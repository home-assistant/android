package io.homeassistant.companion.android.onboarding.integration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.onboarding.OnboardingHeaderView
import io.homeassistant.companion.android.onboarding.OnboardingViewModel
import io.homeassistant.companion.android.common.R as commonR

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MobileAppIntegrationView(
    onboardingViewModel: OnboardingViewModel,
    openPrivacyPolicy: () -> Unit,
    onLocationTrackingChanged: (Boolean) -> Unit,
    onFinishClicked: () -> Unit
) {
    val scrollState = rememberScrollState()
    val keyboardController = LocalSoftwareKeyboardController.current
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(scrollState)
                .fillMaxWidth()
                .weight(1f)
        ) {
            OnboardingHeaderView(
                icon = if (onboardingViewModel.deviceIsWatch) {
                    CommunityMaterial.Icon3.cmd_watch
                } else if (LocalConfiguration.current.screenWidthDp.dp >= 600.dp) {
                    CommunityMaterial.Icon3.cmd_tablet
                } else {
                    CommunityMaterial.Icon.cmd_cellphone
                },
                title = stringResource(id = commonR.string.connect_to_home_assistant)
            )

            TextField(
                value = onboardingViewModel.deviceName.value,
                onValueChange = { onboardingViewModel.onDeviceNameUpdated(it) },
                modifier = Modifier.align(Alignment.CenterHorizontally),
                label = { Text(stringResource(id = commonR.string.device_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        keyboardController?.hide()
                    }
                )
            )
            if (onboardingViewModel.locationTrackingPossible.value) {
                Row {
                    Text(
                        text = stringResource(commonR.string.enable_location_tracking),
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                            .weight(1f)
                    )
                    Switch(
                        checked = onboardingViewModel.locationTrackingEnabled,
                        onCheckedChange = onLocationTrackingChanged,
                        colors = SwitchDefaults.colors(uncheckedThumbColor = colorResource(commonR.color.colorSwitchUncheckedThumb))
                    )
                }
                Text(
                    text = stringResource(id = commonR.string.enable_location_tracking_description),
                    fontWeight = FontWeight.Light
                )
            }
            TextButton(onClick = openPrivacyPolicy) {
                Text(stringResource(id = commonR.string.privacy_url))
            }
        }

        Row(
            modifier = Modifier
                .padding(top = 16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Button(onClick = onFinishClicked) {
                Text(stringResource(id = commonR.string.continue_connect))
            }
        }
    }
}
