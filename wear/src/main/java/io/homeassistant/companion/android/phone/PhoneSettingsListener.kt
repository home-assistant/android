package io.homeassistant.companion.android.phone

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

class PhoneSettingsListener : WearableListenerService() {

    @Inject
    lateinit var integrationUseCase: IntegrationRepository

    companion object {
        private const val TAG = "PhoneSettingsListener"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "We have favorite message listener")
        DaggerPhoneSettingsComponent.builder()
            .appComponent((applicationContext.applicationContext as GraphComponentAccessor).appComponent)
            .build()
            .inject(this)
    }

    override fun onMessageReceived(event: MessageEvent) {
        Log.d(TAG, "Message received: $event")
        if (event.path == "/send_home_favorites") {
            val nodeId = event.sourceNodeId
            sendHomeFavorites(nodeId)
        }
    }

    private fun sendHomeFavorites(nodeId: String) = runBlocking {
        Log.d(TAG, "sendHomeFavorites to: $nodeId")
        val currentFavorites = integrationUseCase.getWearHomeFavorites()

        val putDataRequest = PutDataMapRequest.create("/home_favorites").run {
            dataMap.putString("favorites", currentFavorites.toString())
            setUrgent()
            asPutDataRequest()
        }

        Wearable.getDataClient(this@PhoneSettingsListener).putDataItem(putDataRequest).apply {
            addOnSuccessListener { Log.d(TAG, "Successfully sent favorites to device") }
            addOnFailureListener { Log.d(TAG, "Failed to send favorites to device") }
        }
    }
}
