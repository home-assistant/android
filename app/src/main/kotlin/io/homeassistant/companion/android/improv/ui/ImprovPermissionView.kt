package io.homeassistant.companion.android.improv.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.onboarding.OnboardingHeaderView
import io.homeassistant.companion.android.onboarding.OnboardingPermissionBullet
import io.homeassistant.companion.android.util.compose.ModalBottomSheet

@Composable
fun ImprovPermissionView(needsBluetooth: Boolean, needsLocation: Boolean, onContinue: () -> Unit, onSkip: () -> Unit) {
    ModalBottomSheet(title = null) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
        ) {
            OnboardingHeaderView(
                icon = CommunityMaterial.Icon3.cmd_radar,
                title = stringResource(commonR.string.improv_permission_title),
            )
            Text(
                text = stringResource(commonR.string.improv_permission_text),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .align(Alignment.CenterHorizontally),
            )
            if (needsBluetooth) {
                OnboardingPermissionBullet(
                    icon = CommunityMaterial.Icon.cmd_bluetooth,
                    text = stringResource(commonR.string.improv_permission_bluetooth),
                )
            }
            if (needsLocation) {
                OnboardingPermissionBullet(
                    icon = CommunityMaterial.Icon3.cmd_map_marker,
                    text = stringResource(commonR.string.improv_permission_location),
                )
            }
            Spacer(Modifier.height(96.dp))
            Row {
                TextButton(onClick = onSkip) {
                    Text(stringResource(id = commonR.string.skip))
                }
                Spacer(modifier = Modifier.weight(1f))
                Button(onClick = onContinue) {
                    Text(stringResource(id = commonR.string.continue_connect))
                }
            }
        }
    }
}

@Preview
@Composable
fun ImprovPermissionViewPreview() {
    ImprovPermissionView(
        needsBluetooth = true,
        needsLocation = true,
        onContinue = {},
        onSkip = {},
    )
}
