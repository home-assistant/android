package io.homeassistant.companion.android.settings.qs

import android.app.Application
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.graphics.drawable.DrawableCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.maltaisn.icondialog.data.Icon
import com.maltaisn.icondialog.pack.IconPack
import com.maltaisn.icondialog.pack.IconPackLoader
import com.maltaisn.iconpack.mdi.createMaterialDesignIconPack
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.integration.domain
import io.homeassistant.companion.android.database.qs.TileDao
import io.homeassistant.companion.android.database.qs.TileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class ManageTilesViewModel @Inject constructor(
    private val integrationUseCase: IntegrationRepository,
    private val tileDao: TileDao,
    application: Application
) : AndroidViewModel(application) {

    lateinit var iconPack: IconPack

    val slots = loadTileSlots(application.resources)

    var selectedTile by mutableStateOf(slots[0])
        private set

    var sortedEntities by mutableStateOf<List<Entity<*>>>(emptyList())
        private set
    var selectedIcon by mutableStateOf<Int?>(null)
        private set
    var selectedIconDrawable by mutableStateOf(AppCompatResources.getDrawable(application, R.drawable.ic_stat_ic_notification))
        private set
    var selectedTileId by mutableStateOf(0)
        private set
    var selectedEntityId by mutableStateOf("")
    var tileLabel by mutableStateOf("")
    var tileSubtitle by mutableStateOf<String?>(null)

    init {
        // Initialize fields based on the tile_1 TileEntity
        selectTile(0)

        viewModelScope.launch(Dispatchers.IO) {
            sortedEntities = integrationUseCase.getEntities().orEmpty()
                .filter { it.domain in ManageTilesFragment.validDomains }
        }

        viewModelScope.launch(Dispatchers.IO) {
            val loader = IconPackLoader(getApplication())
            iconPack = createMaterialDesignIconPack(loader)
            iconPack.loadDrawables(loader.drawableLoader)
            withContext(Dispatchers.Main) {
                // The icon pack might not have been initialized when the tile data was loaded
                selectTile(slots.indexOf(selectedTile))
            }
        }
    }

    fun selectTile(index: Int) {
        val tile = slots[index]
        selectedTile = tile
        viewModelScope.launch {
            tileDao.get(tile.id).also {
                selectedTileId = it?.id ?: 0
                it?.let { updateExistingTileFields(it) }
            }
        }
    }

    fun selectIcon(icon: Icon?) {
        selectedIcon = icon?.id
        selectedIconDrawable = icon?.drawable?.let { DrawableCompat.wrap(it) }
    }

    private fun updateExistingTileFields(currentTile: TileEntity) {
        tileLabel = currentTile.label
        tileSubtitle = currentTile.subtitle
        selectedEntityId = currentTile.entityId
        selectIcon(
            currentTile.iconId?.let {
                if (::iconPack.isInitialized) iconPack.getIcon(it)
                else null
            }
        )
    }

    fun addTile(tileData: TileEntity) {
        viewModelScope.launch {
            tileDao.add(tileData)
            Toast.makeText(getApplication(), R.string.tile_updated, Toast.LENGTH_SHORT).show()
        }
    }
}
