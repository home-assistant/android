package io.homeassistant.companion.android.wear.background.capability

import android.util.Log
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Node
import io.homeassistant.companion.android.util.extensions.await
import io.homeassistant.companion.android.util.extensions.catch
import io.homeassistant.companion.android.wear.BuildConfig
import io.homeassistant.companion.android.wear.background.CapabilityResult
import io.homeassistant.companion.android.wear.background.Result
import javax.inject.Inject

class CapabilityManager @Inject constructor(
    private val capabilityClient: CapabilityClient
) {

    private companion object {
        private const val TAG = "CapabilityManager"

        private const val CAPABILITY_PHONE = "verify_home_assistant_phone_app_installed"
    }

    suspend fun getNodeWithInstalledApp(): CapabilityResult? {
        val capabilityInfo = catch {
            capabilityClient.getCapability(CAPABILITY_PHONE, CapabilityClient.FILTER_ALL).await()
        }
        val nodes: MutableSet<Node>? = capabilityInfo?.nodes
        if (nodes == null || nodes.isEmpty()) {
            return CapabilityResult(Result.FAILURE)
        }
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Nodes found: $nodes")
        }
        val foundDevice = nodes.find { node -> node.isNearby }
            ?: return CapabilityResult(Result.NOT_NEARBY)

        return CapabilityResult(Result.SUCCESS, foundDevice)
    }

}