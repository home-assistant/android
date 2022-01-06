package io.homeassistant.companion.android.home

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.HomeAssistantApplication
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AreaRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.DeviceRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryResponse
import io.homeassistant.companion.android.data.SimplifiedEntity
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.wear.Favorites
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject
import io.homeassistant.companion.android.common.R as commonR

@HiltViewModel
class MainViewModel @Inject constructor(application: Application) : AndroidViewModel(application) {

    private lateinit var homePresenter: HomePresenter
    val app = getApplication<HomeAssistantApplication>()
    private val favoritesDao = AppDatabase.getInstance(app.applicationContext).favoritesDao()
    private var areaRegistry: List<AreaRegistryResponse>? = null
    private var deviceRegistry: List<DeviceRegistryResponse>? = null
    private var entityRegistry: List<EntityRegistryResponse>? = null

    // TODO: This is bad, do this instead: https://stackoverflow.com/questions/46283981/android-viewmodel-additional-arguments
    fun init(homePresenter: HomePresenter) {
        this.homePresenter = homePresenter
        loadEntities()
        getFavorites()
    }

    // entities
    var entities = mutableStateMapOf<String, Entity<*>>()
        private set
    var favoriteEntityIds = mutableStateListOf<String>()
        private set
    var shortcutEntities = mutableStateListOf<SimplifiedEntity>()
        private set
    var areas = mutableListOf<AreaRegistryResponse>()
        private set

    var entitiesByArea = mutableStateMapOf<String, SnapshotStateList<Entity<*>>>()
        private set
    var entitiesByDomain = mutableStateMapOf<String, SnapshotStateList<Entity<*>>>()
        private set

    // Content of EntityListView
    var entityLists = mutableStateMapOf<String, List<Entity<*>>>()
    var entityListFilter: (Entity<*>) -> Boolean = { true }

    // settings
    var isHapticEnabled = mutableStateOf(false)
        private set
    var isToastEnabled = mutableStateOf(false)
        private set

    private fun favorites(): Flow<List<Favorites>>? = favoritesDao.getAllFlow()

    // TODO we shouldn't directly access an implementation
    fun supportedDomains(): List<String> = HomePresenterImpl.supportedDomains

    // TODO this should probably be provided from the same place as the supported domains list
    val nameForDomain = mapOf(
        "input_boolean" to app.applicationContext.getString(commonR.string.domain_input_boolean),
        "light" to app.applicationContext.getString(commonR.string.domain_light),
        "lock" to app.applicationContext.getString(commonR.string.domain_lock),
        "switch" to app.applicationContext.getString(commonR.string.domain_switch),
        "script" to app.applicationContext.getString(commonR.string.domain_script),
        "scene" to app.applicationContext.getString(commonR.string.domain_scene)
    )

    private fun loadEntities() {
        viewModelScope.launch {
            if (!homePresenter.isConnected()) {
                return@launch
            }
            shortcutEntities.addAll(homePresenter.getTileShortcuts())
            isHapticEnabled.value = homePresenter.getWearHapticFeedback()
            isToastEnabled.value = homePresenter.getWearToastConfirmation()

            homePresenter.getAreaRegistry()?.let {
                areaRegistry = it
                areas.addAll(it)
            }
            deviceRegistry = homePresenter.getDeviceRegistry()
            entityRegistry = homePresenter.getEntityRegistry()
            homePresenter.getEntities()?.forEach {
                if (supportedDomains().contains(it.entityId.split(".")[0])) {
                    entities[it.entityId] = it
                }
            }
            updateEntityDomains()

            homePresenter.getEntityUpdates()?.collect {
                if (supportedDomains().contains(it.entityId.split(".")[0])) {
                    entities[it.entityId] = it
                    updateEntityDomains()
                }
            }
        }
    }

    fun updateEntityDomains() {
        val entitiesList = entities.values.toList().sortedBy { it.entityId }
        val areasList = areaRegistry.orEmpty().sortedBy { it.name }
        val domainsList = entitiesList.map { it.entityId.split(".")[0] }.distinct()

        // Create a list with all areas + their entities
        areasList.forEach { area ->
            val entitiesInArea = mutableStateListOf<Entity<*>>()
            entitiesInArea.addAll(entitiesList.filter { getAreaForEntity(it.entityId)?.areaId == area.areaId })
            entitiesByArea[area.areaId]?.let {
                it.clear()
                it.addAll(entitiesInArea)
            } ?: run {
                entitiesByArea[area.areaId] = entitiesInArea
            }
        }
        // Quick check: are there any areas in the list that no longer exist?
        entitiesByArea.forEach {
            if (!areasList.any { item -> item.areaId == it.key }) {
                entitiesByArea.remove(it.key)
            }
        }

        // Create a list with all discovered domains + their entities
        domainsList.forEach { domain ->
            val entitiesInDomain = mutableStateListOf<Entity<*>>()
            entitiesInDomain.addAll(entitiesList.filter { it.entityId.split(".")[0] == domain })
            entitiesByDomain[domain]?.let {
                it.clear()
                it.addAll(entitiesInDomain)
            } ?: run {
                entitiesByDomain[domain] = entitiesInDomain
            }
        }
    }

    fun toggleEntity(entityId: String, state: String) {
        viewModelScope.launch {
            homePresenter.onEntityClicked(entityId, state)
        }
    }

    fun getAreaForEntity(entityId: String): AreaRegistryResponse? {
        val rEntity = entityRegistry?.firstOrNull { it.entityId == entityId }
        if (rEntity != null) {
            // By default, an entity should be considered to be in the same area as the associated device (if any)
            // This can be overridden for an individual entity, so check the entity registry first
            if (rEntity.areaId != null) {
                return areaRegistry?.firstOrNull { it.areaId == rEntity.areaId }
            } else if (rEntity.deviceId != null) {
                val rDevice = deviceRegistry?.firstOrNull { it.id == rEntity.deviceId }
                if (rDevice != null) {
                    return areaRegistry?.firstOrNull { it.areaId == rDevice.areaId }
                }
            }
        }
        return null
    }

    private fun getFavorites() {
        viewModelScope.launch {
            favorites()?.collect {
                favoriteEntityIds.clear()
                for (favorite in it) {
                    favoriteEntityIds.add(favorite.id)
                }
            }
        }
    }

    fun clearFavorites() {
        favoriteEntityIds.clear()
        favoritesDao.deleteAll()
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

    fun addFavorites(favorites: Favorites) {
        favoritesDao.add(favorites)
        updateFavoritePositions()
    }

    private fun updateFavorites(favorites: Favorites) {
        favoritesDao.update(favorites)
        updateFavoritePositions()
    }

    fun removeFavorites(id: String) {
        favoritesDao.delete(id)
        updateFavoritePositions()
    }

    private fun updateFavoritePositions() {
        var i = 1
        viewModelScope.launch {
            favoritesDao.getAll()?.forEach { favorites ->
                if (i != i)
                    updateFavorites(Favorites(favorites.id, i))
                i++
            }
        }
    }

    fun logout() {
        homePresenter.onLogoutClicked()
    }
}
