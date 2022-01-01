package io.homeassistant.companion.android.settings.notification.views

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.RadioButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.database.settings.LocalNotificationSetting

@Composable
fun LocalNotificationSettingsView(
    localNotificationSetting: LocalNotificationSetting,
    onSettingChanged: (LocalNotificationSetting) -> Unit
) {
    Column(modifier = Modifier.padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.local_notif_description))
        }
        RadioButtonRow(
            text = stringResource(R.string.local_notif_never),
            selected = localNotificationSetting == LocalNotificationSetting.NEVER,
            onClick = { onSettingChanged(LocalNotificationSetting.NEVER) }
        )
        RadioButtonRow(
            text = stringResource(R.string.local_notif_while_screen_on),
            selected = localNotificationSetting == LocalNotificationSetting.SCREEN_ON,
            onClick = { onSettingChanged(LocalNotificationSetting.SCREEN_ON) }
        )
        RadioButtonRow(
            text = stringResource(R.string.local_notif_always),
            selected = localNotificationSetting == LocalNotificationSetting.ALWAYS,
            onClick = { onSettingChanged(LocalNotificationSetting.ALWAYS) }
        )
    }
}

@Composable
fun RadioButtonRow(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(text)
    }
}
