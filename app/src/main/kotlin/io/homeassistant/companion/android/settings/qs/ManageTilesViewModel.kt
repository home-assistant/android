package io.homeassistant.companion.android.settings.qs

import android.annotation.SuppressLint
import android.app.Application
import android.app.StatusBarManager
import android.content.ComponentName
import android.graphics.drawable.Icon
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.getSystemService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.getIcon
import io.homeassistant.companion.android.common.data.integration.isUsableInTile
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.qs.TileDao
import io.homeassistant.companion.android.database.qs.TileEntity
import io.homeassistant.companion.android.database.qs.getHighestInUse
import io.homeassistant.companion.android.database.qs.isSetup
import io.homeassistant.companion.android.database.qs.numberedId
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@HiltViewModel
class ManageTilesViewModel @Inject constructor(
    state: SavedStateHandle,
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

    var selectedTile by mutableStateOf(slots[0])
        private set

    var servers by mutableStateOf(serverManager.defaultServers)
        private set
    var sortedEntities by mutableStateOf<List<Entity>>(emptyList())
        private set
    var selectedServerId by mutableIntStateOf(ServerManager.SERVER_ID_ACTIVE)
        private set
    var selectedIconId by mutableStateOf<String?>(null)
        private set
    var selectedEntityId by mutableStateOf("")
    var tileLabel by mutableStateOf("")
    var tileSubtitle by mutableStateOf<String?>(null)
    var submitButtonLabel by mutableIntStateOf(commonR.string.tile_save)
        private set
    var selectedShouldVibrate by mutableStateOf(false)
    var tileAuthRequired by mutableStateOf(false)

    var selectedIcon: IIcon? = null
    private var selectedTileId = 0
    private var selectedTileAdded = false

    private val entities = mutableMapOf<Int, List<Entity>>()

    private val _tileInfoSnackbar = MutableSharedFlow<Int>(replay = 1)
    var tileInfoSnackbar = _tileInfoSnackbar.asSharedFlow()

    init {
        // Initialize fields based on the tile_1 TileEntity
        state.get<String>("id")?.let { id ->
            selectTile(slots.indexOfFirst { it.id == id })
            viewModelScope.launch {
                // A deeplink only happens when tapping on a tile that hasn't been setup
                _tileInfoSnackbar.emit(commonR.string.tile_data_missing)
            }
        } ?: run {
            selectTile(0)
        }

        viewModelScope.launch(Dispatchers.IO) {
            serverManager.defaultServers.map {
                async {
                    entities[it.id] = try {
                        serverManager.integrationRepository(it.id).getEntities().orEmpty()
                            .filter(Entity::isUsableInTile)
                    } catch (e: Exception) {
                        Timber.e(e, "Couldn't load entities for server")
                        emptyList()
                    }
                }
            }.awaitAll()
            withContext(Dispatchers.Main) {
                // The entities list might not have been loaded when the tile data was loaded
                selectTile(slots.indexOf(selectedTile))
            }
        }
    }

    fun selectTile(index: Int) {
        val tile = slots[if (index == -1) 0 else index]
        selectedTile = tile
        viewModelScope.launch {
            tileDao.get(tile.id).also {
                selectedTileId = it?.id ?: 0
                selectedTileAdded = it?.added ?: false
                selectedServerId =
                    if (it?.serverId == null || it.serverId == 0) {
                        serverManager.getServer()?.id ?: 0
                    } else {
                        it.serverId
                    }
                selectedShouldVibrate = it?.shouldVibrate ?: false
                tileAuthRequired = it?.authRequired ?: false
                submitButtonLabel =
                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2 || it?.added == true) {
                        commonR.string.tile_save
                    } else {
                        commonR.string.tile_add
                    }
                loadEntities(selectedServerId)
                if (it?.isSetup == true) {
                    updateExistingTileFields(it)
                }
            }
        }
    }

    fun selectServerId(serverId: Int) {
        val resetEntity =
            serverId != selectedServerId && entities[serverId]?.none { it.entityId == selectedEntityId } == true
        selectedServerId = serverId
        loadEntities(serverId)
        selectEntityId(if (resetEntity) "" else selectedEntityId)
    }

    private fun loadEntities(serverId: Int) {
        sortedEntities = entities[serverId] ?: emptyList()
    }

    fun selectEntityId(entityId: String) {
        selectedEntityId = entityId
        if (selectedIconId == null) selectIcon(null) // trigger drawable update
    }

    fun selectIcon(icon: IIcon?) {
        selectedIconId = icon?.mdiName
        selectedIcon = icon ?: sortedEntities.firstOrNull { it.entityId == selectedEntityId }?.getIcon(app)
    }

    private fun updateExistingTileFields(currentTile: TileEntity) {
        tileLabel = currentTile.label
        tileSubtitle = currentTile.subtitle
        selectedEntityId = currentTile.entityId
        selectedShouldVibrate = currentTile.shouldVibrate
        tileAuthRequired = currentTile.authRequired
        selectIcon(
            currentTile.iconName?.let { CommunityMaterial.getIconByMdiName(it) },
        )
    }

    fun addTile() {
        viewModelScope.launch {
            val tileData = TileEntity(
                id = selectedTileId,
                tileId = selectedTile.id,
                serverId = selectedServerId,
                added = selectedTileAdded,
                iconName = selectedIconId,
                entityId = selectedEntityId,
                label = tileLabel,
                subtitle = tileSubtitle,
                shouldVibrate = selectedShouldVibrate,
                authRequired = tileAuthRequired,
            )
            tileDao.add(tileData)

            val highestInUse = tileDao.getHighestInUse()?.numberedId ?: 0
            updateActiveTileServices(highestInUse, app)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !selectedTileAdded) {
                val statusBarManager = app.getSystemService<StatusBarManager>()
                val service = idToTileService[selectedTile.id] ?: Tile1Service::class.java
                val icon = selectedIcon?.let {
                    val bitmap = IconicsDrawable(getApplication(), it).toBitmap()
                    Icon.createWithBitmap(bitmap)
                } ?: Icon.createWithResource(app, commonR.drawable.ic_stat_ic_notification)

                statusBarManager?.requestAddTileService(
                    ComponentName(app, service),
                    tileLabel,
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
                            submitButtonLabel = commonR.string.tile_save
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
}
