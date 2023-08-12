package io.homeassistant.companion.android.onboarding.notifications

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.accompanist.themeadapter.material.MdcTheme
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.onboarding.OnboardingHeaderView
import io.homeassistant.companion.android.common.R as commonR

@Composable
fun NotificationPermissionView(
    onSetNotificationsEnabled: (Boolean) -> Unit
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(scrollState)
                .fillMaxWidth()
                .weight(1f)
        ) {
            OnboardingHeaderView(
                icon = CommunityMaterial.Icon.cmd_bell_outline,
                title = stringResource(id = commonR.string.notifications)
            )
            Text(
                text = stringResource(id = commonR.string.onboarding_notifications_subtitle),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(bottom = 48.dp)
                    .align(Alignment.CenterHorizontally)
            )
            NotificationPermissionBullet(
                icon = CommunityMaterial.Icon.cmd_alert_decagram,
                text = stringResource(id = commonR.string.onboarding_notifications_bullet_alert)
            )
            NotificationPermissionBullet(
                icon = CommunityMaterial.Icon3.cmd_text,
                text = stringResource(id = commonR.string.onboarding_notifications_bullet_commands)
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

@Composable
fun NotificationPermissionBullet(
    icon: IIcon,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 12.dp)
    ) {
        Image(
            asset = icon,
            colorFilter = ColorFilter.tint(MaterialTheme.colors.onSurface),
            contentDescription = null
        )
        Text(
            text = text,
            modifier = Modifier
                .padding(start = 16.dp)
                .fillMaxWidth()
        )
    }
}

@Preview(showSystemUi = true)
@Composable
fun NotificationPermissionViewPreview() {
    MdcTheme {
        NotificationPermissionView(
            onSetNotificationsEnabled = {}
        )
    }
}
