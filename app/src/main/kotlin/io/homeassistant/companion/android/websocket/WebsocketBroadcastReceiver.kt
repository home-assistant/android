package io.homeassistant.companion.android.websocket

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class WebsocketBroadcastReceiver : BroadcastReceiver() {
    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job())

    override fun onReceive(context: Context, intent: Intent) {
        ioScope.launch {
            WebsocketManager.start(context)
        }
    }
}
