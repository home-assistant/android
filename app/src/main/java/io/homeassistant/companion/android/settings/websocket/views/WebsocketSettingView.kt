package io.homeassistant.companion.android.settings.websocket.views

import android.app.UiModeManager
import android.content.res.Configuration
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.util.websocketChannel
import io.homeassistant.companion.android.database.settings.WebsocketSetting
import io.homeassistant.companion.android.util.compose.InfoNotification
import io.homeassistant.companion.android.util.compose.RadioButtonRow

@Composable
fun WebsocketSettingView(
    websocketSetting: WebsocketSetting,
    onSettingChanged: (WebsocketSetting) -> Unit
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    Box(modifier = Modifier.verticalScroll(scrollState)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.websocket_setting_description),
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Divider()
            RadioButtonRow(
                text = stringResource(if (BuildConfig.FLAVOR == "full") R.string.websocket_setting_never else R.string.websocket_setting_never_minimal),
                selected = websocketSetting == WebsocketSetting.NEVER,
                onClick = { onSettingChanged(WebsocketSetting.NEVER) }
            )
            RadioButtonRow(
                text = stringResource(R.string.websocket_setting_home_wifi),
                selected = websocketSetting == WebsocketSetting.HOME_WIFI,
                onClick = { onSettingChanged(WebsocketSetting.HOME_WIFI) }
            )
            RadioButtonRow(
                text = stringResource(if (BuildConfig.FLAVOR == "full") R.string.websocket_setting_while_screen_on else R.string.websocket_setting_while_screen_on_minimal),
                selected = websocketSetting == WebsocketSetting.SCREEN_ON,
                onClick = { onSettingChanged(WebsocketSetting.SCREEN_ON) }
            )
            RadioButtonRow(
                text = stringResource(if (BuildConfig.FLAVOR == "full") R.string.websocket_setting_always else R.string.websocket_setting_always_minimal),
                selected = websocketSetting == WebsocketSetting.ALWAYS,
                onClick = { onSettingChanged(WebsocketSetting.ALWAYS) }
            )
            val uiManager = context.getSystemService<UiModeManager>()
            if (websocketSetting != WebsocketSetting.NEVER && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && uiManager?.currentModeType != Configuration.UI_MODE_TYPE_TELEVISION) {
                InfoNotification(
                    infoString = R.string.websocket_persistent_notification,
                    channelId = websocketChannel,
                    buttonString = R.string.websocket_notification_channel
                )
            }
        }
    }
}
