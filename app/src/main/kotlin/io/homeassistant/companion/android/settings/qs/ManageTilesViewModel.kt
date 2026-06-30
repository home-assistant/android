package io.homeassistant.companion.android.settings.qs

import android.annotation.SuppressLint
import android.app.Application
import android.app.StatusBarManager
import android.content.ComponentName
import android.graphics.drawable.Icon
import android.os.Build
import androidx.compose.runtime.Stable
import androidx.core.content.getSystemService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.getIcon
import io.homeassistant.companion.android.common.data.integration.isUsableInTile
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AreaRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.DeviceRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryResponse
import io.homeassistant.companion.android.common.util.SdkVersion
import io.homeassistant.companion.android.database.qs.TileDao
import io.homeassistant.companion.android.database.qs.TileEntity
import io.homeassistant.companion.android.database.qs.getHighestInUse
import io.homeassistant.companion.android.database.qs.isSetup
import io.homeassistant.companion.android.database.qs.numberedId
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.qs.Tile10Service
import io.homeassistant.companion.android.qs.Tile11Service
import io.homeassistant.companion.android.qs.Tile12Service
import io.homeassistant.companion.android.qs.Tile13Service
import io.homeassistant.companion.android.qs.Tile14Service
import io.homeassistant.companion.android.qs.Tile15Service
import io.homeassistant.companion.android.qs.Tile16Service
import io.homeassistant.companion.android.qs.Tile17Service
import io.homeassistant.companion.android.qs.Tile18Service
import io.homeassistant.companion.android.qs.Tile19Service
import io.homeassistant.companion.android.qs.Tile1Service
import io.homeassistant.companion.android.qs.Tile20Service
import io.homeassistant.companion.android.qs.Tile21Service
import io.homeassistant.companion.android.qs.Tile22Service
import io.homeassistant.companion.android.qs.Tile23Service
import io.homeassistant.companion.android.qs.Tile24Service
import io.homeassistant.companion.android.qs.Tile25Service
import io.homeassistant.companion.android.qs.Tile26Service
import io.homeassistant.companion.android.qs.Tile27Service
import io.homeassistant.companion.android.qs.Tile28Service
import io.homeassistant.companion.android.qs.Tile29Service
import io.homeassistant.companion.android.qs.Tile2Service
import io.homeassistant.companion.android.qs.Tile30Service
import io.homeassistant.companion.android.qs.Tile31Service
import io.homeassistant.companion.android.qs.Tile32Service
import io.homeassistant.companion.android.qs.Tile33Service
import io.homeassistant.companion.android.qs.Tile34Service
import io.homeassistant.companion.android.qs.Tile35Service
import io.homeassistant.companion.android.qs.Tile36Service
import io.homeassistant.companion.android.qs.Tile37Service
import io.homeassistant.companion.android.qs.Tile38Service
import io.homeassistant.companion.android.qs.Tile39Service
import io.homeassistant.companion.android.qs.Tile3Service
import io.homeassistant.companion.android.qs.Tile40Service
import io.homeassistant.companion.android.qs.Tile4Service
import io.homeassistant.companion.android.qs.Tile5Service
import io.homeassistant.companion.android.qs.Tile6Service
import io.homeassistant.companion.android.qs.Tile7Service
import io.homeassistant.companion.android.qs.Tile8Service
import io.homeassistant.companion.android.qs.Tile9Service
import io.homeassistant.companion.android.util.icondialog.getIconByMdiName
import io.homeassistant.companion.android.util.icondialog.mdiName
import java.util.concurrent.Executors
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

