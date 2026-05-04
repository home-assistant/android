package io.homeassistant.companion.android.websocket

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.homeassistant.companion.android.common.util.launchAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class WebsocketBroadcastReceiver : BroadcastReceiver() {
    private val receiverScope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent) {
        launchAsync(receiverScope) {
            WebsocketManager.start(context)
        }
    }
}
