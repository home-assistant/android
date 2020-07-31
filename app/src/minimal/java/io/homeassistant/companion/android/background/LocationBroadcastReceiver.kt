package io.homeassistant.companion.android.background

import android.content.Context
import android.content.Intent

class LocationBroadcastReceiver : LocationBroadcastReceiverBase() {
    override fun setupLocationTracking(context: Context) {
        // No op
    }

    override fun handleLocationUpdate(intent: Intent) {
        // No op
    }

    override fun handleGeoUpdate(context: Context, intent: Intent) {
        // No op
    }

    override fun requestSingleAccurateLocation(context: Context) {
        // No op
    }
}
