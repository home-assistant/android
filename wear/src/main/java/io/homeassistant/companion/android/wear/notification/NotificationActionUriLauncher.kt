package io.homeassistant.companion.android.wear.notification

import android.util.Log
import androidx.core.os.bundleOf
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.MessageClient
import io.homeassistant.companion.android.util.extensions.await
import io.homeassistant.companion.android.util.extensions.catch
import io.homeassistant.companion.android.wear.BuildConfig
import io.homeassistant.companion.android.wear.background.Result
import io.homeassistant.companion.android.wear.background.capability.CapabilityManager
import javax.inject.Inject

class NotificationActionUriLauncher @Inject constructor(
    private val messageClient: MessageClient,
    private val capabilityManager: CapabilityManager
) {

    private companion object {
        private const val TAG = "ActionUriLauncher"

        private const val PATH_ACTION = "/action"

        private const val EXTRA_ACTION_URI = "ActionUri"
    }

    suspend fun launchAction(actionUri: String?): Boolean {
        val capabilityResult = capabilityManager.getNodeWithInstalledApp()
        if (capabilityResult == null || capabilityResult.result == Result.FAILURE) {
            logI("Failure trying to figure out to which device the action needs to be send.")
            return false
        } else if (capabilityResult.result == Result.NOT_NEARBY) {
            logI("No handheld found forwarding this action.")
            return false
        } else {
            logI("Found device: ${capabilityResult.deviceNode}")
            val deviceId = capabilityResult.deviceNode.id
            val dataBundle = DataMap.fromBundle(bundleOf(EXTRA_ACTION_URI to actionUri))
            val result = catch {
                messageClient.sendMessage(deviceId, PATH_ACTION, dataBundle.toByteArray()).await()
            }
            return if (result != null) {
                logI("Send message with resultId: $result")
                true
            } else {
                logI("Failure trying to send message to node.")
                false
            }
        }
    }

    private fun logI(message: String) {
        if (BuildConfig.DEBUG) {
            Log.i(TAG, message)
        }
    }
}
