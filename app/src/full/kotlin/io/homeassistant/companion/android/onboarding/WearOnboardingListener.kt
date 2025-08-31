package io.homeassistant.companion.android.onboarding

import android.annotation.SuppressLint
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.data.servers.ServerManager
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import timber.log.Timber

@AndroidEntryPoint
@SuppressLint("VisibleForTests") // https://issuetracker.google.com/issues/239451111
class WearOnboardingListener : WearableListenerService() {

    @Inject
    lateinit var serverManager: ServerManager

    override fun onMessageReceived(event: MessageEvent) {
        Timber.d("onMessageReceived: $event")

        if (event.path == "/request_home_assistant_instance") {
            val nodeId = event.sourceNodeId
            sendHomeAssistantInstance(nodeId)
        }
    }

    private fun sendHomeAssistantInstance(nodeId: String) = runBlocking {
        Timber.d("sendHomeAssistantInstance: $nodeId")
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
                        Timber.d(
                            "sendHomeAssistantInstance: ${if (it.isSuccessful) "success" else "failed"}",
                        )
                    }
            } catch (e: Exception) {
                Timber.e(e, "Failed to send home assistant instance")
            }
        }
    }
}
