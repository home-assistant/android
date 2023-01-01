package io.homeassistant.companion.android.onboarding

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.data.authentication.AuthenticationRepository
import io.homeassistant.companion.android.common.data.url.UrlRepository
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class WearOnboardingListener : WearableListenerService() {

    @Inject
    lateinit var authenticationUseCase: AuthenticationRepository

    @Inject
    lateinit var urlUseCase: UrlRepository

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
        urlUseCase.getUrl(false)?.let { url ->
            // Put as DataMap in data layer
            val putDataReq: PutDataRequest = PutDataMapRequest.create("/home_assistant_instance").run {
                dataMap.putString("name", url.host.toString())
                dataMap.putString("url", url.toString())
                setUrgent()
                asPutDataRequest()
            }
            Wearable.getDataClient(this@WearOnboardingListener).putDataItem(putDataReq).addOnCompleteListener {
                Log.d("WearOnboardingListener", "sendHomeAssistantInstance: ${if (it.isSuccessful) "success" else "failed"}")
            }
        }
    }
}
