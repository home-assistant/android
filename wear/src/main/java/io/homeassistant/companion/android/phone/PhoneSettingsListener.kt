package io.homeassistant.companion.android.phone

import android.net.Uri
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

class PhoneSettingsListener : WearableListenerService(), DataClient.OnDataChangedListener {

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
        } else if (event.path == "/save_home_favorites")
            saveFavorites()
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        Log.d(TAG, "onDataChanged ${dataEvents.count}")
        dataEvents.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED) {
                event.dataItem.also { item ->
                    if (item.uri.path?.compareTo("/save_home_favorites") == 0) {
                        val data = getHomeFavorites(DataMapItem.fromDataItem(item).dataMap)
                        Log.d(TAG, "onDataChanged: Received home favorites: $data")
                        saveFavorites()
                    }
                }
            }
        }
    }

    private fun getHomeFavorites(map: DataMap): String {
        map.apply {
            return getString("favorites", "")
        }
    }

    private fun sendHomeFavorites(nodeId: String) = runBlocking {
        Log.d(TAG, "sendHomeFavorites to: $nodeId")
        val currentFavorites = integrationUseCase.getWearHomeFavorites().toList()

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

    private fun saveFavorites() {
        Log.d(TAG, "Finding existing favorites")
        Tasks.await(Wearable.getDataClient(this).getDataItems(Uri.parse("wear://*/save_home_favorites"))).apply {
            Log.d(TAG, "Found existing favorites: ${this.count}")
            this.forEach {
                val data = getHomeFavorites(DataMapItem.fromDataItem(this.first()).dataMap)
                Log.d(TAG, "Favorites: ${data.removeSurrounding("[", "]").split(", ").map { it }}")
                runBlocking {
                    integrationUseCase.setWearHomeFavorites(
                        data.removeSurrounding("[", "]").split(", ").map { it }.toSet()
                    )
                }
            }
        }
    }
}
