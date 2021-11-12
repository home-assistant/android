package io.homeassistant.companion.android.settings

import android.app.Activity
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import kotlinx.coroutines.launch

class SettingsWearViewModel : ViewModel() {

    private lateinit var integrationUseCase: IntegrationRepository

    private val TAG = "SettingsWearViewModel"
    private val CAPABILITY_WEAR_FAVORITES = "send_home_favorites"

    var entities = mutableStateMapOf<String, Entity<*>>()
        private set
    var favoriteEntityIds = mutableStateListOf<String>()

    fun init(integrationUseCase: IntegrationRepository) {
        this.integrationUseCase = integrationUseCase
        loadEntities()
    }

    private fun loadEntities() {
        viewModelScope.launch {
            integrationUseCase.getEntities().forEach {
                entities[it.entityId] = it
            }
        }
    }

    fun saveHomeFavorites(data: String, item: DataItem) {
        getFavorites(DataMapItem.fromDataItem(item).dataMap)
        favoriteEntityIds.clear()
        favoriteEntityIds.addAll(
            data.removeSurrounding("[", "]").split(", ").map { it }
        )
    }

    fun onEntitySelected(checked: Boolean, entityId: String, activity: Activity) {
        if (checked)
            favoriteEntityIds.add(entityId)
        else
            favoriteEntityIds.remove(entityId)
        sendHomeFavorites(favoriteEntityIds.toList(), activity)
    }

    private fun sendHomeFavorites(favoritesList: List<String>, activity: Activity) = viewModelScope.launch {
        Log.d(TAG, "sendHomeFavorites")

        val putDataRequest = PutDataMapRequest.create("/save_home_favorites").run {
            dataMap.putString("favorites", favoritesList.toString())
            setUrgent()
            asPutDataRequest()
        }

        Wearable.getDataClient(activity).putDataItem(putDataRequest).apply {
            addOnSuccessListener { Log.d(TAG, "Successfully sent favorites to wear") }
            addOnFailureListener { Log.d(TAG, "Failed to send favorites to wear") }
        }
    }

    fun findExistingFavorites(activity: Activity) {
        Log.d(TAG, "Finding existing favorites")
        Tasks.await(Wearable.getDataClient(activity).getDataItems(Uri.parse("wear://*/home_favorites"))).apply {
            Log.d(TAG, "Found existing favorites: ${this.count}")
            this.forEach {
                val data = getFavorites(DataMapItem.fromDataItem(this.first()).dataMap)
                Log.d(TAG, "Favorites: $data")
                favoriteEntityIds.clear()
                favoriteEntityIds.addAll(
                    data.removeSurrounding("[", "]").split(", ").map { it }
                )
            }
        }
    }

    fun getFavorites(map: DataMap): String {
        map.apply {
            return getString("favorites", "")
        }
    }

    fun requestFavorites(activity: Activity) {
        Log.d(TAG, "Requesting favorites")

        val capabilityInfo = Tasks.await(
            Wearable.getCapabilityClient(activity)
                .getCapability(CAPABILITY_WEAR_FAVORITES, CapabilityClient.FILTER_REACHABLE)
        )

        capabilityInfo.nodes.forEach { node ->
            Log.d(TAG, "Requesting favorite data")
            Wearable.getMessageClient(activity).sendMessage(
                node.id,
                "/send_home_favorites",
                ByteArray(0)
            ).apply {
                addOnSuccessListener { Log.d(TAG, "Request to favorites sent successfully") }
                addOnFailureListener { Log.d(TAG, "Failed to get favorites") }
            }
        }
    }
}
