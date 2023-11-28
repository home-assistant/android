package io.homeassistant.companion.android.onboarding

import android.annotation.SuppressLint
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.data.servers.ServerManager
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
@SuppressLint("VisibleForTests") // https://issuetracker.google.com/issues/239451111
class WearOnboardingListener : WearableListenerService() {

    companion object {
        private const val TAG = "WearOnboardingListener"
    }

    @Inject
    lateinit var serverManager: ServerManager

    override fun onMessageReceived(event: MessageEvent) {
        Log.d("WearOnboardingListener", "onMessageReceived: $event")

        if (event.path == "/request_home_assistant_instance") {
            val nodeId = event.sourceNodeId
            sendHomeAssistantInstance(nodeId)
        }
    }

    private fun sendHomeAssistantInstance(nodeId: String) = runBlocking {
        Log.d("WearOnboardingListener", "sendHomeAssistantInstance: $nodeId")
        // Retrieve current instance
        val url = serverManager.getServer()?.connection?.getUrl(false)

        if (url != null) {
            // Put as DataMap in data layer
            val putDataReq: PutDataRequest = PutDataMapRequest.create("/home_assistant_instance").run {
                dataMap.putString("name", url.host.toString())
                dataMap.putString("url", url.toString())
                setUrgent()
                asPutDataRequest()
            }
            try {
                Wearable.getDataClient(this@WearOnboardingListener).putDataItem(putDataReq)
                    .addOnCompleteListener {
                        Log.d(
                            TAG,
                            "sendHomeAssistantInstance: ${if (it.isSuccessful) "success" else "failed"}"
                        )
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send home assistant instance", e)
            }
        }
    }
}
