package io.homeassistant.companion.android.home

import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.HomeAssistantApplication
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.CAMERA_DOMAIN
import io.homeassistant.companion.android.common.data.integration.domain
import io.homeassistant.companion.android.common.data.prefs.impl.entities.TemplateTileConfig
import io.homeassistant.companion.android.common.data.websocket.WebSocketState
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AreaRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.DeviceRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryResponse
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.data.SimplifiedEntity
import io.homeassistant.companion.android.database.sensor.SensorDao
import io.homeassistant.companion.android.database.wear.CameraTile
import io.homeassistant.companion.android.database.wear.CameraTileDao
import io.homeassistant.companion.android.database.wear.FavoriteCaches
import io.homeassistant.companion.android.database.wear.FavoriteCachesDao
import io.homeassistant.companion.android.database.wear.FavoritesDao
import io.homeassistant.companion.android.database.wear.ThermostatTile
import io.homeassistant.companion.android.database.wear.ThermostatTileDao
import io.homeassistant.companion.android.database.wear.getAll
import io.homeassistant.companion.android.database.wear.getAllFlow
import io.homeassistant.companion.android.sensors.SensorReceiver
import io.homeassistant.companion.android.util.RegistriesDataHandler
import io.homeassistant.companion.android.util.throttleLatest
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@HiltViewModel
class MainViewModel @Inject constructor(
    private val favoritesDao: FavoritesDao,
    private val favoriteCachesDao: FavoriteCachesDao,
    private val sensorsDao: SensorDao,
    private val cameraTileDao: CameraTileDao,
    private val thermostatTileDao: ThermostatTileDao,
    application: Application,
) : AndroidViewModel(application) {

    enum class LoadingState {
        LOADING,
        READY,
        ERROR,
    }

    private val app = application

    private lateinit var homePresenter: HomePresenter
    private var areaRegistry: List<AreaRegistryResponse>? = null
    private var deviceRegistry: List<DeviceRegistryResponse>? = null
    private var entityRegistry: List<EntityRegistryResponse>? = null

    // TODO: This is bad, do this instead: https://stackoverflow.com/questions/46283981/android-viewmodel-additional-arguments
    fun init(homePresenter: HomePresenter) {
        this.homePresenter = homePresenter
        loadSettings()
        loadEntities()
    }

    // entities
    var entities = mutableStateMapOf<String, Entity>()
        private set

    private val _supportedEntities = MutableStateFlow(emptyList<String>())
    val supportedEntities = _supportedEntities.asStateFlow()

    /**
     * IDs of favorites in the Favorites database.
     */
    val favoriteEntityIds = favoritesDao.getAllFlow().collectAsState()
    var favoriteCaches = mutableStateListOf<FavoriteCaches>()
        private set

    val shortcutEntitiesMap = mutableStateMapOf<Int?, SnapshotStateList<SimplifiedEntity>>()

    val cameraTiles = cameraTileDao.getAllFlow().collectAsState()
    var cameraEntitiesMap = mutableStateMapOf<String, SnapshotStateList<Entity>>()
        private set

    val thermostatTiles = thermostatTileDao.getAllFlow().collectAsState()
    var climateEntitiesMap = mutableStateMapOf<String, SnapshotStateList<Entity>>()
        private set

    var areas = mutableListOf<AreaRegistryResponse>()
        private set

    var entitiesByArea = mutableStateMapOf<String, SnapshotStateList<Entity>>()
        private set
    var entitiesByDomain = mutableStateMapOf<String, SnapshotStateList<Entity>>()
        private set
    var entitiesByAreaOrder = mutableStateListOf<String>()
        private set
    var entitiesByDomainOrder = mutableStateListOf<String>()
        private set

    // Content of EntityListView
    var entityLists = mutableStateMapOf<String, List<Entity>>()
    var entityListsOrder = mutableStateListOf<String>()
    var entityListFilter: (Entity) -> Boolean = { true }

    // settings
    var loadingState = mutableStateOf(LoadingState.LOADING)
        private set
    var isHapticEnabled = mutableStateOf(false)
        private set
    var isToastEnabled = mutableStateOf(false)
        private set
    var isShowShortcutTextEnabled = mutableStateOf(false)
        private set
    var templateTiles = mutableStateMapOf<Int, TemplateTileConfig>()
        private set
    var isFavoritesOnly by mutableStateOf(false)
        private set
    var isAssistantAppAllowed by mutableStateOf(true)
        private set
    var areNotificationsAllowed by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            favoriteCaches.addAll(favoriteCachesDao.getAll())
        }
    }

    fun supportedDomains(): List<String> = HomePresenterImpl.supportedDomains

    fun stringForDomain(domain: String): String? =
        HomePresenterImpl.domainsWithNames[domain]?.let { getApplication<Application>().getString(it) }

    val sensors = sensorsDao.getAllFlow().collectAsState()

    var availableSensors = emptyList<SensorManager.BasicSensor>()

    private fun loadSettings() {
        viewModelScope.launch {
            if (!homePresenter.isConnected()) {
                return@launch
            }
            loadShortcutTileEntities()
            isHapticEnabled.value = homePresenter.getWearHapticFeedback()
            isToastEnabled.value = homePresenter.getWearToastConfirmation()
            isShowShortcutTextEnabled.value = homePresenter.getShowShortcutText()
            templateTiles.clear()
            templateTiles.putAll(homePresenter.getAllTemplateTiles())
            isFavoritesOnly = homePresenter.getWearFavoritesOnly()

            val assistantAppComponent = ComponentName(
                BuildConfig.APPLICATION_ID,
                "io.homeassistant.companion.android.conversation.AssistantActivity",
            )
            isAssistantAppAllowed =
                app.packageManager.getComponentEnabledSetting(assistantAppComponent) !=
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED

            refreshNotificationPermission()
        }
    }

    fun loadShortcutTileEntities() {
        viewModelScope.launch {
            val map = homePresenter.getAllTileShortcuts().mapValues { (_, entities) ->
                entities.toMutableStateList()
            }
            shortcutEntitiesMap.clear()
            shortcutEntitiesMap.putAll(map)
        }
    }

    fun loadTemplateTiles() {
        viewModelScope.launch {
            templateTiles.clear()
            templateTiles.putAll(homePresenter.getAllTemplateTiles())
        }
    }

    fun loadEntities() {
        viewModelScope.launch {
            if (!homePresenter.isConnected()) return@launch
            try {
                // Load initial state
                loadingState.value = LoadingState.LOADING
                updateUI()

                // Finished initial load, update state
                val webSocketState = homePresenter.getWebSocketState()
                if (webSocketState == WebSocketState.CLOSED_AUTH) {
                    homePresenter.onInvalidAuthorization()
                    return@launch
                }
                loadingState.value = if (webSocketState == WebSocketState.ACTIVE) {
                    LoadingState.READY
                } else {
                    LoadingState.ERROR
                }
            } catch (e: Exception) {
                Timber.e(e, "Exception while loading entities")
                loadingState.value = LoadingState.ERROR
            }
        }
    }

    private fun updateEntityStates(entity: Entity) {
        if (supportedDomains().contains(entity.domain)) {
            entities[entity.entityId] = entity
            // add to cache if part of favorites
            if (favoriteEntityIds.value.contains(entity.entityId)) {
                addCachedFavorite(entity.entityId)
            }
        }
    }

    suspend fun updateUI() = withContext(Dispatchers.IO) {
        if (!homePresenter.isConnected()) return@withContext
        val getAreaRegistry = async { homePresenter.getAreaRegistry() }
        val getDeviceRegistry = async { homePresenter.getDeviceRegistry() }
        val getEntityRegistry = async { homePresenter.getEntityRegistry() }
        val getEntities = async { homePresenter.getEntities() }

        if (!isFavoritesOnly) {
            areaRegistry = getAreaRegistry.await()?.also {
                areas.clear()
                areas.addAll(it)
            }
            deviceRegistry = getDeviceRegistry.await()
        }
        entityRegistry = getEntityRegistry.await()

        _supportedEntities.value = getSupportedEntities()

        getEntities.await()?.also {
            entities.clear()
            it.forEach { state -> updateEntityStates(state) }

            // Special lists: camera entities and climate entities
            val cameraEntities = it.filter { entity -> entity.domain == CAMERA_DOMAIN }
            cameraEntitiesMap[CAMERA_DOMAIN] = mutableStateListOf<Entity>().apply { addAll(cameraEntities) }
            val climateEntities = it.filter { entity -> entity.domain == "climate" }
            climateEntitiesMap["climate"] = mutableStateListOf<Entity>().apply { addAll(climateEntities) }
        }
        if (!isFavoritesOnly) {
            updateEntityDomains()
        }
    }

    suspend fun entityUpdates() {
        if (!homePresenter.isConnected()) {
            return
        }
        homePresenter.getEntityUpdates(supportedEntities.value)?.collect {
            updateEntityStates(it)
            if (!isFavoritesOnly) {
                updateEntityDomains()
            }
        }
    }

    suspend fun areaUpdates() {
        if (!homePresenter.isConnected() || isFavoritesOnly) {
            return
        }
        homePresenter.getAreaRegistryUpdates()?.throttleLatest(1000)?.collect {
            areaRegistry = homePresenter.getAreaRegistry()
            areas.clear()
            areaRegistry?.let {
                areas.addAll(it)
            }
            updateEntityDomains()
        }
    }

    suspend fun deviceUpdates() {
        if (!homePresenter.isConnected() || isFavoritesOnly) {
            return
        }
        homePresenter.getDeviceRegistryUpdates()?.throttleLatest(1000)?.collect {
            deviceRegistry = homePresenter.getDeviceRegistry()
            updateEntityDomains()
        }
    }

    suspend fun entityRegistryUpdates() {
        if (!homePresenter.isConnected()) {
            return
        }
        homePresenter.getEntityRegistryUpdates()?.throttleLatest(1000)?.collect {
            entityRegistry = homePresenter.getEntityRegistry()
            _supportedEntities.value = getSupportedEntities()
            updateEntityDomains()
        }
    }

    private fun getSupportedEntities(): List<String> = entityRegistry
        .orEmpty()
        .map { it.entityId }
        .filter { it.split(".")[0] in supportedDomains() }

    private fun updateEntityDomains() {
        val entitiesList = entities.values.toList().sortedBy { it.entityId }
        val areasList = areaRegistry.orEmpty().sortedBy { it.name }
        val domainsList = entitiesList.map { it.domain }.distinct()

        // Create a list with all areas + their entities
        areasList.forEach { area ->
            val entitiesInArea = mutableStateListOf<Entity>()
            entitiesInArea.addAll(
                entitiesList
                    .filter { getAreaForEntity(it.entityId)?.areaId == area.areaId }
                    .sortedBy { (it.attributes["friendly_name"] ?: it.entityId) as String },
            )
            entitiesByArea[area.areaId]?.let {
                it.clear()
                it.addAll(entitiesInArea)
            } ?: run {
                entitiesByArea[area.areaId] = entitiesInArea
            }
        }
        entitiesByAreaOrder.clear()
        entitiesByAreaOrder.addAll(areasList.map { it.areaId })
        // Quick check: are there any areas in the list that no longer exist?
        entitiesByArea.forEach {
            if (!areasList.any { item -> item.areaId == it.key }) {
                entitiesByArea.remove(it.key)
            }
        }

        // Create a list with all discovered domains + their entities
        domainsList.forEach { domain ->
            val entitiesInDomain = mutableStateListOf<Entity>()
            entitiesInDomain.addAll(entitiesList.filter { it.domain == domain })
            entitiesByDomain[domain]?.let {
                it.clear()
                it.addAll(entitiesInDomain)
            } ?: run {
                entitiesByDomain[domain] = entitiesInDomain
            }
        }
        entitiesByDomainOrder.clear()
        entitiesByDomainOrder.addAll(domainsList)
    }

    fun toggleEntity(entityId: String, state: String) {
        viewModelScope.launch {
            homePresenter.onEntityClicked(entityId, state)
        }
    }

    fun setFanSpeed(entityId: String, speed: Float) {
        viewModelScope.launch {
            homePresenter.onFanSpeedChanged(entityId, speed)
        }
    }

    fun setBrightness(entityId: String, brightness: Float) {
        viewModelScope.launch {
            homePresenter.onBrightnessChanged(entityId, brightness)
        }
    }

    fun setColorTemp(entityId: String, colorTemp: Float, isKelvin: Boolean) {
        viewModelScope.launch {
            homePresenter.onColorTempChanged(entityId, colorTemp, isKelvin)
        }
    }

    fun enableDisableSensor(sensorManager: SensorManager, sensorId: String, isEnabled: Boolean) {
        viewModelScope.launch {
            val basicSensor = sensorManager.getAvailableSensors(getApplication())
                .first { basicSensor -> basicSensor.id == sensorId }
            updateSensorEntity(sensorsDao, basicSensor, isEnabled)

            if (isEnabled) {
                try {
                    sensorManager.requestSensorUpdate(getApplication())
                } catch (e: Exception) {
                    Timber.e(e, "Exception while requesting update for sensor $sensorId")
                }
            }
        }
    }

    private suspend fun updateSensorEntity(
        sensorDao: SensorDao,
        basicSensor: SensorManager.BasicSensor,
        isEnabled: Boolean,
    ) {
        homePresenter.getServerId()?.let { serverId ->
            sensorDao.setSensorsEnabled(listOf(basicSensor.id), serverId, isEnabled)
            SensorReceiver.updateAllSensors(getApplication())
        }
    }

    fun updateAllSensors(sensorManager: SensorManager) {
        availableSensors = emptyList()
        viewModelScope.launch {
            val context = getApplication<HomeAssistantApplication>().applicationContext
            availableSensors = sensorManager
                .getAvailableSensors(context)
                .sortedBy { context.getString(it.name) }.distinct()
        }
    }

    fun initAllSensors() {
        viewModelScope.launch {
            for (manager in SensorReceiver.MANAGERS) {
                for (basicSensor in manager.getAvailableSensors(getApplication())) {
                    manager.isEnabled(getApplication(), basicSensor)
                }
            }
        }
    }

    fun getAreaForEntity(entityId: String): AreaRegistryResponse? =
        RegistriesDataHandler.getAreaForEntity(entityId, areaRegistry, deviceRegistry, entityRegistry)

    fun getCategoryForEntity(entityId: String): String? =
        RegistriesDataHandler.getCategoryForEntity(entityId, entityRegistry)

    fun getHiddenByForEntity(entityId: String): String? =
        RegistriesDataHandler.getHiddenByForEntity(entityId, entityRegistry)

    /**
     * Clears all favorites in the database.
     */
    fun clearFavorites() {
        viewModelScope.launch {
            favoritesDao.deleteAll()
            setWearFavoritesOnly(false)
        }
    }

    fun setCameraTileEntity(tileId: Int, entityId: String) = viewModelScope.launch {
        val current = cameraTileDao.get(tileId)
        val updated = current?.copy(entityId = entityId) ?: CameraTile(id = tileId, entityId = entityId)
        cameraTileDao.add(updated)
    }

    fun setCameraTileRefreshInterval(tileId: Int, interval: Long) = viewModelScope.launch {
        val current = cameraTileDao.get(tileId)
        val updated = current?.copy(refreshInterval = interval) ?: CameraTile(id = tileId, refreshInterval = interval)
        cameraTileDao.add(updated)
    }

    fun setThermostatTileEntity(tileId: Int, entityId: String) = viewModelScope.launch {
        val current = thermostatTileDao.get(tileId)
        val updated = current?.copy(entityId = entityId) ?: ThermostatTile(id = tileId, entityId = entityId)
        thermostatTileDao.add(updated)
    }

    fun setThermostatTileRefreshInterval(tileId: Int, interval: Long) = viewModelScope.launch {
        val current = thermostatTileDao.get(tileId)
        val updated =
            current?.copy(refreshInterval = interval) ?: ThermostatTile(id = tileId, refreshInterval = interval)
        thermostatTileDao.add(updated)
    }

    fun setThermostatTileShowName(tileId: Int, showName: Boolean) = viewModelScope.launch {
        val current = thermostatTileDao.get(tileId)
        val updated = current?.copy(showEntityName = showName) ?: ThermostatTile(id = tileId, showEntityName = showName)
        thermostatTileDao.add(updated)
    }

    fun setTileShortcut(tileId: Int?, index: Int, entity: SimplifiedEntity) {
        viewModelScope.launch {
            val shortcutEntities = shortcutEntitiesMap[tileId]!!
            if (index < shortcutEntities.size) {
                shortcutEntities[index] = entity
            } else {
                shortcutEntities.add(entity)
            }
            homePresenter.setTileShortcuts(tileId, entities = shortcutEntities)
        }
    }

    fun clearTileShortcut(tileId: Int?, index: Int) {
        viewModelScope.launch {
            val shortcutEntities = shortcutEntitiesMap[tileId]!!
            if (index < shortcutEntities.size) {
                shortcutEntities.removeAt(index)
                homePresenter.setTileShortcuts(tileId, entities = shortcutEntities)
            }
        }
    }

    fun setHapticEnabled(enabled: Boolean) {
        viewModelScope.launch {
            homePresenter.setWearHapticFeedback(enabled)
            isHapticEnabled.value = enabled
        }
    }

    fun setToastEnabled(enabled: Boolean) {
        viewModelScope.launch {
            homePresenter.setWearToastConfirmation(enabled)
            isToastEnabled.value = enabled
        }
    }

    fun setShowShortcutTextEnabled(enabled: Boolean) {
        viewModelScope.launch {
            homePresenter.setShowShortcutTextEnabled(enabled)
            isShowShortcutTextEnabled.value = enabled
        }
    }

    fun setWearFavoritesOnly(enabled: Boolean) {
        viewModelScope.launch {
            homePresenter.setWearFavoritesOnly(enabled)
            isFavoritesOnly = enabled
        }
    }

    fun setTemplateTileRefreshInterval(tileId: Int, interval: Int) {
        viewModelScope.launch {
            homePresenter.setTemplateTileRefreshInterval(tileId, interval)
            templateTiles[tileId]?.let {
                templateTiles[tileId] = it.copy(refreshInterval = interval)
            }
        }
    }

    fun addFavoriteEntity(entityId: String) {
        viewModelScope.launch {
            favoritesDao.addToEnd(entityId)
            addCachedFavorite(entityId)
        }
    }

    fun removeFavoriteEntity(entityId: String) {
        viewModelScope.launch {
            favoritesDao.delete(entityId)
            favoriteCachesDao.delete(entityId)

            if (favoritesDao.getAll().isEmpty() && isFavoritesOnly) {
                setWearFavoritesOnly(false)
            }
        }
    }

    private fun addCachedFavorite(entityId: String) {
        viewModelScope.launch {
            val entity = entities[entityId]
            val attributes = entity?.attributes as Map<*, *>
            val icon = attributes["icon"] as String?
            val name = attributes["friendly_name"]?.toString() ?: entityId
            favoriteCachesDao.add(FavoriteCaches(entityId, name, icon))
        }
    }

    fun setAssistantApp(allowed: Boolean) {
        val assistantAppComponent = ComponentName(
            BuildConfig.APPLICATION_ID,
            "io.homeassistant.companion.android.conversation.AssistantActivity",
        )
        app.packageManager.setComponentEnabledSetting(
            assistantAppComponent,
            if (allowed) {
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            },
            PackageManager.DONT_KILL_APP,
        )
        isAssistantAppAllowed = allowed
    }

    fun refreshNotificationPermission() {
        areNotificationsAllowed = NotificationManagerCompat.from(app).areNotificationsEnabled()
    }

    fun logout() {
        homePresenter.onLogoutClicked()

        // also clear cache when logging out
        clearCache()
    }

    private fun clearCache() {
        viewModelScope.launch {
            favoriteCachesDao.deleteAll()
        }
    }

    /**
     * Convert a Flow into a State object that updates until the view model is cleared.
     */
    private fun <T> Flow<T>.collectAsState(initial: T): State<T> {
        val state = mutableStateOf(initial)
        viewModelScope.launch {
            collect { state.value = it }
        }
        return state
    }

    private fun <T> Flow<List<T>>.collectAsState(): State<List<T>> = collectAsState(initial = emptyList())
}
