package io.homeassistant.companion.android.onboarding

import com.google.android.gms.common.api.ApiException
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.data.servers.ServerManager
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber

@AndroidEntryPoint
class WearOnboardingListener : WearableListenerService() {

    @Inject
    lateinit var serverManager: ServerManager
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onMessageReceived(event: MessageEvent) {
        Timber.d("onMessageReceived: $event")

        if (event.path == "/request_home_assistant_instance") {
            val nodeId = event.sourceNodeId
            sendHomeAssistantInstance(nodeId)
        }
    }

    private fun sendHomeAssistantInstance(nodeId: String) {
        serviceScope.launch {
            Timber.d("sendHomeAssistantInstance: $nodeId")
            // Retrieve current instance
            val server = serverManager.getServer()
            val url = server?.let { serverManager.connectionStateProvider(it.id).getExternalUrl() }

            if (url != null) {
                // Put as DataMap in data layer
                val putDataReq: PutDataRequest = PutDataMapRequest.create("/home_assistant_instance").run {
                    dataMap.putString("name", url.host.orEmpty())
                    dataMap.putString("url", url.toString())
                    setUrgent()
                    asPutDataRequest()
                }
                try {
                    Wearable.getDataClient(this@WearOnboardingListener)
                        .putDataItem(putDataReq)
                        .await()
                    Timber.d("sendHomeAssistantInstance: success")
                } catch (e: ApiException) {
                    Timber.e(e, "Failed to send home assistant instance")
                }
            }
        }
    }
}
