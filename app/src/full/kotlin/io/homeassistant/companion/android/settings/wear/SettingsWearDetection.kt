package io.homeassistant.companion.android.settings.wear

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

object SettingsWearDetection {

    private const val TAG = "SettingsWearDetection"

    /**
     * Returns if there are any Wear OS devices connected to this device. It does **not** indicate
     * if they have the Home Assistant app installed.
     */
    suspend fun hasAnyNodes(context: Context): Boolean {
        if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) return false
        return try {
            val nodeClient = Wearable.getNodeClient(context)
            nodeClient.connectedNodes.await().any()
        } catch (e: Exception) {
            if (e is ApiException && e.statusCode == CommonStatusCodes.API_NOT_CONNECTED && e.message?.contains("API_UNAVAILABLE") == true) {
                // Wearable.API is not available on this device.
                Log.d(TAG, "API unavailable for discovering nodes (no Wear)")
            } else {
                Log.e(TAG, "Exception while discovering nodes", e)
            }
            false
        }
    }
}
