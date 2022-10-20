package io.homeassistant.companion.android.settings.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

object SettingsWearDetection {

    private const val TAG = "SettingsWearDetection"

    /**
     * Returns if there are any Wear OS devices connected to this device. It does **not** indicate
     * if they have the Home Assistant app installed.
     */
    suspend fun hasAnyNodes(context: Context): Boolean {
        return try {
            val nodeClient = Wearable.getNodeClient(context)
            nodeClient.connectedNodes.await().any()
        } catch (e: Exception) {
            Log.e(TAG, "Exception while discovering nodes", e)
            false
        }
    }
}