@Stable
internal data class ManageTilesState(
    val tileSlots: List<TileSlot>,
    val selectedTileId: String = "",
    val servers: List<Server> = emptyList(),
    val sortedEntities: List<Entity> = emptyList(),
    val entityRegistry: List<EntityRegistryResponse> = emptyList(),
    val deviceRegistry: List<DeviceRegistryResponse> = emptyList(),
    val areaRegistry: List<AreaRegistryResponse> = emptyList(),
    val selectedServerId: Int = ServerManager.SERVER_ID_ACTIVE,
    val selectedIconId: String? = null,
    val selectedIcon: IIcon? = null,
    val selectedEntityId: String = "",
    val tileLabel: String = "",
    val tileSubtitle: String? = null,
    val submitButtonLabel: Int = commonR.string.tile_save,
    val selectedShouldVibrate: Boolean = false,
    val tileAuthRequired: Boolean = false,
    val tileSlotsDropdownItems: List<HADropdownItem<String>> = tileSlots.map {
        HADropdownItem(key = it.id, label = it.name)
    },
    val serversDropdownItems: List<HADropdownItem<Int>> = servers.map {
        HADropdownItem(key = it.id, label = it.friendlyName)
    },
) {
    val showSubtitle = SdkVersion.isAtLeast(Build.VERSION_CODES.Q)

    val showServerSelector get() = servers.size > 1 ||
        servers.none { server -> server.id == selectedServerId }

    val showResetIcon get() = selectedIconId != null && selectedEntityId.isNotBlank()

    val submitEnabled get() = tileLabel.isNotBlank() &&
        selectedServerId in servers.map { it.id } &&
        selectedEntityId in sortedEntities.map { it.entityId }
}

