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
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.CAMERA_DOMAIN
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.CLIMATE_DOMAIN
import io.homeassistant.companion.android.common.data.integration.display.EntitiesForDisplayManager
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplay
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayState
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithContext
import io.homeassistant.companion.android.common.data.prefs.impl.entities.TemplateTileConfig
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.WebSocketState
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.common.sensors.SensorRepository
import io.homeassistant.companion.android.common.util.mdiName
import io.homeassistant.companion.android.data.SimplifiedEntity
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
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@HiltViewModel
class MainViewModel @Inject constructor(
    private val entitiesForDisplayManager: EntitiesForDisplayManager,
    private val favoritesDao: FavoritesDao,
    private val favoriteCachesDao: FavoriteCachesDao,
    private val sensorRepository: SensorRepository,
    private val cameraTileDao: CameraTileDao,
    private val thermostatTileDao: ThermostatTileDao,
    private val managers: Set<@JvmSuppressWildcards SensorManager>,
    application: Application,
) : AndroidViewModel(application) {

    /** Every registered [SensorManager], used by the sensor management screens. */
    val sensorManagers: List<SensorManager> get() = managers.toList()

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
        val entityListFilter: (EntityDisplay) -> Boolean = { true },
        val entityLists: Map<String, List<EntityDisplay>> = emptyMap(),
    )

    /**
     * Immutable UI state for MainView that contains thread-safe snapshots of all data.
     */
    @Immutable
    data class MainViewUiState(
        val displayItems: Map<String, EntityDisplayWithContext> = emptyMap(),
        val cameraItems: List<EntityDisplay> = emptyList(),
        val climateItems: List<EntityDisplay> = emptyList(),
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
        val areas: List<String> = emptyList(),
        val entitiesByDomainFilteredOrder: List<String> = emptyList(),
        val entitiesByDomainFiltered: Map<String, List<String>> = emptyMap(),
        val entitiesByDomain: Map<String, List<String>> = emptyMap(),
        val favoriteEntityIds: List<String> = emptyList(),
        val allDisplayItemsByDomain: Map<String, List<EntityDisplay>> = emptyMap(),
        val domainNames: Map<String, String> = emptyMap(),
        val entityListNavigation: EntityListNavigation = EntityListNavigation(),
    )

    private val app = application

    private lateinit var homePresenter: HomePresenter

    /**
     * Internal thread-safe holder for registry data used for entity classification.
     * Wrapped in a [MutableStateFlow] to guarantee visibility and consistency across dispatchers.
     */
    // TODO: This is bad, do this instead: https://stackoverflow.com/questions/46283981/android-viewmodel-additional-arguments
    fun init(homePresenter: HomePresenter) {
        this.homePresenter = homePresenter
        loadSettings()
        loadEntities()
    }

    /** Grouping key of the last snapshot the entities were grouped for, see [groupingKey]. */
    private var lastGroupingKey: List<Any?>? = null

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
        entityListFilter: (EntityDisplay) -> Boolean,
    ) {
        updateUiState {
            it.copy(
                entityListNavigation = EntityListNavigation(
                    entityListIds = entityListIds,
                    entityListsOrder = entityListsOrder,
                    entityListFilter = entityListFilter,
                    entityLists = entityListIds.resolveEntities(it.displayItems),
                ),
            )
        }
    }

    private fun Map<String, List<String>>.resolveEntities(
        allItems: Map<String, EntityDisplay>,
    ): Map<String, List<EntityDisplay>> = mapValues { (_, ids) ->
        ids.mapNotNull { id -> allItems[id] }
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

    val sensors = sensorRepository.getAllFlow().collectAsState()

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

    /**
     * Observes the entities of the supported domains with their display information. Emissions are
     * complete snapshots, so each one replaces the state of the screen.
     *
     * Collected on [Dispatchers.Default]: an emission walks every entity of the server, and
     * regroups them when [groupingKey] changed, which is too much work for the main thread the
     * caller collects from.
     */
    suspend fun observeEntities() = withContext(Dispatchers.Default) {
        entitiesForDisplayManager
            .observeInContext(ServerManager.SERVER_ID_ACTIVE) { it.domain in supportedDomains() }
            .collect { state ->
                if (state !is EntityDisplayState.Loaded) return@collect

                updateDisplayItems(state.entitiesById)

                if (mainViewUiState.value.isFavoritesOnly) return@collect

                // States change far more often than what the groupings are made of
                val groupingKey = groupingKey(state.entitiesById)
                if (groupingKey != lastGroupingKey) {
                    lastGroupingKey = groupingKey
                    updateEntityDomains()
                }
            }
    }

    /**
     * What [updateEntityDomains] groups the entities by: two snapshots sharing it group the same
     * way, whatever their states are. Keep it in sync with the fields the grouping reads.
     */
    private fun groupingKey(items: Map<String, EntityDisplayWithContext>): List<Any?> = items.values.map {
        listOf(it.entityId, it.name, it.domain, it.areaName, it.isHidden, it.entityCategory)
    }

    /** Applies a snapshot of the entities to the UI state and caches the favorites of the user. */
    private fun updateDisplayItems(items: Map<String, EntityDisplayWithContext>) {
        updateUiState { uiState ->
            val itemsByDomain = items.values.groupBy { it.domain }
            uiState.copy(
                displayItems = items,
                cameraItems = itemsByDomain[CAMERA_DOMAIN].orEmpty(),
                climateItems = itemsByDomain[CLIMATE_DOMAIN].orEmpty(),
                allDisplayItemsByDomain = itemsByDomain,
                entityListNavigation = uiState.entityListNavigation.copy(
                    entityLists = uiState.entityListNavigation.entityListIds.resolveEntities(items),
                ),
            )
        }
        val favoriteIds = mainViewUiState.value.favoriteEntityIds
        items.keys
            .filter { it in favoriteIds }
            .forEach { addCachedFavorite(it) }
    }

    /**
     * Computes entity groupings by area and domain, then updates UiState in a single shot.
     * This function does a lot of manipulation and could take some time so we need
     * to make sure it doesn't happen in the Main thread.
     */
    private suspend fun updateEntityDomains() = withContext(Dispatchers.Default) {
        val items = mainViewUiState.value.displayItems
        val itemsList = items.values.sortedBy { it.entityId }
        val domainsList = itemsList.map { it.domain }.distinct()

        val withoutArea = mutableSetOf<String>()
        val withCategory = mutableSetOf<String>()
        val hidden = mutableSetOf<String>()

        itemsList.forEach { item ->
            if (item.areaName == null) {
                withoutArea.add(item.entityId)
            }
            if (item.entityCategory != null) {
                withCategory.add(item.entityId)
            }
            if (item.isHidden) {
                hidden.add(item.entityId)
            }
        }

        // Areas holding at least one entity, the only ones the UI can navigate to
        val areasList = itemsList.mapNotNull { it.areaName }.distinct().sorted()

        // Determine if entity should be shown in filtered views
        val shouldShowEntity: (String) -> Boolean = { entityId ->
            entityId !in withCategory && entityId !in hidden
        }

        // Group entities by area
        val computedEntitiesByArea = mutableMapOf<String, List<String>>()
        areasList.forEach { area ->
            val entitiesInArea = itemsList
                .filter { it.areaName == area }
                .sortedBy { it.name }
            computedEntitiesByArea[area] = entitiesInArea.map { it.entityId }
        }

        // Group entities by domain (both full and filtered) in a single pass
        val computedEntitiesByDomain = mutableMapOf<String, List<String>>()
        val computedItemsByDomain = mutableMapOf<String, List<EntityDisplay>>()
        val computedEntitiesByDomainFiltered = mutableMapOf<String, List<String>>()
        val filteredDomainsList = mutableListOf<String>()

        domainsList.forEach { domain ->
            val entitiesInDomain = itemsList.filter { it.domain == domain }
            computedEntitiesByDomain[domain] = entitiesInDomain.map { it.entityId }
            computedItemsByDomain[domain] = entitiesInDomain

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
                entitiesByArea = computedEntitiesByArea,
                areas = areasList,
                entitiesByDomainFilteredOrder = filteredDomainsList,
                entitiesByDomainFiltered = computedEntitiesByDomainFiltered,
                entitiesByDomain = computedEntitiesByDomain,
                allDisplayItemsByDomain = computedItemsByDomain,
                domainNames = computedDomainNames,
                entityListNavigation = uiState.entityListNavigation.copy(
                    entityLists = uiState.entityListNavigation.entityListIds.resolveEntities(items),
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
            val basicSensor = sensorManager.getAvailableSensors()
                .first { basicSensor -> basicSensor.id == sensorId }
            updateSensorEntity(basicSensor, isEnabled)

            if (isEnabled) {
                try {
                    sensorManager.requestSensorUpdate()
                } catch (e: Exception) {
                    Timber.e(e, "Exception while requesting update for sensor $sensorId")
                }
            }
        }
    }

    private suspend fun updateSensorEntity(basicSensor: SensorManager.BasicSensor, isEnabled: Boolean) {
        homePresenter.getServerId()?.let { serverId ->
            sensorRepository.setSensorsEnabled(listOf(basicSensor.id), serverId, isEnabled)
            SensorReceiver.updateAllSensors(getApplication())
        }
    }

    fun updateAllSensors(sensorManager: SensorManager) {
        availableSensors = emptyList()
        viewModelScope.launch {
            val context = getApplication<HomeAssistantApplication>().applicationContext
            availableSensors = sensorManager
                .getAvailableSensors()
                .sortedBy { context.getString(it.name) }.distinct()
        }
    }

    fun initAllSensors() {
        viewModelScope.launch {
            for (manager in managers) {
                for (basicSensor in manager.getAvailableSensors()) {
                    manager.isEnabled(basicSensor)
                }
            }
        }
    }

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
            val item = mainViewUiState.value.displayItems[entityId] ?: return@launch
            favoriteCachesDao.add(FavoriteCaches(entityId, item.name, item.statelessIcon.mdiName))
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
