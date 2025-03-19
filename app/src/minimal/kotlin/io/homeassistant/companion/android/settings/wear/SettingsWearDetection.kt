package io.homeassistant.companion.android.settings.wear

import android.content.Context

object SettingsWearDetection {

    /**
     * Returns if there are any Wear OS devices connected to this device. It does **not** indicate
     * if they have the Home Assistant app installed.
     */
    suspend fun hasAnyNodes(context: Context): Boolean {
        // The minimal version of the app doesn't support Wear OS, so always return false
        return false
    }
}
