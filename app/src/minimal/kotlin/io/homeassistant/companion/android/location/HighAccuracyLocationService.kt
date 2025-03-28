package io.homeassistant.companion.android.location

import android.content.Context
import android.location.Location

object HighAccuracyLocationService {
    fun updateNotificationAddress(context: Context, location: Location, geocodedAddress: String = "") {
        // no-op
    }
}
