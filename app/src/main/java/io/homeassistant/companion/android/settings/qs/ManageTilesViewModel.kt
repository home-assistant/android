package io.homeassistant.companion.android.settings.qs

import android.app.Application
import android.app.StatusBarManager
import android.content.ComponentName
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Log
import androidx.compose.runtime.getValue
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
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.integration.domain
import io.homeassistant.companion.android.database.qs.TileDao
import io.homeassistant.companion.android.database.qs.TileEntity
import io.homeassistant.companion.android.database.qs.isSetup
import io.homeassistant.companion.android.qs.Tile10Service
import io.homeassistant.companion.android.qs.Tile11Service
import io.homeassistant.companion.android.qs.Tile12Service
import io.homeassistant.companion.android.qs.Tile1Service
import io.homeassistant.companion.android.qs.Tile2Service
import io.homeassistant.companion.android.qs.Tile3Service
import io.homeassistant.companion.android.qs.Tile4Service
import io.homeassistant.companion.android.qs.Tile5Service
import io.homeassistant.companion.android.qs.Tile6Service
import io.homeassistant.companion.android.qs.Tile7Service
import io.homeassistant.companion.android.qs.Tile8Service
import io.homeassistant.companion.android.qs.Tile9Service
import io.homeassistant.companion.android.util.icondialog.getIconByMdiName
import io.homeassistant.companion.android.util.icondialog.mdiName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import javax.inject.Inject
import io.homeassistant.companion.android.common.R as commonR

@HiltViewModel
class ManageTilesViewModel @Inject constructor(
    state: SavedStateHandle,
    private val integrationUseCase: IntegrationRepository,
    private val tileDao: TileDao,
    application: Application
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ManageTilesViewModel"
    }

    private val app = application

    val slots = loadTileSlots(application.resources)

    var selectedTile by mutableStateOf(slots[0])
        private set

    var sortedEntities by mutableStateOf<List<Entity<*>>>(emptyList())
        private set
    var selectedEntityId by mutableStateOf("")
    var tileLabel by mutableStateOf("")
    var tileSubtitle by mutableStateOf<String?>(null)
    var submitButtonLabel by mutableStateOf(commonR.string.tile_save)
        private set

    var selectedIcon: IIcon? = null
    private var selectedTileId = 0
    private var selectedTileAdded = false

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
            sortedEntities = integrationUseCase.getEntities().orEmpty()
                .filter { it.domain in ManageTilesFragment.validDomains }
        }
    }

    fun selectTile(index: Int) {
        val tile = slots[if (index == -1) 0 else index]
        selectedTile = tile
        viewModelScope.launch {
            tileDao.get(tile.id).also {
                selectedTileId = it?.id ?: 0
                selectedTileAdded = it?.added ?: false
                submitButtonLabel =
                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2 || it?.added == true) commonR.string.tile_save
                    else commonR.string.tile_add
                if (it?.isSetup == true) {
                    updateExistingTileFields(it)
                }
            }
        }
    }

    fun selectIcon(icon: IIcon?) {
        selectedIcon = icon
    }

    private fun updateExistingTileFields(currentTile: TileEntity) {
        tileLabel = currentTile.label
        tileSubtitle = currentTile.subtitle
        selectedEntityId = currentTile.entityId
        selectIcon(
            currentTile.iconName?.let { CommunityMaterial.getIconByMdiName(it) }
        )
    }

    fun addTile() {
        viewModelScope.launch {
            val tileData = TileEntity(
                id = selectedTileId,
                tileId = selectedTile.id,
                added = selectedTileAdded,
                iconName = selectedIcon?.mdiName,
                entityId = selectedEntityId,
                label = tileLabel,
                subtitle = tileSubtitle
            )
            tileDao.add(tileData)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !selectedTileAdded) {
                val statusBarManager = app.getSystemService<StatusBarManager>()
                val service = when (selectedTile.id) {
                    Tile2Service.TILE_ID -> Tile2Service::class.java
                    Tile3Service.TILE_ID -> Tile3Service::class.java
                    Tile4Service.TILE_ID -> Tile4Service::class.java
                    Tile5Service.TILE_ID -> Tile5Service::class.java
                    Tile6Service.TILE_ID -> Tile6Service::class.java
                    Tile7Service.TILE_ID -> Tile7Service::class.java
                    Tile8Service.TILE_ID -> Tile8Service::class.java
                    Tile9Service.TILE_ID -> Tile9Service::class.java
                    Tile10Service.TILE_ID -> Tile10Service::class.java
                    Tile11Service.TILE_ID -> Tile11Service::class.java
                    Tile12Service.TILE_ID -> Tile12Service::class.java
                    else -> Tile1Service::class.java
                }
                val icon = selectedIcon?.let {
                    val bitmap = IconicsDrawable(getApplication(), it).toBitmap()
                    Icon.createWithBitmap(bitmap)
                } ?: Icon.createWithResource(app, commonR.drawable.ic_stat_ic_notification)

                statusBarManager?.requestAddTileService(
                    ComponentName(app, service),
                    tileLabel,
                    icon,
                    Executors.newSingleThreadExecutor()
                ) { result ->
                    viewModelScope.launch {
                        Log.d(TAG, "Adding quick settings tile, system returned: $result")
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
