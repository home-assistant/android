package io.homeassistant.companion.android.settings.wear

import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.burnoutcrew.reorderable.ItemPosition
import javax.inject.Inject
import io.homeassistant.companion.android.common.R as commonR

@HiltViewModel
class SettingsWearViewModel @Inject constructor(
    private val integrationUseCase: IntegrationRepository,
    application: Application
) :
    AndroidViewModel(application),
    DataClient.OnDataChangedListener {

    companion object {
        private const val TAG = "SettingsWearViewModel"
        private const val CAPABILITY_WEAR_SENDS_CONFIG = "sends_config"

        private const val KEY_UPDATE_TIME = "UpdateTime"
        private const val KEY_IS_AUTHENTICATED = "isAuthenticated"
        private const val KEY_SUPPORTED_DOMAINS = "supportedDomains"
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_TEMPLATE_TILE = "templateTile"
        private const val KEY_TEMPLATE_TILE_REFRESH_INTERVAL = "templateTileRefreshInterval"
    }

    private val objectMapper = jacksonObjectMapper()

    private val _hasData = MutableStateFlow(false)
    val hasData = _hasData.asStateFlow()
    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated = _isAuthenticated.asStateFlow()
    var entities = mutableStateMapOf<String, Entity<*>>()
        private set
    var supportedDomains = mutableStateListOf<String>()
        private set
    var favoriteEntityIds = mutableStateListOf<String>()
        private set
    var templateTileContent = mutableStateOf("")
        private set
    var templateTileContentRendered = mutableStateOf("")
        private set
    var templateTileRefreshInterval = mutableStateOf(0)
        private set

    init {
        Wearable.getDataClient(application).addListener(this)
        Wearable.getCapabilityClient(application)
            .getCapability(CAPABILITY_WEAR_SENDS_CONFIG, CapabilityClient.FILTER_REACHABLE)
            .addOnSuccessListener {
                it.nodes.forEach { node ->
                    Log.d(TAG, "Requesting config from node ${node.id}")
                    Wearable.getMessageClient(application).sendMessage(
                        node.id,
                        "/requestConfig",
                        ByteArray(0)
                    ).apply {
                        addOnSuccessListener {
                            Log.d(TAG, "Request for config sent successfully")
                        }
                        addOnFailureListener { e ->
                            Log.e(TAG, "Failed to request config", e)
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to send config request to wear", e)
                Toast.makeText(
                    application,
                    application.getString(commonR.string.failed_wear_config_request),
                    Toast.LENGTH_LONG
                ).show()
            }
        viewModelScope.launch {
            integrationUseCase.getEntities()?.forEach {
                entities[it.entityId] = it
            }
        }
    }

    override fun onCleared() {
        Wearable.getDataClient(getApplication<HomeAssistantApplication>()).removeListener(this)
    }

    fun setTemplateContent(template: String) {
        templateTileContent.value = template
        if (template.isNotEmpty()) {
            viewModelScope.launch {
                try {
                    templateTileContentRendered.value =
                        integrationUseCase.renderTemplate(template, mapOf()).toString()
                } catch (e: Exception) {
                    Log.e(TAG, "Exception while rendering template", e)
                    // JsonMappingException suggests that template is not a String (= error)
                    templateTileContentRendered.value = getApplication<Application>().getString(
                        if (e.cause is JsonMappingException) commonR.string.template_error
                        else commonR.string.template_render_error
                    )
                }
            }
        } else {
            templateTileContentRendered.value = ""
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
        favoriteEntityIds.apply {
            add(
                favoriteEntityIds.indexOfFirst { it == toItem.key },
                removeAt(favoriteEntityIds.indexOfFirst { it == fromItem.key })
            )
        }
    }

    fun canDragOver(position: ItemPosition) = favoriteEntityIds.any { it == position.key }

    fun sendHomeFavorites(favoritesList: List<String>) = viewModelScope.launch {
        val application = getApplication<HomeAssistantApplication>()
        val putDataRequest = PutDataMapRequest.create("/updateFavorites").run {
            dataMap.putLong(KEY_UPDATE_TIME, System.nanoTime())
            dataMap.putString(KEY_FAVORITES, objectMapper.writeValueAsString(favoritesList))
            setUrgent()
            asPutDataRequest()
        }

        Wearable.getDataClient(application).putDataItem(putDataRequest).apply {
            addOnSuccessListener { Log.d(TAG, "Successfully sent favorites to wear") }
            addOnFailureListener { e ->
                Log.e(TAG, "Failed to send favorites to wear", e)
                Toast.makeText(
                    application,
                    application.getString(commonR.string.failure_send_favorites_wear),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun sendAuthToWear(
        url: String,
        authCode: String,
        deviceName: String,
        deviceTrackingEnabled: Boolean,
        notificationsEnabled: Boolean
    ) {
        val putDataRequest = PutDataMapRequest.create("/authenticate").run {
            dataMap.putString("URL", url)
            dataMap.putString("AuthCode", authCode)
            dataMap.putString("DeviceName", deviceName)
            dataMap.putBoolean("LocationTracking", deviceTrackingEnabled)
            dataMap.putBoolean("Notifications", notificationsEnabled)
            setUrgent()
            asPutDataRequest()
        }

        Wearable.getDataClient(getApplication<HomeAssistantApplication>()).putDataItem(putDataRequest).apply {
            addOnSuccessListener { Log.d(TAG, "Successfully sent auth to wear") }
            addOnFailureListener { e -> Log.e(TAG, "Failed to send auth to wear", e) }
        }
    }

    fun sendTemplateTileInfo() {
        val putDataRequest = PutDataMapRequest.create("/updateTemplateTile").run {
            dataMap.putString(KEY_TEMPLATE_TILE, templateTileContent.value)
            dataMap.putInt(KEY_TEMPLATE_TILE_REFRESH_INTERVAL, templateTileRefreshInterval.value)
            setUrgent()
            asPutDataRequest()
        }

        Wearable.getDataClient(getApplication<HomeAssistantApplication>()).putDataItem(putDataRequest).apply {
            addOnSuccessListener { Log.d(TAG, "Successfully sent tile template to wear") }
            addOnFailureListener { e -> Log.e(TAG, "Failed to send tile template to wear", e) }
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        Log.d(TAG, "onDataChanged ${dataEvents.count}")
        dataEvents.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED) {
                event.dataItem.also { item ->
                    when (item.uri.path) {
                        "/config" -> {
                            onLoadConfigFromWear(DataMapItem.fromDataItem(item).dataMap)
                        }
                    }
                }
            }
        }
        dataEvents.release()
    }

    private fun onLoadConfigFromWear(data: DataMap) {
        _isAuthenticated.value = data.getBoolean(KEY_IS_AUTHENTICATED, false)
        val supportedDomainsList: List<String> =
            objectMapper.readValue(data.getString(KEY_SUPPORTED_DOMAINS, "[\"input_boolean\", \"light\", \"lock\", \"switch\", \"script\", \"scene\"]"))
        supportedDomains.clear()
        supportedDomains.addAll(supportedDomainsList)
        val favoriteEntityIdList: List<String> =
            objectMapper.readValue(data.getString(KEY_FAVORITES, "[]"))
        favoriteEntityIds.clear()
        favoriteEntityIdList.forEach { entityId ->
            favoriteEntityIds.add(entityId)
        }
        setTemplateContent(data.getString(KEY_TEMPLATE_TILE, ""))
        templateTileRefreshInterval.value = data.getInt(KEY_TEMPLATE_TILE_REFRESH_INTERVAL, 0)
        _hasData.value = true
    }
}
