package io.homeassistant.companion.android.websocket

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class WebsocketBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        WebsocketManager.start(context)
    }
}
