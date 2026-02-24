package io.homeassistant.companion.android.home

import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.HomeAssistantApplication
import io.homeassistant.companion.android.common.data.integration.Entity
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
import kotlinx.coroutines.flow.update
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

    /**
     * Holds entity classification information for filtering entities in the UI.
     */
    @Immutable
    data class EntityClassification(
        val entitiesWithoutArea: Set<String> = emptySet(),
        val entitiesWithCategory: Set<String> = emptySet(),
        val entitiesHidden: Set<String> = emptySet(),
        val hasAreasToShow: Boolean = false,
        val hasMoreEntitiesToShow: Boolean = false,
    )

    /**
     * Holds the navigation state for the entity list screen, set before navigating.
     */
    @Immutable
    data class EntityListNavigation(
        val entityListIds: Map<String, List<String>> = emptyMap(),
        val entityListsOrder: List<String> = emptyList(),
        val entityListFilter: (Entity) -> Boolean = { true },
        val entityLists: Map<String, List<Entity>> = emptyMap(),
    )

    /**
     * Immutable UI state for MainView that contains thread-safe snapshots of all data.
     */
    @Immutable
    data class MainViewUiState(
        val entities: Map<String, Entity> = emptyMap(),
        val favoriteCaches: List<FavoriteCaches> = emptyList(),
        val isFavoritesOnly: Boolean = false,
        val isHapticEnabled: Boolean = false,
        val isToastEnabled: Boolean = false,
        val isShowShortcutTextEnabled: Boolean = false,
        val isAssistantAppAllowed: Boolean = true,
        val areNotificationsAllowed: Boolean = false,
        val templateTiles: Map<Int, TemplateTileConfig> = emptyMap(),
        val shortcutEntitiesMap: Map<Int?, List<SimplifiedEntity>> = emptyMap(),
        val loadingState: LoadingState = LoadingState.LOADING,
        val entitiesByArea: Map<String, List<String>> = emptyMap(),
        val areas: List<AreaRegistryResponse> = emptyList(),
        val entitiesByDomainFilteredOrder: List<String> = emptyList(),
        val entitiesByDomainFiltered: Map<String, List<String>> = emptyMap(),
        val entitiesByDomain: Map<String, List<String>> = emptyMap(),
        val favoriteEntityIds: List<String> = emptyList(),
        val allEntitiesByDomain: Map<String, List<Entity>> = emptyMap(),
        val domainNames: Map<String, String> = emptyMap(),
        val entityListNavigation: EntityListNavigation = EntityListNavigation(),
    )

    private val app = application

    private lateinit var homePresenter: HomePresenter

    /**
     * Internal thread-safe holder for registry data used for entity classification.
     * Wrapped in a [MutableStateFlow] to guarantee visibility and consistency across dispatchers.
     */
    private data class Registries(
        val area: List<AreaRegistryResponse> = emptyList(),
        val device: List<DeviceRegistryResponse> = emptyList(),
        val entity: List<EntityRegistryResponse> = emptyList(),
    )

    private val registries = MutableStateFlow(Registries())

    // TODO: This is bad, do this instead: https://stackoverflow.com/questions/46283981/android-viewmodel-additional-arguments
    fun init(homePresenter: HomePresenter) {
        this.homePresenter = homePresenter
        loadSettings()
        loadEntities()
    }

    private val _supportedEntities = MutableStateFlow(emptyList<String>())
    val supportedEntities = _supportedEntities.asStateFlow()

    private val _entityClassification = MutableStateFlow(EntityClassification())
    val entityClassification = _entityClassification.asStateFlow()

    private val _mainViewUiState = MutableStateFlow(MainViewUiState())
    val mainViewUiState = _mainViewUiState.asStateFlow()

    private inline fun updateUiState(transform: (MainViewUiState) -> MainViewUiState) {
        _mainViewUiState.update { transform(it) }
    }

    val cameraTiles = cameraTileDao.getAllFlow().collectAsState()

    val thermostatTiles = thermostatTileDao.getAllFlow().collectAsState()

    fun setEntityListNavigation(
        entityListIds: Map<String, List<String>>,
        entityListsOrder: List<String>,
        entityListFilter: (Entity) -> Boolean,
    ) {
        updateUiState {
            it.copy(
                entityListNavigation = EntityListNavigation(
                    entityListIds = entityListIds,
                    entityListsOrder = entityListsOrder,
                    entityListFilter = entityListFilter,
                    entityLists = entityListIds.resolveEntities(it.entities),
                ),
            )
        }
    }

    private fun Map<String, List<String>>.resolveEntities(
        allEntities: Map<String, Entity>,
    ): Map<String, List<Entity>> = mapValues { (_, ids) ->
        ids.mapNotNull { id -> allEntities[id] }
    }

    init {
        viewModelScope.launch {
            favoritesDao.getAllFlow().collect { favoriteIds ->
                updateUiState { it.copy(favoriteEntityIds = favoriteIds) }
            }
        }
        viewModelScope.launch {
            updateUiState { it.copy(favoriteCaches = favoriteCachesDao.getAll()) }
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

            val assistantAppComponent = ComponentName(
                BuildConfig.APPLICATION_ID,
                "io.homeassistant.companion.android.conversation.AssistantActivity",
            )
            updateUiState {
                it.copy(
                    templateTiles = homePresenter.getAllTemplateTiles(),
                    isHapticEnabled = homePresenter.getWearHapticFeedback(),
                    isToastEnabled = homePresenter.getWearToastConfirmation(),
                    isShowShortcutTextEnabled = homePresenter.getShowShortcutText(),
                    isFavoritesOnly = homePresenter.getWearFavoritesOnly(),
                    isAssistantAppAllowed = app.packageManager.getComponentEnabledSetting(assistantAppComponent) !=
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    areNotificationsAllowed = NotificationManagerCompat.from(app).areNotificationsEnabled(),
                )
            }
        }
    }

    fun loadShortcutTileEntities() {
        viewModelScope.launch {
            updateUiState {
                it.copy(shortcutEntitiesMap = homePresenter.getAllTileShortcuts())
            }
        }
    }

    fun loadTemplateTiles() {
        viewModelScope.launch {
            updateUiState { it.copy(templateTiles = homePresenter.getAllTemplateTiles()) }
        }
    }

    fun loadEntities() {
        viewModelScope.launch {
            if (!homePresenter.isConnected()) return@launch
            try {
                // Load initial state
                updateUiState { it.copy(loadingState = LoadingState.LOADING) }
                updateUI()

                // Finished initial load, update state
                val webSocketState = homePresenter.getWebSocketState()
                if (webSocketState == WebSocketState.ClosedAuth) {
                    homePresenter.onInvalidAuthorization()
                    return@launch
                }
                updateUiState {
                    it.copy(
                        loadingState = if (webSocketState == WebSocketState.Active) {
                            LoadingState.READY
                        } else {
                            LoadingState.ERROR
                        },
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Exception while loading entities")
                updateUiState { it.copy(loadingState = LoadingState.ERROR) }
            }
        }
    }

    suspend fun updateUI() = withContext(Dispatchers.IO) {
        if (!homePresenter.isConnected()) return@withContext
        val getAreaRegistry = async { homePresenter.getAreaRegistry() }
        val getDeviceRegistry = async { homePresenter.getDeviceRegistry() }
        val getEntityRegistry = async { homePresenter.getEntityRegistry() }
        val getEntities = async { homePresenter.getEntities() }

        val newEntityRegistry = getEntityRegistry.await().orEmpty()
        val isFavoritesOnly = mainViewUiState.value.isFavoritesOnly
        if (!isFavoritesOnly) {
            val newAreaRegistry = getAreaRegistry.await().orEmpty()
            val newDeviceRegistry = getDeviceRegistry.await().orEmpty()
            registries.update { Registries(area = newAreaRegistry, device = newDeviceRegistry, entity = newEntityRegistry) }
        } else {
            registries.update { it.copy(entity = newEntityRegistry) }
        }

        _supportedEntities.value = getSupportedEntities()

        getEntities.await()?.let { updateEntityStates(it, replaceAll = true) }
        if (!isFavoritesOnly) {
            updateEntityDomains()
        }
    }

    suspend fun entityUpdates() {
        if (!homePresenter.isConnected()) {
            return
        }
        homePresenter.getEntityUpdates(supportedEntities.value)?.collect { entity ->
            updateEntityStates(listOf(entity))
            if (!mainViewUiState.value.isFavoritesOnly) {
                updateEntityDomains()
            }
        }
    }

    /**
     * Filters supported entities, updates UI state, and caches favorites.
     * When [replaceAll] is true, replaces the entire entity map; otherwise merges into existing.
     */
    private fun updateEntityStates(entities: List<Entity>, replaceAll: Boolean = false) {
        val supportedDomains = supportedDomains()
        val supported = entities
            .filter { it.domain in supportedDomains }
            .associateBy { it.entityId }
        updateUiState { uiState ->
            val updatedEntities = if (replaceAll) supported else uiState.entities + supported
            uiState.copy(
                entities = updatedEntities,
                allEntitiesByDomain = updatedEntities.values.groupBy { it.domain },
                entityListNavigation = uiState.entityListNavigation.copy(
                    entityLists = uiState.entityListNavigation.entityListIds.resolveEntities(updatedEntities),
                ),
            )
        }
        val favoriteIds = mainViewUiState.value.favoriteEntityIds
        supported.keys
            .filter { it in favoriteIds }
            .forEach { addCachedFavorite(it) }
    }

    suspend fun areaUpdates() {
        if (!homePresenter.isConnected() || mainViewUiState.value.isFavoritesOnly) {
            return
        }
        homePresenter.getAreaRegistryUpdates()?.throttleLatest(1000)?.collect {
            registries.update { it.copy(area = homePresenter.getAreaRegistry().orEmpty()) }
            updateEntityDomains()
        }
    }

    suspend fun deviceUpdates() {
        if (!homePresenter.isConnected() || mainViewUiState.value.isFavoritesOnly) {
            return
        }
        homePresenter.getDeviceRegistryUpdates()?.throttleLatest(1000)?.collect {
            registries.update { it.copy(device = homePresenter.getDeviceRegistry().orEmpty()) }
            updateEntityDomains()
        }
    }

    suspend fun entityRegistryUpdates() {
        if (!homePresenter.isConnected()) {
            return
        }
        homePresenter.getEntityRegistryUpdates()?.throttleLatest(1000)?.collect {
            registries.update { it.copy(entity = homePresenter.getEntityRegistry().orEmpty()) }
            _supportedEntities.value = getSupportedEntities()
            updateEntityDomains()
        }
    }

    private fun getSupportedEntities(): List<String> = registries.value.entity
        .map { it.entityId }
        .filter { it.split(".")[0] in supportedDomains() }

    /**
     * Computes entity groupings by area and domain, then updates UiState in a single shot.
     * This function does a lot of manipulation and could take some time so we need
     * to make sure it doesn't happen in the Main thread.
     */
    private suspend fun updateEntityDomains() = withContext(Dispatchers.Default) {
        val entities = mainViewUiState.value.entities
        val entitiesList = entities.values.sortedBy { it.entityId }
        val areasList = registries.value.area.sortedBy { it.name }
        val domainsList = entitiesList.map { it.domain }.distinct()

        // Single pass: compute entity metadata and cache area lookups to avoid redundant calls
        val entityAreaMap = mutableMapOf<String, AreaRegistryResponse?>()
        val withoutArea = mutableSetOf<String>()
        val withCategory = mutableSetOf<String>()
        val hidden = mutableSetOf<String>()

        entities.keys.forEach { entityId ->
            val area = getAreaForEntity(entityId)
            entityAreaMap[entityId] = area

            if (area == null) {
                withoutArea.add(entityId)
            }
            if (getCategoryForEntity(entityId) != null) {
                withCategory.add(entityId)
            }
            if (getHiddenByForEntity(entityId) != null) {
                hidden.add(entityId)
            }
        }

        // Determine if entity should be shown in filtered views
        val shouldShowEntity: (String) -> Boolean = { entityId ->
            entityId !in withCategory && entityId !in hidden
        }

        // Group entities by area
        val computedEntitiesByArea = mutableMapOf<String, List<String>>()
        areasList.forEach { area ->
            val entitiesInArea = entitiesList
                .filter { entityAreaMap[it.entityId]?.areaId == area.areaId }
                .sortedBy { (it.attributes["friendly_name"] ?: it.entityId) as String }
            computedEntitiesByArea[area.areaId] = entitiesInArea.map { it.entityId }
        }

        // Group entities by domain (both full and filtered) in a single pass
        val computedEntitiesByDomain = mutableMapOf<String, List<String>>()
        val computedAllEntitiesByDomain = mutableMapOf<String, List<Entity>>()
        val computedEntitiesByDomainFiltered = mutableMapOf<String, List<String>>()
        val filteredDomainsList = mutableListOf<String>()

        domainsList.forEach { domain ->
            val entitiesInDomain = entitiesList.filter { it.domain == domain }
            computedEntitiesByDomain[domain] = entitiesInDomain.map { it.entityId }
            computedAllEntitiesByDomain[domain] = entitiesInDomain

            // Filtered entities (without area, category, or hidden status)
            val entitiesInDomainFiltered = entitiesInDomain.filter { entity ->
                entity.entityId in withoutArea &&
                    entity.entityId !in withCategory &&
                    entity.entityId !in hidden
            }
            if (entitiesInDomainFiltered.isNotEmpty()) {
                filteredDomainsList.add(domain)
                computedEntitiesByDomainFiltered[domain] = entitiesInDomainFiltered.map { it.entityId }
            }
        }

        val computedDomainNames = domainsList.associateWith { domain ->
            stringForDomain(domain) ?: domain
        }

        // Compute UI visibility flags
        val hasAreasToShow = computedEntitiesByArea.any { (_, entityIds) ->
            entityIds.any { entityId -> shouldShowEntity(entityId) }
        }
        val hasMoreEntitiesToShow = withoutArea.any(shouldShowEntity)

        // Update entity classification with all computed values
        _entityClassification.value = EntityClassification(
            entitiesWithoutArea = withoutArea,
            entitiesWithCategory = withCategory,
            entitiesHidden = hidden,
            hasAreasToShow = hasAreasToShow,
            hasMoreEntitiesToShow = hasMoreEntitiesToShow,
        )

        // Update UiState in a single shot
        updateUiState { uiState ->
            uiState.copy(
                entities = entities,
                entitiesByArea = computedEntitiesByArea,
                areas = areasList,
                entitiesByDomainFilteredOrder = filteredDomainsList,
                entitiesByDomainFiltered = computedEntitiesByDomainFiltered,
                entitiesByDomain = computedEntitiesByDomain,
                allEntitiesByDomain = computedAllEntitiesByDomain,
                domainNames = computedDomainNames,
                entityListNavigation = uiState.entityListNavigation.copy(
                    entityLists = uiState.entityListNavigation.entityListIds.resolveEntities(entities),
                ),
            )
        }
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

    fun getAreaForEntity(entityId: String): AreaRegistryResponse? {
        val regs = registries.value
        return RegistriesDataHandler.getAreaForEntity(entityId, regs.area, regs.device, regs.entity)
    }

    fun getCategoryForEntity(entityId: String): String? =
        RegistriesDataHandler.getCategoryForEntity(entityId, registries.value.entity)

    fun getHiddenByForEntity(entityId: String): String? =
        RegistriesDataHandler.getHiddenByForEntity(entityId, registries.value.entity)

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
            val current = mainViewUiState.value.shortcutEntitiesMap[tileId].orEmpty()
            val updated = current.toMutableList().apply {
                if (index < size) set(index, entity) else add(entity)
            }
            homePresenter.setTileShortcuts(tileId, entities = updated)
            updateUiState {
                it.copy(shortcutEntitiesMap = it.shortcutEntitiesMap + (tileId to updated))
            }
        }
    }

    fun clearTileShortcut(tileId: Int?, index: Int) {
        viewModelScope.launch {
            val current = mainViewUiState.value.shortcutEntitiesMap[tileId] ?: return@launch
            if (index < current.size) {
                val updated = current.toMutableList().apply { removeAt(index) }
                homePresenter.setTileShortcuts(tileId, entities = updated)
                updateUiState {
                    it.copy(shortcutEntitiesMap = it.shortcutEntitiesMap + (tileId to updated))
                }
            }
        }
    }

    fun setHapticEnabled(enabled: Boolean) {
        viewModelScope.launch {
            homePresenter.setWearHapticFeedback(enabled)
            updateUiState { it.copy(isHapticEnabled = enabled) }
        }
    }

    fun setToastEnabled(enabled: Boolean) {
        viewModelScope.launch {
            homePresenter.setWearToastConfirmation(enabled)
            updateUiState { it.copy(isToastEnabled = enabled) }
        }
    }

    fun setShowShortcutTextEnabled(enabled: Boolean) {
        viewModelScope.launch {
            homePresenter.setShowShortcutTextEnabled(enabled)
            updateUiState { it.copy(isShowShortcutTextEnabled = enabled) }
        }
    }

    fun setWearFavoritesOnly(enabled: Boolean) {
        viewModelScope.launch {
            homePresenter.setWearFavoritesOnly(enabled)
            updateUiState { it.copy(isFavoritesOnly = enabled) }
            if (!enabled) {
                updateEntityDomains()
            }
        }
    }

    fun setTemplateTileRefreshInterval(tileId: Int, interval: Int) {
        viewModelScope.launch {
            homePresenter.setTemplateTileRefreshInterval(tileId, interval)
            updateUiState { state ->
                val current = state.templateTiles[tileId] ?: return@updateUiState state
                state.copy(templateTiles = state.templateTiles + (tileId to current.copy(refreshInterval = interval)))
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

            if (favoritesDao.getAll().isEmpty() && mainViewUiState.value.isFavoritesOnly) {
                setWearFavoritesOnly(false)
            }
        }
    }

    private fun addCachedFavorite(entityId: String) {
        viewModelScope.launch {
            val entity = mainViewUiState.value.entities[entityId]
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
        updateUiState { it.copy(isAssistantAppAllowed = allowed) }
    }

    fun refreshNotificationPermission() {
        updateUiState {
            it.copy(areNotificationsAllowed = NotificationManagerCompat.from(app).areNotificationsEnabled())
        }
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
