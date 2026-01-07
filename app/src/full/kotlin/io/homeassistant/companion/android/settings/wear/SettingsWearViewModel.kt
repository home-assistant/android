package io.homeassistant.companion.android.settings.wear

import android.app.Application
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.NodeClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.HomeAssistantApplication
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationException
import io.homeassistant.companion.android.common.data.prefs.impl.entities.TemplateTileConfig
import io.homeassistant.companion.android.common.util.WearDataMessages
import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.SerializationException
import timber.log.Timber

@HiltViewModel
class SettingsWearViewModel @Inject constructor(
    private val settingsWearRepository: SettingsWearRepository,
    application: Application,
) : AndroidViewModel(application),
    DataClient.OnDataChangedListener {

    data class SettingsWearOnboardingViewUiState(
        @StringRes val infoTextResourceId: Int = commonR.string.message_checking,
        val shouldShowRemoteInstallButton: Boolean = false,
        val installedOnDevices: Boolean = false,
    )

    companion object {
        private const val CAPABILITY_WEAR_SENDS_CONFIG = "sends_config"

        // Name of capability listed in Wear app's wear.xml.
        // IMPORTANT NOTE: This should be named differently than your Phone app's capability.
        const val CAPABILITY_WEAR_APP = "verify_wear_app"
    }

    private val _hasData = MutableStateFlow(false)
    val hasData = _hasData.asStateFlow()
    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated = _isAuthenticated.asStateFlow()

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    val wearNodesWithApp: MutableStateFlow<Set<Node>> = MutableStateFlow(setOf<Node>())

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    val allConnectedNodes: MutableStateFlow<List<Node>> = MutableStateFlow(listOf<Node>())

    val settingsWearOnboardingViewUiState =
        combine(wearNodesWithApp, allConnectedNodes) { nodesWithApp, connectedNodes ->
            var infoTextResourceId = commonR.string.message_checking
            var shouldDisplayRemoteAppInstallButton = false
            var installedOnDevices = false
            when {
                connectedNodes.isEmpty() -> {
                    Timber.d("No devices")
                    infoTextResourceId = commonR.string.message_no_connected_nodes
                    shouldDisplayRemoteAppInstallButton = true
                }

                nodesWithApp.isEmpty() -> {
                    Timber.d("Missing on all devices")
                    infoTextResourceId = commonR.string.message_missing_all
                    shouldDisplayRemoteAppInstallButton = true
                }

                nodesWithApp.size < connectedNodes.size -> {
                    Timber.d("Installed on some devices")
                    installedOnDevices = true
                }

                else -> {
                    Timber.d("Installed on all devices")
                    installedOnDevices = true
                }
            }
            SettingsWearOnboardingViewUiState(
                infoTextResourceId = infoTextResourceId,
                shouldShowRemoteInstallButton = shouldDisplayRemoteAppInstallButton,
                installedOnDevices = installedOnDevices,
            )
        }

    private var authenticateId: String? = null
    private var wearServer: WearServer? = null

    var entities = mutableStateMapOf<String, Entity>()
        private set
    var supportedDomains = mutableStateListOf<String>()
        private set
    var favoriteEntityIds = mutableStateListOf<String>()
        private set
    var templateTiles = mutableStateMapOf<Int, TemplateTileConfig>()
        private set
    var templateTilesRenderedTemplates = mutableStateMapOf<Int, String>()
        private set

    private val _resultSnackbar = MutableSharedFlow<String>()
    val resultSnackbar = _resultSnackbar.asSharedFlow()

    init {
        try {
            Wearable.getDataClient(application).addListener(this)
        } catch (e: Exception) {
            Timber.e(e, "Unable to get wearable data client")
        }
        viewModelScope.launch {
            try {
                val capabilityInfo = Wearable.getCapabilityClient(application)
                    .getCapability(CAPABILITY_WEAR_SENDS_CONFIG, CapabilityClient.FILTER_REACHABLE)
                    .await()
                capabilityInfo.nodes.forEach { node ->
                    Timber.d("Requesting config from node ${node.id}")
                    launch {
                        try {
                            Wearable.getMessageClient(application).sendMessage(
                                node.id,
                                "/requestConfig",
                                ByteArray(0),
                            ).await()
                            Timber.d("Request for config sent successfully")
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to request config")
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to send config request to wear")
                _resultSnackbar.emit(application.getString(commonR.string.failed_watch_connection))
            }
        }
    }

    override fun onCleared() {
        try {
            Wearable.getDataClient(getApplication<HomeAssistantApplication>()).removeListener(this)
        } catch (e: Exception) {
            Timber.e(e, "Unable to remove listener from wearable data client")
        }
    }

    private suspend fun WearServer.loadEntities() {
        settingsWearRepository.getEntities(this).forEach {
            entities[it.entityId] = it
        }
    }

    fun setTemplateTileContent(tileId: Int, updatedTemplateTileContent: String) {
        val templateTileConfig = templateTiles[tileId]
        templateTileConfig?.let {
            templateTiles[tileId] = it.copy(template = updatedTemplateTileContent)
            renderTemplate(tileId, updatedTemplateTileContent)
        }
    }

    fun setTemplateTileRefreshInterval(tileId: Int, refreshInterval: Int) {
        val templateTileConfig = templateTiles[tileId]
        templateTileConfig?.let {
            templateTiles[tileId] = it.copy(refreshInterval = refreshInterval)
        }
    }

    private fun setTemplateTiles(newTemplateTiles: Map<Int, TemplateTileConfig>) {
        templateTiles.clear()
        templateTilesRenderedTemplates.clear()

        templateTiles.putAll(newTemplateTiles)
        templateTiles.forEach {
            renderTemplate(it.key, it.value.template)
        }
    }

    private fun renderTemplate(tileId: Int, template: String) {
        wearServer?.takeIf { template.isNotEmpty() }?.let {
            viewModelScope.launch {
                templateTilesRenderedTemplates[tileId] = try {
                    settingsWearRepository.renderTemplate(it, template).toString()
                } catch (e: IntegrationException) {
                    Timber.e(e, "Exception while rendering template for tile ID $tileId")
                    // SerializationException suggests that template is not a String (= error)
                    getApplication<Application>().getString(
                        if (e.cause is SerializationException) {
                            commonR.string.template_error
                        } else {
                            commonR.string.template_render_error
                        },
                    )
                }
            }
        } ?: run {
            templateTilesRenderedTemplates[tileId] = ""
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

    fun onMove(fromItem: LazyListItemInfo, toItem: LazyListItemInfo) {
        favoriteEntityIds.apply {
            add(
                favoriteEntityIds.indexOfFirst { it == toItem.key },
                removeAt(favoriteEntityIds.indexOfFirst { it == fromItem.key }),
            )
        }
    }

    fun sendHomeFavorites(favoritesList: List<String>) = viewModelScope.launch {
        val application = getApplication<HomeAssistantApplication>()
        val putDataRequest = PutDataMapRequest.create("/updateFavorites").run {
            dataMap.putLong(WearDataMessages.KEY_UPDATE_TIME, System.nanoTime())
            dataMap.putString(WearDataMessages.CONFIG_FAVORITES, kotlinJsonMapper.encodeToString(favoritesList))
            setUrgent()
            asPutDataRequest()
        }

        try {
            Wearable.getDataClient(application).putDataItem(putDataRequest).await()
            Timber.d("Successfully sent favorites to wear")
        } catch (e: Exception) {
            Timber.e(e, "Failed to send favorites to wear")
            _resultSnackbar.emit(application.getString(commonR.string.failure_send_favorites_wear))
        }
    }

    private fun readUriData(uri: String?): ByteArray {
        if (uri.isNullOrEmpty()) return ByteArray(0)
        return getApplication<HomeAssistantApplication>().contentResolver.openInputStream(
            uri.toUri(),
        )!!.buffered().use {
            it.readBytes()
        }
    }

    fun sendAuthToWear(
        url: String,
        authCode: String,
        deviceName: String,
        deviceTrackingEnabled: Boolean,
        notificationsEnabled: Boolean,
        tlsClientCertificateUri: String?,
        tlsClientCertificatePassword: String?,
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
            dataMap.putString("TLSClientCertificatePassword", tlsClientCertificatePassword.orEmpty())
            setUrgent()
            asPutDataRequest()
        }

        val app = getApplication<HomeAssistantApplication>()
        try {
            Wearable.getDataClient(app).putDataItem(putDataRequest).apply {
                addOnSuccessListener { Timber.d("Successfully sent auth to wear") }
                addOnFailureListener { e ->
                    Timber.e(e, "Failed to send auth to wear")
                    _hasData.value = true
                    watchConnectionError(app)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Unable to send auth to wear")
            watchConnectionError(app)
        }
    }

    fun sendTemplateTileInfo() {
        val putDataRequest = PutDataMapRequest.create("/updateTemplateTiles").run {
            dataMap.putString(
                WearDataMessages.CONFIG_TEMPLATE_TILES,
                kotlinJsonMapper.encodeToString(templateTiles.toMap()),
            )
            setUrgent()
            asPutDataRequest()
        }

        try {
            Wearable.getDataClient(getApplication<HomeAssistantApplication>())
                .putDataItem(putDataRequest).apply {
                    addOnSuccessListener { Timber.d("Successfully sent tile template to wear") }
                    addOnFailureListener { e -> Timber.e(e, "Failed to send tile template to wear") }
                }
        } catch (e: Exception) {
            Timber.e(e, "Unable to send template tile to wear")
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        Timber.d("onDataChanged ${dataEvents.count}")
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
            kotlinJsonMapper.decodeFromString(
                data.getString(
                    WearDataMessages.CONFIG_SUPPORTED_DOMAINS,
                    "[\"input_boolean\", \"light\", \"lock\", \"switch\", \"script\", \"scene\"]",
                ),
            )
        supportedDomains.clear()
        supportedDomains.addAll(supportedDomainsList)
        val favoriteEntityIdList: List<String> =
            kotlinJsonMapper.decodeFromString(data.getString(WearDataMessages.CONFIG_FAVORITES, "[]"))
        favoriteEntityIds.clear()
        favoriteEntityIdList.forEach { entityId ->
            favoriteEntityIds.add(entityId)
        }

        val templateTilesFromWear: Map<Int, TemplateTileConfig> = kotlinJsonMapper.decodeFromString(
            data.getString(
                WearDataMessages.CONFIG_TEMPLATE_TILES,
                "{}",
            ),
        )
        setTemplateTiles(templateTilesFromWear)

        _hasData.value = true
    }

    private suspend fun updateServer(data: DataMap) {
        val wearServerId = data.getInt(WearDataMessages.CONFIG_SERVER_ID, 0)

        if (wearServerId == this.wearServer?.serverId) {
            Timber.i("Server already updated")
            return
        }

        val wearExternalUrl = data.getString(WearDataMessages.CONFIG_SERVER_EXTERNAL_URL) ?: return
        val wearWebhookId = data.getString(WearDataMessages.CONFIG_SERVER_WEBHOOK_ID)?.ifBlank { null } ?: return
        val wearCloudUrl = data.getString(WearDataMessages.CONFIG_SERVER_CLOUD_URL, "").ifBlank { null }
        val wearCloudhookUrl = data.getString(WearDataMessages.CONFIG_SERVER_CLOUDHOOK_URL, "").ifBlank { null }
        val wearRefreshToken = data.getString(WearDataMessages.CONFIG_SERVER_REFRESH_TOKEN, "")

        try {
            val wearServer = settingsWearRepository.registerRefreshToken(
                WearServer(
                    serverId = wearServerId,
                    externalUrl = wearExternalUrl,
                    cloudUrl = wearCloudUrl,
                    webhookId = wearWebhookId,
                    cloudhookUrl = wearCloudhookUrl,
                    accessToken = null,
                ),
                wearRefreshToken,
            )
            this.wearServer = wearServer

            viewModelScope.launch { wearServer.loadEntities() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Unable to add Wear server from data")
            wearServer = null
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
            Timber.e(e, "Watch was unable to register.")
            _resultSnackbar.emit(application.getString(commonR.string.failed_watch_registration))
        }

        authenticateId = null
    }

    private fun watchConnectionError(app: HomeAssistantApplication) {
        viewModelScope.launch {
            _resultSnackbar.emit(app.getString(commonR.string.failed_watch_connection))
        }
    }

    fun updateWearNodesWithApp(nodes: Set<Node>) {
        wearNodesWithApp.value = nodes
    }

    suspend fun findWearDevicesWithApp(capabilityClient: CapabilityClient?) {
        try {
            val capabilityInfo = capabilityClient
                ?.getCapability(CAPABILITY_WEAR_APP, CapabilityClient.FILTER_ALL)
                ?.await()

            capabilityInfo?.nodes?.let { nodes ->
                wearNodesWithApp.value = nodes
            }
            Timber.d("Capable Nodes: ${wearNodesWithApp.value}")
        } catch (cancellationException: CancellationException) {
            // Request was cancelled normally
            throw cancellationException
        } catch (throwable: Throwable) {
            Timber.d(throwable, "Capability request failed to return any results.")
        }
    }

    suspend fun findAllWearDevices(nodeClient: NodeClient?) {
        try {
            val connectedNodes = nodeClient?.connectedNodes?.await()

            if (connectedNodes != null) {
                allConnectedNodes.value = connectedNodes
            }
        } catch (cancellationException: CancellationException) {
            // Request was cancelled normally
            throw cancellationException
        } catch (throwable: Throwable) {
            Timber.d(throwable, "Node request failed to return any results.")
        }
    }

    fun getNodesWithApp(): Set<Node> {
        return wearNodesWithApp.value
    }

    fun getNodesWithoutApp(): List<Node> {
        // Determine the list of nodes (wear devices) that don't have the app installed yet.
        return allConnectedNodes.value - wearNodesWithApp.value
    }
}
