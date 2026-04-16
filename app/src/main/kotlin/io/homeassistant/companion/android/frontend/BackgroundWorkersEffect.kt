package io.homeassistant.companion.android.frontend

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.homeassistant.companion.android.sensors.SensorReceiver
import io.homeassistant.companion.android.sensors.SensorWorker
import io.homeassistant.companion.android.websocket.WebsocketManager
import kotlinx.coroutines.launch

/**
 * Starts background workers on resume and triggers a sensor update on pause.
 *
 * On resume, kicks off periodic sensor collection via [SensorWorker] and starts
 * the persistent WebSocket connection via [WebsocketManager].
 * On pause, triggers an immediate sensor update via [SensorReceiver] so the server
 * has fresh data before the app goes to the background.
 */
@Composable
internal fun BackgroundWorkersEffect() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    LifecycleResumeEffect(Unit) {
        SensorWorker.start(context)
        coroutineScope.launch {
            WebsocketManager.start(context)
        }
        onPauseOrDispose {
            SensorReceiver.updateAllSensors(context)
        }
    }
}
