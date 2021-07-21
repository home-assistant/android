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
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.database.AppDatabase
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@RequiresApi(Build.VERSION_CODES.N)
abstract class TileExtensions : TileService() {

    abstract fun getTile(): Tile?

    abstract fun getTileId(): String

    @Inject
    lateinit var integrationUseCase: IntegrationRepository

    override fun onClick() {
        super.onClick()
        DaggerTileComponent.builder()
            .appComponent((applicationContext as GraphComponentAccessor).appComponent)
            .build()
            .inject(this)
        if (getTile() != null) {
            setTileData(applicationContext, getTileId(), getTile()!!)
            tileClicked(applicationContext, getTileId(), getTile()!!, integrationUseCase)
        }
    }

    override fun onTileAdded() {
        super.onTileAdded()
        if (getTile() != null)
            setTileData(applicationContext, getTileId(), getTile()!!)
    }

    override fun onStartListening() {
        super.onStartListening()
        if (getTile() != null)
            setTileData(applicationContext, getTileId(), getTile()!!)
    }

    companion object {
        private const val TAG = "TileExtensions"
        private var iconPack: IconPack? = null

        @RequiresApi(Build.VERSION_CODES.N)
        fun setTileData(context: Context, tileId: String, tile: Tile): Boolean {
            val tileDao = AppDatabase.getInstance(context).tileDao()
            val tileData = tileDao.get(tileId)
            try {
                return if (tileData != null) {
                    tile.label = tileData.label
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        tile.subtitle = tileData.subtitle
                    }
                    tile.state = Tile.STATE_INACTIVE
                    if (tileData.iconId != null) {
                        val icon = getTileIcon(tileData.iconId, context)
                        tile.icon = Icon.createWithBitmap(icon)
                    }
                    tile.updateTile()
                    true
                } else {
                    tile.state = Tile.STATE_UNAVAILABLE
                    tile.updateTile()
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unable to set tile data", e)
                return false
            }
        }

        @RequiresApi(Build.VERSION_CODES.N)
        fun tileClicked(
            context: Context,
            tileId: String,
            tile: Tile,
            integrationUseCase: IntegrationRepository
        ) {

            val tileDao = AppDatabase.getInstance(context).tileDao()
            val tileData = tileDao.get(tileId)
            val hasTile = setTileData(context, tileId, tile)
            if (hasTile) {
                tile.state = Tile.STATE_ACTIVE
                tile.updateTile()
                val tileService = tileData?.entityId?.split(".")
                runBlocking {
                    try {
                        integrationUseCase.callService(
                            tileService!![0],
                            tileService[1],
                            hashMapOf()
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Unable to call service", e)
                        Toast.makeText(context, R.string.service_call_failure, Toast.LENGTH_SHORT)
                            .show()
                    }
                }
                tile.state = Tile.STATE_INACTIVE
                tile.updateTile()
            } else {
                tile.state = Tile.STATE_UNAVAILABLE
                tile.updateTile()
                Toast.makeText(context, R.string.tile_data_missing, Toast.LENGTH_SHORT).show()
            }
        }

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
