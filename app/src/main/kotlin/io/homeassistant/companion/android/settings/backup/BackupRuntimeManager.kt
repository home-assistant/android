package io.homeassistant.companion.android.settings.backup

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.sensors.SensorReceiver
import io.homeassistant.companion.android.websocket.WebsocketManager
import javax.inject.Inject

/** Refreshes background work after configuration has been committed. */
internal class BackupRuntimeManager @Inject constructor(@ApplicationContext private val context: Context) {
    suspend fun refresh() {
        SensorReceiver.updateAllSensors(context)
        WebsocketManager.start(context)
    }
}
