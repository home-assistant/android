package io.homeassistant.companion.android.settings.qs

import android.app.Application
import android.graphics.PorterDuff
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.graphics.drawable.DrawableCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.maltaisn.icondialog.pack.IconPack
import com.maltaisn.icondialog.pack.IconPackLoader
import com.maltaisn.iconpack.mdi.createMaterialDesignIconPack
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.HomeAssistantApplication
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.qs.TileEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ManageTilesViewModel @Inject constructor(
    private val integrationUseCase: IntegrationRepository,
    application: Application
) : AndroidViewModel(application) {

    private lateinit var iconPack: IconPack

    private val tileDao = AppDatabase.getInstance(application).tileDao()
    private fun tileDaoFlow(): Flow<List<TileEntity>>? = tileDao.getAllFlow()
    fun currentTile() = tileDao.get(selectedTile.value)
    var entities = mutableStateMapOf<String, Entity<*>>()
        private set
    private var tileList = mutableStateListOf<TileEntity>()
    var selectedTile = mutableStateOf("tile_1")
        private set
    var selectedTileName = mutableStateOf(application.getString(R.string.tile_1))
        private set
    var selectedIcon = mutableStateOf(currentTile()?.iconId)
        private set
    var drawableIcon = mutableStateOf(AppCompatResources.getDrawable(application, R.drawable.ic_stat_ic_notification))
        private set
    var selectedEntityId = mutableStateOf(currentTile()?.entityId ?: "")
        private set
    var tileLabel = mutableStateOf(currentTile()?.label)
        private set
    var tileSubtitle = mutableStateOf(currentTile()?.subtitle)
        private set

    init {
        viewModelScope.launch {
            integrationUseCase.getEntities()?.forEach {
                val split = it.entityId.split(".")
                if (split[0] in ManageTilesFragment.validDomains)
                    entities[it.entityId] = it
            }
        }
        tileFlow()
    }

    private fun tileFlow() {
        viewModelScope.launch {
            tileDaoFlow()?.collect {
                tileList.clear()
                it.forEach { tile ->
                    tileList.add(tile)
                }
            }
        }
    }

    fun updateExistingTileFields() {
        val currentTile = currentTile()!!
        tileLabel.value = currentTile.label
        tileSubtitle.value = currentTile.subtitle
        selectedEntityId.value = currentTile.entityId
        selectedIcon.value = currentTile.iconId
        val loader = IconPackLoader(getApplication())
        iconPack = createMaterialDesignIconPack(loader)
        iconPack.loadDrawables(loader.drawableLoader)
        val iconDrawable = selectedIcon.value?.let { iconPack.getIcon(it)?.drawable }
        if (iconDrawable != null) {
            val icon = DrawableCompat.wrap(iconDrawable)
            icon.setColorFilter(getApplication<HomeAssistantApplication>().resources.getColor(R.color.colorAccent), PorterDuff.Mode.SRC_IN)
            drawableIcon.value = icon
        }
    }
}
