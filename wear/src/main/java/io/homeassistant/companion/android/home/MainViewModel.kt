package io.homeassistant.companion.android.home

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

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
    var entitiesNotInAreas = mutableStateMapOf<String, Entity<*>>()
        private set
    var favoriteEntityIds = mutableStateListOf<String>()
        private set
    var shortcutEntities = mutableStateListOf<SimplifiedEntity>()
        private set
    var areas = mutableListOf<AreaRegistryResponse>()
        private set
    var areaEntities = mutableStateMapOf<String, List<Entity<*>>>()
        private set
    var scenes = mutableStateListOf<Entity<*>>()
        private set
    var scripts = mutableStateListOf<Entity<*>>()
        private set
    var lights = mutableStateListOf<Entity<*>>()
        private set
    var locks = mutableStateListOf<Entity<*>>()
        private set
    var inputBooleans = mutableStateListOf<Entity<*>>()
        private set
    var switches = mutableStateListOf<Entity<*>>()
        private set

    // Content of EntityListView
    var entityLists = mutableStateMapOf<String, List<Entity<*>>>()

    // settings
    var isHapticEnabled = mutableStateOf(false)
        private set
    var isToastEnabled = mutableStateOf(false)
        private set

    private fun favorites(): Flow<List<Favorites>>? = favoritesDao.getAllFlow()

    private fun loadEntities() {
        viewModelScope.launch {
            if (!homePresenter.isConnected()) {
                return@launch
            }
            shortcutEntities.addAll(homePresenter.getTileShortcuts())
            isHapticEnabled.value = homePresenter.getWearHapticFeedback()
            isToastEnabled.value = homePresenter.getWearToastConfirmation()

            areaRegistry = homePresenter.getAreaRegistry()
            deviceRegistry = homePresenter.getDeviceRegistry()
            entityRegistry = homePresenter.getEntityRegistry()
            homePresenter.getEntities()?.forEach {
                entities[it.entityId] = it
            }
            updateEntityDomains()

            homePresenter.getEntityUpdates()?.collect {
                entities[it.entityId] = it
                updateEntityDomains()
            }
        }
    }

    fun updateEntityDomains() {
        val entitiesList = entities.values.toList().sortedBy { it.entityId }
        val areaForEntity = mutableMapOf<String, AreaRegistryResponse?>()
        entitiesList.forEach {
            areaForEntity[it.entityId] = getAreaForEntity(it.entityId)
        }

        val areasList = mutableListOf<AreaRegistryResponse>()
        val areaEntitiesList = mutableMapOf<String, MutableList<Entity<*>>>()
        entitiesNotInAreas.clear()
        entitiesList.forEach { entity ->
            areaForEntity[entity.entityId]?.let {
                if (!areasList.contains(it)) {
                    areasList.add(it)
                }
                if (areaEntitiesList[it.name] == null) {
                    areaEntitiesList[it.name] = mutableListOf(entity)
                } else {
                    areaEntitiesList[it.name]!!.add(entity)
                }
            } ?: run {
                entitiesNotInAreas[entity.entityId] = entity
            }
        }
        areas.clear()
        areas.addAll(areasList.sortedBy { it.name })
        areaEntities.clear()
        areaEntitiesList.forEach {
            areaEntities[it.key] = it.value
        }
        scenes.clear()
        scenes.addAll(entitiesList.filter { areaForEntity[it.entityId] == null && it.entityId.split(".")[0] == "scene" })
        scripts.clear()
        scripts.addAll(entitiesList.filter { areaForEntity[it.entityId] == null && it.entityId.split(".")[0] == "script" })
        lights.clear()
        lights.addAll(entitiesList.filter { areaForEntity[it.entityId] == null && it.entityId.split(".")[0] == "light" })
        locks.clear()
        locks.addAll(entitiesList.filter { areaForEntity[it.entityId] == null && it.entityId.split(".")[0] == "lock" })
        inputBooleans.clear()
        inputBooleans.addAll(entitiesList.filter { areaForEntity[it.entityId] == null && it.entityId.split(".")[0] == "input_boolean" })
        switches.clear()
        switches.addAll(entitiesList.filter { areaForEntity[it.entityId] == null && it.entityId.split(".")[0] == "switch" })
    }

    fun toggleEntity(entityId: String, state: String) {
        viewModelScope.launch {
            homePresenter.onEntityClicked(entityId, state)
        }
    }

    private fun getAreaForEntity(entityId: String): AreaRegistryResponse? {
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
