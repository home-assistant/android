package io.homeassistant.companion.android.phone

import android.net.Uri
import android.util.Log
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PhoneSettingsListener : WearableListenerService(), DataClient.OnDataChangedListener {

    @Inject
    lateinit var integrationUseCase: IntegrationRepository

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    companion object {
        private const val TAG = "PhoneSettingsListener"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "We have favorite message listener")
    }

    override fun onMessageReceived(event: MessageEvent) {
        Log.d(TAG, "Message received: $event")
        if (event.path == "/send_home_favorites") {
            val nodeId = event.sourceNodeId
            sendHomeFavorites(nodeId)
        }
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

    private fun sendHomeFavorites(nodeId: String) = mainScope.launch {
        Log.d(TAG, "sendHomeFavorites to: $nodeId")
        val currentFavorites = integrationUseCase.getWearHomeFavorites().toList()

        val putDataRequest = PutDataMapRequest.create("/home_favorites").run {
            dataMap.putString("favorites", currentFavorites.toString())
            setUrgent()
            asPutDataRequest()
        }

        Wearable.getDataClient(this@PhoneSettingsListener).putDataItem(putDataRequest).apply {
            addOnSuccessListener { Log.d(TAG, "Successfully sent favorites to device") }
            addOnFailureListener { e ->
                Log.e(TAG, "Failed to send favorites to device", e)
            }
        }
    }

    private fun saveFavorites() {
        Log.d(TAG, "Finding existing favorites")
        mainScope.launch {
            Wearable.getDataClient(applicationContext).getDataItems(Uri.parse("wear://*/save_home_favorites"))
                .addOnSuccessListener {
                    Log.d(TAG, "Found existing favorites: ${it.count}")
                    it.forEach { dataItem ->
                        val data = getHomeFavorites(DataMapItem.fromDataItem(dataItem).dataMap)
                        Log.d(
                            TAG,
                            "Favorites: ${data.removeSurrounding("[", "]").split(", ").map { it }}"
                        )
                        mainScope.launch {
                            integrationUseCase.setWearHomeFavorites(
                                data.removeSurrounding("[", "]").split(", ").map { it }.toSet()
                            )
                        }
                    }
                }
        }
    }
}
