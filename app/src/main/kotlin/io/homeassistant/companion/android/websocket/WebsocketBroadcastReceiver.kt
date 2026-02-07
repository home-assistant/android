package io.homeassistant.companion.android.websocket

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.homeassistant.companion.android.common.util.launchAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job

class WebsocketBroadcastReceiver : BroadcastReceiver() {
    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job())

    override fun onReceive(context: Context, intent: Intent) {
        launchAsync(ioScope) {
            WebsocketManager.start(context)
        }
    }
}
