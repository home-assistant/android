package io.homeassistant.companion.android.onboarding.notifications

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import io.homeassistant.companion.android.onboarding.OnboardingScreen
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme

@Composable
fun NotificationPermissionView(onSetNotificationsEnabled: (Boolean) -> Unit) {
    val scrollState = rememberScrollState()
    OnboardingScreen {
        Column(
            modifier = Modifier
                .verticalScroll(scrollState)
                .fillMaxWidth()
                .weight(1f),
        ) {
            OnboardingHeaderView(
                icon = CommunityMaterial.Icon.cmd_bell_outline,
                title = stringResource(id = commonR.string.notifications),
            )
            Text(
                text = stringResource(id = commonR.string.onboarding_notifications_subtitle),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(bottom = 48.dp)
                    .align(Alignment.CenterHorizontally),
            )
            OnboardingPermissionBullet(
                icon = CommunityMaterial.Icon.cmd_alert_decagram,
                text = stringResource(id = commonR.string.onboarding_notifications_bullet_alert),
            )
            OnboardingPermissionBullet(
                icon = CommunityMaterial.Icon3.cmd_text,
                text = stringResource(id = commonR.string.onboarding_notifications_bullet_commands),
            )
        }
        Row(modifier = Modifier.padding(top = 16.dp)) {
            TextButton(onClick = { onSetNotificationsEnabled(false) }) {
                Text(stringResource(id = commonR.string.skip))
            }
            Spacer(modifier = Modifier.weight(1f))
            Button(onClick = { onSetNotificationsEnabled(true) }) {
                Text(stringResource(id = commonR.string.continue_connect))
            }
        }
    }
}

@Preview(showSystemUi = true)
@Composable
fun NotificationPermissionViewPreview() {
    HomeAssistantAppTheme {
        NotificationPermissionView(
            onSetNotificationsEnabled = {},
        )
    }
}
