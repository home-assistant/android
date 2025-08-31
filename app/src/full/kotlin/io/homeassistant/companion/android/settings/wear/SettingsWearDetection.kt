package io.homeassistant.companion.android.settings.wear

import android.content.Context
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.wearable.Wearable
import io.homeassistant.companion.android.common.util.isAutomotive
import kotlinx.coroutines.tasks.await
import timber.log.Timber

object SettingsWearDetection {

    /**
     * Returns if there are any Wear OS devices connected to this device. It does **not** indicate
     * if they have the Home Assistant app installed.
     */
    suspend fun hasAnyNodes(context: Context): Boolean {
        if (context.isAutomotive()) return false
        return try {
            val nodeClient = Wearable.getNodeClient(context)
            nodeClient.connectedNodes.await().any()
        } catch (e: Exception) {
            if (e is ApiException &&
                e.statusCode == CommonStatusCodes.API_NOT_CONNECTED &&
                e.message?.contains("API_UNAVAILABLE") == true
            ) {
                // Wearable.API is not available on this device.
                Timber.d("API unavailable for discovering nodes (no Wear)")
            } else {
                Timber.e(e, "Exception while discovering nodes")
            }
            false
        }
    }
}
