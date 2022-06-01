package io.homeassistant.companion.android.home

import android.app.Application
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.domain
import io.homeassistant.companion.android.common.data.websocket.WebSocketState
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AreaRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.DeviceRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryResponse
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.data.SimplifiedEntity
import io.homeassistant.companion.android.database.sensor.SensorDao
import io.homeassistant.companion.android.database.wear.FavoritesDao
import io.homeassistant.companion.android.database.wear.getAllFlow
import io.homeassistant.companion.android.util.RegistriesDataHandler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val favoritesDao: FavoritesDao,
    private val sensorsDao: SensorDao,
    application: Application
) : AndroidViewModel(application) {

    companion object {
        const val TAG = "MainViewModel"
    }

    enum class LoadingState {
        LOADING, READY, ERROR
    }

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
    var entities = mutableStateMapOf<String, Entity<*>>()
        private set

    /**
     * IDs of favorites in the Favorites database.
     */
    val favoriteEntityIds = favoritesDao.getAllFlow().collectAsState()

    var shortcutEntities = mutableStateListOf<SimplifiedEntity>()
        private set
    var areas = mutableListOf<AreaRegistryResponse>()
        private set

    var entitiesByArea = mutableStateMapOf<String, SnapshotStateList<Entity<*>>>()
        private set
    var entitiesByDomain = mutableStateMapOf<String, SnapshotStateList<Entity<*>>>()
        private set
    var entitiesByAreaOrder = mutableStateListOf<String>()
        private set
    var entitiesByDomainOrder = mutableStateListOf<String>()
        private set

    // Content of EntityListView
    var entityLists = mutableStateMapOf<String, List<Entity<*>>>()
    var entityListsOrder = mutableStateListOf<String>()
    var entityListFilter: (Entity<*>) -> Boolean = { true }

    // settings
    var loadingState = mutableStateOf(LoadingState.LOADING)
        private set
    var isHapticEnabled = mutableStateOf(false)
        private set
    var isToastEnabled = mutableStateOf(false)
        private set
    var isShowShortcutTextEnabled = mutableStateOf(false)
        private set
    var templateTileContent = mutableStateOf("")
        private set
    var templateTileRefreshInterval = mutableStateOf(0)
        private set

    fun supportedDomains(): List<String> = HomePresenterImpl.supportedDomains

    fun stringForDomain(domain: String): String? =
        HomePresenterImpl.domainsWithNames[domain]?.let { getApplication<Application>().getString(it) }

    val sensors = sensorsDao.getAllFlow().collectAsState()

    private fun loadSettings() {
        viewModelScope.launch {
            if (!homePresenter.isConnected()) {
                return@launch
            }
            shortcutEntities.addAll(homePresenter.getTileShortcuts())
            isHapticEnabled.value = homePresenter.getWearHapticFeedback()
            isToastEnabled.value = homePresenter.getWearToastConfirmation()
            isShowShortcutTextEnabled.value = homePresenter.getShowShortcutText()
            templateTileContent.value = homePresenter.getTemplateTileContent()
            templateTileRefreshInterval.value = homePresenter.getTemplateTileRefreshInterval()
        }
    }

    fun loadEntities() {
        viewModelScope.launch {
            if (!homePresenter.isConnected()) {
                return@launch
            }
            try {
                // Load initial state
                loadingState.value = LoadingState.LOADING
                homePresenter.getAreaRegistry()?.let {
                    areaRegistry = it
                    areas.addAll(it)
                }
                deviceRegistry = homePresenter.getDeviceRegistry()
                entityRegistry = homePresenter.getEntityRegistry()
                homePresenter.getEntities()?.forEach {
                    if (supportedDomains().contains(it.domain)) {
                        entities[it.entityId] = it
                    }
                }
                updateEntityDomains()

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

                // Listen for updates
                viewModelScope.launch {
                    homePresenter.getEntityUpdates()?.collect {
                        if (supportedDomains().contains(it.domain)) {
                            entities[it.entityId] = it
                            updateEntityDomains()
                        }
                    }
                }
                viewModelScope.launch {
                    homePresenter.getAreaRegistryUpdates()?.collect {
                        areaRegistry = homePresenter.getAreaRegistry()
                        areas.clear()
                        areaRegistry?.let {
                            areas.addAll(it)
                        }
                        updateEntityDomains()
                    }
                }
                viewModelScope.launch {
                    homePresenter.getDeviceRegistryUpdates()?.collect {
                        deviceRegistry = homePresenter.getDeviceRegistry()
                        updateEntityDomains()
                    }
                }
                viewModelScope.launch {
                    homePresenter.getEntityRegistryUpdates()?.collect {
                        entityRegistry = homePresenter.getEntityRegistry()
                        updateEntityDomains()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception while loading entities", e)
                loadingState.value = LoadingState.ERROR
            }
        }
    }

    fun updateEntityDomains() {
        val entitiesList = entities.values.toList().sortedBy { it.entityId }
        val areasList = areaRegistry.orEmpty().sortedBy { it.name }
        val domainsList = entitiesList.map { it.domain }.distinct()

        // Create a list with all areas + their entities
        areasList.forEach { area ->
            val entitiesInArea = mutableStateListOf<Entity<*>>()
            entitiesInArea.addAll(
                entitiesList
                    .filter { getAreaForEntity(it.entityId)?.areaId == area.areaId }
                    .map { it as Entity<Map<String, Any>> }
                    .sortedBy { (it.attributes["friendly_name"] ?: it.entityId) as String }
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
            val entitiesInDomain = mutableStateListOf<Entity<*>>()
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
    fun setColorTemp(entityId: String, colorTemp: Float) {
        viewModelScope.launch {
            homePresenter.onColorTempChanged(entityId, colorTemp)
        }
    }

    fun enableDisableSensor(sensorManager: SensorManager, sensorId: String, isEnabled: Boolean) {
        viewModelScope.launch {
            val basicSensor = sensorManager.getAvailableSensors(getApplication())
                .first { basicSensor -> basicSensor.id == sensorId }
            updateSensorEntity(sensorsDao, basicSensor, isEnabled)

            if (isEnabled) try {
                sensorManager.requestSensorUpdate(getApplication())
            } catch (e: Exception) {
                Log.e(TAG, "Exception while requesting update for sensor $sensorId", e)
            }
        }
    }

    private suspend fun updateSensorEntity(
        sensorDao: SensorDao,
        basicSensor: SensorManager.BasicSensor,
        isEnabled: Boolean
    ) {
        sensorDao.setSensorsEnabled(listOf(basicSensor.id), isEnabled)
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
        }
    }

    fun setTileShortcut(index: Int, entity: SimplifiedEntity) {
        viewModelScope.launch {
            if (index < shortcutEntities.size) {
                shortcutEntities[index] = entity
            } else {
                shortcutEntities.add(entity)
            }
            homePresenter.setTileShortcuts(shortcutEntities)
        }
    }

    fun clearTileShortcut(index: Int) {
        viewModelScope.launch {
            if (index < shortcutEntities.size) {
                shortcutEntities.removeAt(index)
                homePresenter.setTileShortcuts(shortcutEntities)
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

    fun setTemplateTileContent(content: String) {
        viewModelScope.launch {
            homePresenter.setTemplateTileContent(content)
            templateTileContent.value = content
        }
    }

    fun setTemplateTileRefreshInterval(interval: Int) {
        viewModelScope.launch {
            homePresenter.setTemplateTileRefreshInterval(interval)
            templateTileRefreshInterval.value = interval
        }
    }

    fun addFavoriteEntity(entityId: String) {
        viewModelScope.launch {
            favoritesDao.addToEnd(entityId)
        }
    }

    fun removeFavoriteEntity(entityId: String) {
        viewModelScope.launch {
            favoritesDao.delete(entityId)
        }
    }

    fun logout() {
        homePresenter.onLogoutClicked()
    }

    /**
     * Convert a Flow into a State object that updates until the view model is cleared.
     */
    private fun <T> Flow<T>.collectAsState(
        initial: T
    ): State<T> {
        val state = mutableStateOf(initial)
        viewModelScope.launch {
            collect { state.value = it }
        }
        return state
    }
    private fun <T> Flow<List<T>>.collectAsState(): State<List<T>> = collectAsState(initial = emptyList())
}
