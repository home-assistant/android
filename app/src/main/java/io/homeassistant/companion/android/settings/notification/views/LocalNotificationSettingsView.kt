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
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.database.settings.LocalNotificationSetting

@Composable
fun LocalNotificationSettingsView(
    localNotificationSetting: LocalNotificationSetting,
    onSettingChanged: (LocalNotificationSetting) -> Unit
) {
    Column(modifier = Modifier.padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Please select when you would like the application to attempt to directly communicate with your Home Assistant instance to attempt to bypass Firebase Cloud Messaging for push notifications.")
        }
        RadioButtonRow(
            text = "Never",
            selected = localNotificationSetting == LocalNotificationSetting.NEVER,
            onClick = { onSettingChanged(LocalNotificationSetting.NEVER) }
        )
        RadioButtonRow(
            text = "While screen on",
            selected = localNotificationSetting == LocalNotificationSetting.SCREEN_ON,
            onClick = { onSettingChanged(LocalNotificationSetting.SCREEN_ON) }
        )
        RadioButtonRow(
            text = "All the time",
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
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(text)
    }
}
