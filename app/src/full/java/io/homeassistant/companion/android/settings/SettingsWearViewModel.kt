package io.homeassistant.companion.android.settings

import android.app.Application
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.HomeAssistantApplication
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import kotlinx.coroutines.launch
import org.burnoutcrew.reorderable.ItemPosition
import org.burnoutcrew.reorderable.move
import org.json.JSONArray
import javax.inject.Inject
import io.homeassistant.companion.android.common.R as commonR

@HiltViewModel
class SettingsWearViewModel @Inject constructor(
    private val integrationUseCase: IntegrationRepository,
    application: Application
) :
    AndroidViewModel(application),
    DataClient.OnDataChangedListener {

    private val application = getApplication<HomeAssistantApplication>()
    companion object {
        private const val TAG = "SettingsWearViewModel"
        private const val CAPABILITY_WEAR_FAVORITES = "send_home_favorites"
    }

    var entities = mutableStateMapOf<String, Entity<*>>()
        private set
    var favoriteEntityIds = mutableStateListOf<String>()

    fun init() {
        loadEntities()
    }

    private fun loadEntities() {
        viewModelScope.launch {
            integrationUseCase.getEntities().forEach {
                entities[it.entityId] = it
            }
        }
    }

    private fun saveHomeFavorites(data: String) {
        val jsonString = JSONArray(data)
        Log.d(TAG, "saveHomeFavorites: $jsonString")
        favoriteEntityIds.clear()
        for (favorite in 0 until jsonString.length()) {
            favoriteEntityIds.add(
                jsonString.getString(favorite)
            )
        }
    }

    fun onEntitySelected(checked: Boolean, entityId: String) {
        if (checked)
            favoriteEntityIds.add(entityId)
        else
            favoriteEntityIds.remove(entityId)
        sendHomeFavorites(favoriteEntityIds.toList())
    }

    fun onMove(fromItem: ItemPosition, toItem: ItemPosition) {
        favoriteEntityIds.move(favoriteEntityIds.indexOfFirst { it == fromItem.key }, favoriteEntityIds.indexOfFirst { it == toItem.key })
    }

    fun canDragOver(position: ItemPosition) = favoriteEntityIds.any { it == position.key }

    fun sendHomeFavorites(favoritesList: List<String>) = viewModelScope.launch {
        Log.d(TAG, "sendHomeFavorites")

        val putDataRequest = PutDataMapRequest.create("/save_home_favorites").run {
            dataMap.putString("favorites", favoritesList.toString())
            setUrgent()
            asPutDataRequest()
        }

        Wearable.getDataClient(application).putDataItem(putDataRequest).apply {
            addOnSuccessListener { Log.d(TAG, "Successfully sent favorites to wear") }
            addOnFailureListener { e ->
                Log.e(TAG, "Failed to send favorites to wear", e)
                Toast.makeText(application, application.getString(commonR.string.failure_send_favorites_wear), Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun findExistingFavorites() {
        Log.d(TAG, "Finding existing favorites")
        viewModelScope.launch {
            Wearable.getDataClient(application).getDataItems(Uri.parse("wear://*/home_favorites"))
                .addOnSuccessListener { dataItemBuffer ->
                    Log.d(TAG, "Found existing favorites: ${dataItemBuffer.count}")
                    dataItemBuffer.forEach {
                        val data = getFavorites(DataMapItem.fromDataItem(it).dataMap)
                        saveHomeFavorites(data)
                    }
                    dataItemBuffer.release()
                }
        }
    }

    private fun getFavorites(map: DataMap): String {
        return map.getString("favorites", "")
    }

    fun requestFavorites() {
        Log.d(TAG, "Requesting favorites")

        viewModelScope.launch {
            Wearable.getCapabilityClient(application)
                .getCapability(CAPABILITY_WEAR_FAVORITES, CapabilityClient.FILTER_REACHABLE)
                .addOnSuccessListener {

                    it.nodes.forEach { node ->
                        Log.d(TAG, "Requesting favorite data")
                        Wearable.getMessageClient(application).sendMessage(
                            node.id,
                            "/send_home_favorites",
                            ByteArray(0)
                        ).apply {
                            addOnSuccessListener {
                                Log.d(
                                    TAG,
                                    "Request to favorites sent successfully"
                                )
                            }
                            addOnFailureListener { e ->
                                Log.e(TAG, "Failed to get favorites", e)
                            }
                        }
                    }
                }
        }
    }

    fun startWearListening() {
        Wearable.getDataClient(application).addListener(this)
    }

    fun stopWearListening() {
        Wearable.getDataClient(application).removeListener(this)
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        Log.d(TAG, "onDataChanged ${dataEvents.count}")
        dataEvents.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED) {
                event.dataItem.also { item ->
                    if (item.uri.path?.compareTo("/home_favorites") == 0) {
                        val data = getFavorites(DataMapItem.fromDataItem(item).dataMap)
                        saveHomeFavorites(data)
                    }
                }
            }
        }
        dataEvents.release()
    }
}
