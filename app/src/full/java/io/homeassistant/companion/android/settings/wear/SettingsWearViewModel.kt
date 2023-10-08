package io.homeassistant.companion.android.settings.wear

import android.annotation.SuppressLint
import android.app.Application
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.net.toUri
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
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.WearDataMessages
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerConnectionInfo
import io.homeassistant.companion.android.database.server.ServerSessionInfo
import io.homeassistant.companion.android.database.server.ServerType
import io.homeassistant.companion.android.database.server.ServerUserInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.burnoutcrew.reorderable.ItemPosition
import java.util.UUID
import javax.inject.Inject
import io.homeassistant.companion.android.common.R as commonR

@HiltViewModel
@SuppressLint("VisibleForTests") // https://issuetracker.google.com/issues/239451111
class SettingsWearViewModel @Inject constructor(
    private val serverManager: ServerManager,
    application: Application
) :
    AndroidViewModel(application),
    DataClient.OnDataChangedListener {

    companion object {
        private const val TAG = "SettingsWearViewModel"
        private const val CAPABILITY_WEAR_SENDS_CONFIG = "sends_config"
    }

    private val objectMapper = jacksonObjectMapper()

    private val _hasData = MutableStateFlow(false)
    val hasData = _hasData.asStateFlow()
    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated = _isAuthenticated.asStateFlow()
    private var authenticateId: String? = null
    private var serverId = 0
    private var remoteServerId = 0

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

    private val _resultSnackbar = MutableSharedFlow<String>()
    val resultSnackbar = _resultSnackbar.asSharedFlow()

    init {
        try {
            Wearable.getDataClient(application).addListener(this)
        } catch (e: Exception) {
            Log.e(TAG, "Unable to get wearable data client", e)
        }
        viewModelScope.launch {
            try {
                val capabilityInfo = Wearable.getCapabilityClient(application)
                    .getCapability(CAPABILITY_WEAR_SENDS_CONFIG, CapabilityClient.FILTER_REACHABLE)
                    .await()
                capabilityInfo.nodes.forEach { node ->
                    Log.d(TAG, "Requesting config from node ${node.id}")
                    launch {
                        try {
                            Wearable.getMessageClient(application).sendMessage(
                                node.id,
                                "/requestConfig",
                                ByteArray(0)
                            ).await()
                            Log.d(TAG, "Request for config sent successfully")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to request config", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send config request to wear", e)
                _resultSnackbar.emit(application.getString(commonR.string.failed_watch_connection))
            }
        }
    }

    override fun onCleared() {
        try {
            Wearable.getDataClient(getApplication<HomeAssistantApplication>()).removeListener(this)
        } catch (e: Exception) {
            Log.e(TAG, "Unable to remove listener from wearable data client", e)
        }

        if (serverId != 0) {
            CoroutineScope(Dispatchers.Main + Job()).launch {
                serverManager.removeServer(serverId)
            }
        }
    }

    private suspend fun loadEntities() {
        if (serverId != 0) {
            try {
                serverManager.integrationRepository(serverId).getEntities()?.forEach {
                    entities[it.entityId] = it
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load entities for Wear server", e)
                entities.clear()
            }
        }
    }

    fun setTemplateContent(template: String) {
        templateTileContent.value = template
        if (template.isNotEmpty() && serverId != 0) {
            viewModelScope.launch {
                try {
                    templateTileContentRendered.value =
                        serverManager.integrationRepository(serverId).renderTemplate(template, mapOf()).toString()
                } catch (e: Exception) {
                    Log.e(TAG, "Exception while rendering template", e)
                    // JsonMappingException suggests that template is not a String (= error)
                    templateTileContentRendered.value = getApplication<Application>().getString(
                        if (e.cause is JsonMappingException) {
                            commonR.string.template_error
                        } else {
                            commonR.string.template_render_error
                        }
                    )
                }
            }
        } else {
            templateTileContentRendered.value = ""
        }
    }

    fun onEntitySelected(checked: Boolean, entityId: String) {
        if (checked) {
            favoriteEntityIds.add(entityId)
        } else {
            favoriteEntityIds.remove(entityId)
        }
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
            dataMap.putLong(WearDataMessages.KEY_UPDATE_TIME, System.nanoTime())
            dataMap.putString(WearDataMessages.CONFIG_FAVORITES, objectMapper.writeValueAsString(favoritesList))
            setUrgent()
            asPutDataRequest()
        }

        try {
            Wearable.getDataClient(application).putDataItem(putDataRequest).await()
            Log.d(TAG, "Successfully sent favorites to wear")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send favorites to wear", e)
            _resultSnackbar.emit(application.getString(commonR.string.failure_send_favorites_wear))
        }
    }

    private fun readUriData(uri: String): ByteArray {
        if (uri.isEmpty()) return ByteArray(0)
        return getApplication<HomeAssistantApplication>().contentResolver.openInputStream(uri.toUri())!!.buffered().use {
            it.readBytes()
        }
    }

    fun sendAuthToWear(
        url: String,
        authCode: String,
        deviceName: String,
        deviceTrackingEnabled: Boolean,
        notificationsEnabled: Boolean,
        tlsClientCertificateUri: String,
        tlsClientCertificatePassword: String
    ) {
        _hasData.value = false // Show loading indicator
        val putDataRequest = PutDataMapRequest.create("/authenticate").run {
            authenticateId = UUID.randomUUID().toString()
            dataMap.putString("AuthId", authenticateId!!)
            dataMap.putString("URL", url)
            dataMap.putString("AuthCode", authCode)
            dataMap.putString("DeviceName", deviceName)
            dataMap.putBoolean("LocationTracking", deviceTrackingEnabled)
            dataMap.putBoolean("Notifications", notificationsEnabled)
            dataMap.putByteArray("TLSClientCertificateData", readUriData(tlsClientCertificateUri))
            dataMap.putString("TLSClientCertificatePassword", tlsClientCertificatePassword)
            setUrgent()
            asPutDataRequest()
        }

        val app = getApplication<HomeAssistantApplication>()
        try {
            Wearable.getDataClient(app).putDataItem(putDataRequest).apply {
                addOnSuccessListener { Log.d(TAG, "Successfully sent auth to wear") }
                addOnFailureListener { e ->
                    Log.e(TAG, "Failed to send auth to wear", e)
                    _hasData.value = true
                    watchConnectionError(app)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unable to send auth to wear", e)
            watchConnectionError(app)
        }
    }

    fun sendTemplateTileInfo() {
        val putDataRequest = PutDataMapRequest.create("/updateTemplateTile").run {
            dataMap.putString(WearDataMessages.CONFIG_TEMPLATE_TILE, templateTileContent.value)
            dataMap.putInt(WearDataMessages.CONFIG_TEMPLATE_TILE_REFRESH_INTERVAL, templateTileRefreshInterval.value)
            setUrgent()
            asPutDataRequest()
        }

        try {
            Wearable.getDataClient(getApplication<HomeAssistantApplication>())
                .putDataItem(putDataRequest).apply {
                    addOnSuccessListener { Log.d(TAG, "Successfully sent tile template to wear") }
                    addOnFailureListener { e -> Log.e(TAG, "Failed to send tile template to wear", e) }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Unable to send template tile to wear", e)
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
                        WearDataMessages.PATH_LOGIN_RESULT -> {
                            onAuthenticateResult(DataMapItem.fromDataItem(item).dataMap)
                        }
                    }
                }
            }
        }
        dataEvents.release()
    }

    private fun onLoadConfigFromWear(data: DataMap) = viewModelScope.launch {
        val isAuthenticated = data.getBoolean(WearDataMessages.CONFIG_IS_AUTHENTICATED, false)
        _isAuthenticated.value = isAuthenticated
        if (isAuthenticated) {
            updateServer(data)
        }

        val supportedDomainsList: List<String> =
            objectMapper.readValue(data.getString(WearDataMessages.CONFIG_SUPPORTED_DOMAINS, "[\"input_boolean\", \"light\", \"lock\", \"switch\", \"script\", \"scene\"]"))
        supportedDomains.clear()
        supportedDomains.addAll(supportedDomainsList)
        val favoriteEntityIdList: List<String> =
            objectMapper.readValue(data.getString(WearDataMessages.CONFIG_FAVORITES, "[]"))
        favoriteEntityIds.clear()
        favoriteEntityIdList.forEach { entityId ->
            favoriteEntityIds.add(entityId)
        }
        setTemplateContent(data.getString(WearDataMessages.CONFIG_TEMPLATE_TILE, ""))
        templateTileRefreshInterval.value = data.getInt(WearDataMessages.CONFIG_TEMPLATE_TILE_REFRESH_INTERVAL, 0)

        _hasData.value = true
    }

    private suspend fun updateServer(data: DataMap) {
        val wearServerId = data.getInt(WearDataMessages.CONFIG_SERVER_ID, 0)
        if (wearServerId == 0 || wearServerId == remoteServerId) return

        if (remoteServerId != 0) { // First, remove the old server
            serverManager.removeServer(serverId)
            serverId = 0
            remoteServerId = 0
        }

        val wearExternalUrl = data.getString(WearDataMessages.CONFIG_SERVER_EXTERNAL_URL) ?: return
        val wearWebhookId = data.getString(WearDataMessages.CONFIG_SERVER_WEBHOOK_ID) ?: return
        val wearCloudUrl = data.getString(WearDataMessages.CONFIG_SERVER_CLOUD_URL, "").ifBlank { null }
        val wearCloudhookUrl = data.getString(WearDataMessages.CONFIG_SERVER_CLOUDHOOK_URL, "").ifBlank { null }
        val wearUseCloud = data.getBoolean(WearDataMessages.CONFIG_SERVER_USE_CLOUD, false)
        val wearRefreshToken = data.getString(WearDataMessages.CONFIG_SERVER_REFRESH_TOKEN, "")

        try {
            serverId = serverManager.addServer(
                Server(
                    _name = "",
                    type = ServerType.TEMPORARY,
                    connection = ServerConnectionInfo(
                        externalUrl = wearExternalUrl,
                        cloudUrl = wearCloudUrl,
                        webhookId = wearWebhookId,
                        cloudhookUrl = wearCloudhookUrl,
                        useCloud = wearUseCloud
                    ),
                    session = ServerSessionInfo(),
                    user = ServerUserInfo()
                )
            )
            serverManager.authenticationRepository(serverId).registerRefreshToken(wearRefreshToken)
            remoteServerId = wearServerId

            viewModelScope.launch { loadEntities() }
        } catch (e: Exception) {
            Log.e(TAG, "Unable to add Wear server from data", e)
            if (serverId != 0) {
                serverManager.removeServer(serverId)
                serverId = 0
                remoteServerId = 0
            }
        }
    }
    private fun onAuthenticateResult(data: DataMap) = viewModelScope.launch {
        val id = data.getString(WearDataMessages.KEY_ID, "")
        if (id != authenticateId) return@launch

        val success = data.getBoolean(WearDataMessages.KEY_SUCCESS, false)
        val application = getApplication<HomeAssistantApplication>()
        if (success) {
            _resultSnackbar.emit(application.getString(commonR.string.logged_in))
        } else {
            val e = data.getString(WearDataMessages.LOGIN_RESULT_EXCEPTION, "")
            Log.e(TAG, "Watch was unable to register: $e")
            _resultSnackbar.emit(application.getString(commonR.string.failed_watch_registration))
        }

        authenticateId = null
    }

    private fun watchConnectionError(app: HomeAssistantApplication) {
        viewModelScope.launch {
            _resultSnackbar.emit(app.getString(commonR.string.failed_watch_connection))
        }
    }
}
