package io.homeassistant.companion.android.sensors

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import timber.log.Timber

/**
 * Exported entry point for the public
 * `io.homeassistant.companion.android.background.REQUEST_ACCURATE_UPDATE` action used by external
 * automation tools (e.g. Tasker) to ask the app for a fresh accurate location reading.
 *
 * Acts as a thin, proxy in front of [LocationSensorManager], which itself stays
 * unexported. We forward only the accurate-update action via an explicit-component broadcast and
 * deliberately drop any extras supplied by the caller.
 */
class RequestAccurateLocationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != LocationSensorManager.ACTION_REQUEST_ACCURATE_LOCATION_UPDATE) {
            Timber.w("Ignoring unexpected action on RequestAccurateLocationReceiver: ${intent.action}")
            return
        }
        context.sendBroadcast(LocationSensorManager.createRequestAccurateLocationUpdateIntent(context))
    }
}
