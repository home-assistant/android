package io.homeassistant.companion.android.qs

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.drawable.toBitmap
import com.maltaisn.icondialog.pack.IconPack
import com.maltaisn.icondialog.pack.IconPackLoader
import com.maltaisn.iconpack.mdi.createMaterialDesignIconPack
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.database.qs.TileDao
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import io.homeassistant.companion.android.common.R as commonR

@RequiresApi(Build.VERSION_CODES.N)
@AndroidEntryPoint
abstract class TileExtensions : TileService() {

    abstract fun getTile(): Tile?

    abstract fun getTileId(): String

    @Inject
    lateinit var integrationUseCase: IntegrationRepository

    @Inject
    lateinit var tileDao: TileDao

    private val mainScope = MainScope()

    override fun onClick() {
        super.onClick()
        getTile()?.let { tile ->
            mainScope.launch {
                setTileData(getTileId(), tile)
                tileClicked(getTileId(), tile)
            }
        }
    }

    override fun onTileAdded() {
        super.onTileAdded()
        Log.d(TAG, "Tile: ${getTileId()} added")
        getTile()?.let { tile ->
            mainScope.launch {
                setTileData(getTileId(), tile)
            }
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        Log.d(TAG, "Tile: ${getTileId()} is in view")
        getTile()?.let { tile ->
            mainScope.launch {
                setTileData(getTileId(), tile)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mainScope.cancel()
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private suspend fun setTileData(tileId: String, tile: Tile): Boolean {
        Log.d(TAG, "Attempting to set tile data for tile ID: $tileId")
        val context = applicationContext
        val tileData = tileDao.get(tileId)
        try {
            return if (tileData != null) {
                tile.label = tileData.label
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = tileData.subtitle
                }
                if (tileData.entityId.split('.')[0] in toggleDomains) {
                    try {
                        val state = runBlocking { integrationUseCase.getEntity(tileData.entityId) }
                        tile.state = if (state?.state == "on" || state?.state == "open") Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                    } catch (e: Exception) {
                        Log.e(TAG, "Unable to set state for tile", e)
                        tile.state = Tile.STATE_UNAVAILABLE
                    }
                } else
                    tile.state = Tile.STATE_INACTIVE
                val iconId = tileData.iconId
                if (iconId != null) {
                    val icon = getTileIcon(iconId, context)
                    tile.icon = Icon.createWithBitmap(icon)
                }
                Log.d(TAG, "Tile data set for tile ID: $tileId")
                tile.updateTile()
                true
            } else {
                Log.d(TAG, "No tile data found for tile ID: $tileId")
                tile.state = Tile.STATE_UNAVAILABLE
                tile.updateTile()
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unable to set tile data for tile ID: $tileId", e)
            return false
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private suspend fun tileClicked(
        tileId: String,
        tile: Tile
    ) {
        Log.d(TAG, "Click detected for tile ID: $tileId")
        val context = applicationContext
        val tileData = tileDao.get(tileId)
        val hasTile = setTileData(tileId, tile)
        if (hasTile) {
            tile.state = Tile.STATE_ACTIVE
            tile.updateTile()
            runBlocking {
                try {
                    integrationUseCase.callService(
                        tileData?.entityId?.split(".")!![0],
                        when (tileData.entityId.split(".")[0]) {
                            "button", "input_button" -> "press"
                            in toggleDomains -> "toggle"
                            else -> "turn_on"
                        },
                        hashMapOf("entity_id" to tileData.entityId)
                    )
                    Log.d(TAG, "Service call sent for tile ID: $tileId")
                } catch (e: Exception) {
                    Log.e(TAG, "Unable to call service for tile ID: $tileId", e)
                    Toast.makeText(context, commonR.string.service_call_failure, Toast.LENGTH_SHORT)
                        .show()
                }
            }
            tile.state = Tile.STATE_INACTIVE
            tile.updateTile()
        } else {
            tile.state = Tile.STATE_UNAVAILABLE
            tile.updateTile()
            Log.d(TAG, "No tile data found for tile ID: $tileId")
            Toast.makeText(context, commonR.string.tile_data_missing, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val TAG = "TileExtensions"
        private var iconPack: IconPack? = null
        private val toggleDomains = listOf(
            "cover", "fan", "humidifier", "input_boolean", "light",
            "media_player", "remote", "siren", "switch"
        )

        private fun getTileIcon(tileIconId: Int, context: Context): Bitmap? {
            // Create an icon pack and load all drawables.
            if (iconPack == null) {
                val loader = IconPackLoader(context)
                iconPack = createMaterialDesignIconPack(loader)
                iconPack!!.loadDrawables(loader.drawableLoader)
            }

            val iconDrawable = iconPack?.icons?.get(tileIconId)?.drawable
            if (iconDrawable != null) {
                return DrawableCompat.wrap(iconDrawable).toBitmap()
            }
            return null
        }
    }
}
