package io.homeassistant.companion.android.settings.websocket.views

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
import io.homeassistant.companion.android.database.settings.WebsocketSetting

@Composable
fun WebsocketSettingView(
    websocketSetting: WebsocketSetting,
    onSettingChanged: (WebsocketSetting) -> Unit
) {
    Column(modifier = Modifier.padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.websocket_setting_description))
        }
        RadioButtonRow(
            text = stringResource(R.string.websocket_setting_never),
            selected = websocketSetting == WebsocketSetting.NEVER,
            onClick = { onSettingChanged(WebsocketSetting.NEVER) }
        )
        RadioButtonRow(
            text = stringResource(R.string.websocket_setting_while_screen_on),
            selected = websocketSetting == WebsocketSetting.SCREEN_ON,
            onClick = { onSettingChanged(WebsocketSetting.SCREEN_ON) }
        )
        RadioButtonRow(
            text = stringResource(R.string.websocket_setting_always),
            selected = websocketSetting == WebsocketSetting.ALWAYS,
            onClick = { onSettingChanged(WebsocketSetting.ALWAYS) }
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