@HiltViewModel
internal class ManageTilesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val serverManager: ServerManager,
    private val tileDao: TileDao,
    application: Application,
) : AndroidViewModel(application) {

    companion object {
        @SuppressLint("InlinedApi", "NewApi")
        val idToTileService = mapOf(
            Tile1Service.TILE_ID to Tile1Service::class.java,
            Tile2Service.TILE_ID to Tile2Service::class.java,
            Tile3Service.TILE_ID to Tile3Service::class.java,
            Tile4Service.TILE_ID to Tile4Service::class.java,
            Tile5Service.TILE_ID to Tile5Service::class.java,
            Tile6Service.TILE_ID to Tile6Service::class.java,
            Tile7Service.TILE_ID to Tile7Service::class.java,
            Tile8Service.TILE_ID to Tile8Service::class.java,
            Tile9Service.TILE_ID to Tile9Service::class.java,
            Tile10Service.TILE_ID to Tile10Service::class.java,
            Tile11Service.TILE_ID to Tile11Service::class.java,
            Tile12Service.TILE_ID to Tile12Service::class.java,
            Tile13Service.TILE_ID to Tile13Service::class.java,
            Tile14Service.TILE_ID to Tile14Service::class.java,
            Tile15Service.TILE_ID to Tile15Service::class.java,
            Tile16Service.TILE_ID to Tile16Service::class.java,
            Tile17Service.TILE_ID to Tile17Service::class.java,
            Tile18Service.TILE_ID to Tile18Service::class.java,
            Tile19Service.TILE_ID to Tile19Service::class.java,
            Tile20Service.TILE_ID to Tile20Service::class.java,
            Tile21Service.TILE_ID to Tile21Service::class.java,
            Tile22Service.TILE_ID to Tile22Service::class.java,
            Tile23Service.TILE_ID to Tile23Service::class.java,
            Tile24Service.TILE_ID to Tile24Service::class.java,
            Tile25Service.TILE_ID to Tile25Service::class.java,
            Tile26Service.TILE_ID to Tile26Service::class.java,
            Tile27Service.TILE_ID to Tile27Service::class.java,
            Tile28Service.TILE_ID to Tile28Service::class.java,
            Tile29Service.TILE_ID to Tile29Service::class.java,
            Tile30Service.TILE_ID to Tile30Service::class.java,
            Tile31Service.TILE_ID to Tile31Service::class.java,
            Tile32Service.TILE_ID to Tile32Service::class.java,
            Tile33Service.TILE_ID to Tile33Service::class.java,
            Tile34Service.TILE_ID to Tile34Service::class.java,
            Tile35Service.TILE_ID to Tile35Service::class.java,
            Tile36Service.TILE_ID to Tile36Service::class.java,
            Tile37Service.TILE_ID to Tile37Service::class.java,
            Tile38Service.TILE_ID to Tile38Service::class.java,
            Tile39Service.TILE_ID to Tile39Service::class.java,
            Tile40Service.TILE_ID to Tile40Service::class.java,
        )
    }

    private val app = application

    val slots = loadTileSlots(application.resources)

    private val _state = MutableStateFlow(
        ManageTilesState(
            selectedTileId = slots[0].id,
            tileSlots = slots,
        ),
    )
    internal val state: StateFlow<ManageTilesState> = _state.asStateFlow()

    private var selectedTileId = 0
    private var selectedTileAdded = false

    private val entities = mutableMapOf<Int, List<Entity>>()
    private val entityRegistries = mutableMapOf<Int, List<EntityRegistryResponse>>()
    private val deviceRegistries = mutableMapOf<Int, List<DeviceRegistryResponse>>()
    private val areaRegistries = mutableMapOf<Int, List<AreaRegistryResponse>>()

    private val _tileInfoSnackbar = MutableSharedFlow<Int>(replay = 1)
    var tileInfoSnackbar = _tileInfoSnackbar.asSharedFlow()

    init {
        // Initialize fields based on the tile_1 TileEntity
        savedStateHandle.get<String>("id")?.let { id ->
            selectTile(id)
            viewModelScope.launch {
                // A deeplink only happens when tapping on a tile that hasn't been setup
                _tileInfoSnackbar.emit(commonR.string.tile_data_missing)
            }
        } ?: run {
            selectTile()
        }

        viewModelScope.launch(Dispatchers.Default) {
            val loadedServers = serverManager.servers()
            _state.update {
                it.copy(
                    servers = loadedServers,
                    serversDropdownItems = loadedServers.map { server ->
                        HADropdownItem(key = server.id, label = server.friendlyName)
                    },
                )
            }
            loadedServers.map { server ->
                val serverId = server.id
                async {
                    launch { entities[serverId] = loadEntitiesForServer(serverId) }
                    launch { entityRegistries[serverId] = loadEntityRegistry(serverId) }
                    launch { deviceRegistries[serverId] = loadDeviceRegistry(serverId) }
                    launch { areaRegistries[serverId] = loadAreaRegistry(serverId) }
                }
            }.awaitAll()
            // The entities list might not have been loaded when the tile data was loaded
            selectTile(_state.value.selectedTileId)
        }
    }

    fun selectTile(id: String? = null) {
        viewModelScope.launch {
            val tile = slots.find { it.id == id } ?: slots.first()
            val entity = tileDao.get(tile.id)
            selectedTileId = entity?.id ?: 0
            selectedTileAdded = entity?.added ?: false
            val serverId = if (entity?.serverId == null || entity.serverId == 0) {
                serverManager.getServer()?.id ?: 0
            } else {
                entity.serverId
            }
            _state.update {
                it.copy(
                    selectedTileId = tile.id,
                    selectedServerId = serverId,
                    selectedShouldVibrate = entity?.shouldVibrate ?: false,
                    tileAuthRequired = entity?.authRequired ?: false,
                    submitButtonLabel = if (!SdkVersion.isAtLeast(Build.VERSION_CODES.TIRAMISU) ||
                        entity?.added == true
                    ) {
                        commonR.string.tile_save
                    } else {
                        commonR.string.tile_add
                    },
                )
            }
            loadEntities(serverId)
            if (entity?.isSetup == true) {
                updateExistingTileFields(entity)
            }
        }
    }

    fun selectServerId(serverId: Int) {
        val current = _state.value
        val resetEntity =
            serverId != current.selectedServerId &&
                entities[serverId]?.none { it.entityId == current.selectedEntityId } == true
        _state.update { it.copy(selectedServerId = serverId) }
        loadEntities(serverId)
        selectEntityId(if (resetEntity) "" else current.selectedEntityId)
    }

    private fun loadEntities(serverId: Int) {
        _state.update {
            it.copy(
                sortedEntities = entities[serverId] ?: emptyList(),
                entityRegistry = entityRegistries[serverId] ?: emptyList(),
                deviceRegistry = deviceRegistries[serverId] ?: emptyList(),
                areaRegistry = areaRegistries[serverId] ?: emptyList(),
            )
        }
    }

    fun selectEntityId(entityId: String) {
        _state.update { it.copy(selectedEntityId = entityId) }
        if (_state.value.selectedIconId == null) selectIcon(null) // trigger drawable update
    }

    fun selectIcon(icon: IIcon?) {
        val current = _state.value
        val resolvedIcon =
            icon ?: current.sortedEntities.firstOrNull { it.entityId == current.selectedEntityId }?.getIcon(app)
        _state.update { it.copy(selectedIconId = icon?.mdiName, selectedIcon = resolvedIcon) }
    }

    private fun updateExistingTileFields(currentTile: TileEntity) {
        _state.update {
            it.copy(
                tileLabel = currentTile.label,
                tileSubtitle = currentTile.subtitle,
                selectedEntityId = currentTile.entityId,
                selectedShouldVibrate = currentTile.shouldVibrate,
                tileAuthRequired = currentTile.authRequired,
            )
        }
        selectIcon(currentTile.iconName?.let { CommunityMaterial.getIconByMdiName(it) })
    }

    fun addTile() {
        viewModelScope.launch(Dispatchers.IO) {
            val current = _state.value
            val tileData = TileEntity(
                id = selectedTileId,
                tileId = current.selectedTileId,
                serverId = current.selectedServerId,
                added = selectedTileAdded,
                iconName = current.selectedIconId,
                entityId = current.selectedEntityId,
                label = current.tileLabel,
                subtitle = current.tileSubtitle,
                shouldVibrate = current.selectedShouldVibrate,
                authRequired = current.tileAuthRequired,
            )
            tileDao.add(tileData)

            val highestInUse = tileDao.getHighestInUse()?.numberedId ?: 0
            updateActiveTileServices(highestInUse, app)

            if (SdkVersion.isAtLeast(Build.VERSION_CODES.TIRAMISU) && !selectedTileAdded) {
                val statusBarManager = app.getSystemService<StatusBarManager>()
                val service = idToTileService[current.selectedTileId] ?: Tile1Service::class.java
                val icon = current.selectedIcon?.let {
                    val bitmap = IconicsDrawable(getApplication(), it).toBitmap()
                    Icon.createWithBitmap(bitmap)
                } ?: Icon.createWithResource(app, commonR.drawable.ic_stat_ic_notification)

                statusBarManager?.requestAddTileService(
                    ComponentName(app, service),
                    current.tileLabel,
                    icon,
                    Executors.newSingleThreadExecutor(),
                ) { result ->
                    viewModelScope.launch {
                        Timber.d("Adding quick settings tile, system returned: $result")
                        if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
                            result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED
                        ) {
                            _tileInfoSnackbar.emit(commonR.string.tile_added)
                            selectedTileAdded = true
                            _state.update { it.copy(submitButtonLabel = commonR.string.tile_save) }
                        } else { // Silently ignore error, database was still updated
                            _tileInfoSnackbar.emit(commonR.string.tile_updated)
                        }
                    }
                }
            } else {
                _tileInfoSnackbar.emit(commonR.string.tile_updated)
            }
        }
    }

    fun setTileLabel(value: String) = _state.update { it.copy(tileLabel = value) }

    fun setTileSubtitle(value: String) = _state.update { it.copy(tileSubtitle = value) }

    fun setShouldVibrate(value: Boolean) = _state.update { it.copy(selectedShouldVibrate = value) }

    fun setAuthRequired(value: Boolean) = _state.update { it.copy(tileAuthRequired = value) }

    private suspend fun loadEntitiesForServer(serverId: Int): List<Entity> = try {
        serverManager.integrationRepository(serverId).getEntities().orEmpty()
            .filter(Entity::isUsableInTile)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.e(e, "Couldn't load entities for server")
        emptyList()
    }

    private suspend fun loadEntityRegistry(serverId: Int): List<EntityRegistryResponse> = try {
        serverManager.webSocketRepository(serverId).getEntityRegistry().orEmpty()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.e(e, "Couldn't load entity registry for server")
        emptyList()
    }

    private suspend fun loadDeviceRegistry(serverId: Int): List<DeviceRegistryResponse> = try {
        serverManager.webSocketRepository(serverId).getDeviceRegistry().orEmpty()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.e(e, "Couldn't load device registry for server")
        emptyList()
    }

    private suspend fun loadAreaRegistry(serverId: Int): List<AreaRegistryResponse> = try {
        serverManager.webSocketRepository(serverId).getAreaRegistry().orEmpty()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.e(e, "Couldn't load area registry for server")
        emptyList()
    }
}
