package io.homeassistant.companion.android.qs

import android.content.Context
import android.content.Intent
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
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.database.qs.TileDao
import io.homeassistant.companion.android.database.qs.TileEntity
import io.homeassistant.companion.android.database.qs.isSetup
import io.homeassistant.companion.android.settings.SettingsActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        handleInject()
        getTile()?.let { tile ->
            mainScope.launch {
                setTileData(getTileId(), tile)
            }
        }
        MainScope().launch {
            setTileAdded(getTileId(), true)
        }
    }

    override fun onTileRemoved() {
        super.onTileRemoved()
        Log.d(TAG, "Tile: ${getTileId()} removed")
        handleInject()
        MainScope().launch {
            setTileAdded(getTileId(), false)
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

    private suspend fun setTileData(tileId: String, tile: Tile): Boolean {
        Log.d(TAG, "Attempting to set tile data for tile ID: $tileId")
        val context = applicationContext
        val tileData = tileDao.get(tileId)
        try {
            return if (tileData != null && tileData.isSetup) {
                tile.label = tileData.label
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = tileData.subtitle
                }
                if (tileData.entityId.split('.')[0] in toggleDomains.plus("lock")) {
                    try {
                        val state = withContext(Dispatchers.IO) { integrationUseCase.getEntity(tileData.entityId) }
                        tile.state = if (state?.state in validActiveStates) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
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
                if (tileData != null) {
                    Log.d(TAG, "Tile data found but not setup for tile ID: $tileId")
                } else {
                    Log.d(TAG, "No tile data found for tile ID: $tileId")
                }
                tile.state =
                    if (integrationUseCase.isRegistered()) Tile.STATE_INACTIVE
                    else Tile.STATE_UNAVAILABLE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = getString(commonR.string.tile_not_setup)
                }
                tile.updateTile()
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unable to set tile data for tile ID: $tileId", e)
            return false
        }
    }

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
            withContext(Dispatchers.IO) {
                try {
                    integrationUseCase.callService(
                        tileData!!.entityId.split(".")[0],
                        when (tileData.entityId.split(".")[0]) {
                            "button", "input_button" -> "press"
                            in toggleDomains -> "toggle"
                            "lock" -> {
                                val state = integrationUseCase.getEntity(tileData.entityId)
                                if (state?.state == "locked")
                                    "unlock"
                                else
                                    "lock"
                            }
                            else -> "turn_on"
                        },
                        hashMapOf("entity_id" to tileData.entityId)
                    )
                    Log.d(TAG, "Service call sent for tile ID: $tileId")
                } catch (e: Exception) {
                    Log.e(TAG, "Unable to call service for tile ID: $tileId", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            commonR.string.service_call_failure,
                            Toast.LENGTH_SHORT
                        )
                            .show()
                    }
                }
            }
            tile.state = Tile.STATE_INACTIVE
            tile.updateTile()
        } else {
            Log.d(TAG, "No tile data found for tile ID: $tileId")
            withContext(Dispatchers.Main) {
                startActivityAndCollapse(
                    SettingsActivity.newInstance(context).apply {
                        putExtra("fragment", "tiles/$tileId")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
                    }
                )
            }
        }
    }

    private suspend fun setTileAdded(tileId: String, added: Boolean) {
        tileDao.get(tileId)?.let {
            tileDao.add(it.copy(added = added))
        } ?: run {
            if (added) { // Store an empty tile in the database to track added
                tileDao.add(
                    TileEntity(
                        tileId = tileId,
                        added = added,
                        iconId = null,
                        entityId = "",
                        label = "",
                        subtitle = null
                    )
                )
            } // else if it doesn't exist and is removed we don't have to save anything
        }
    }

    companion object {
        private const val TAG = "TileExtensions"
        private var iconPack: IconPack? = null
        private val toggleDomains = listOf(
            "cover", "fan", "humidifier", "input_boolean", "light",
            "media_player", "remote", "siren", "switch"
        )
        private val validActiveStates = listOf("on", "open", "locked")

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

    private fun handleInject() {
        // onTileAdded/onTileRemoved might be called outside onCreate - onDestroy, which usually
        // handles injection. Because we need the DAO to save added/removed, inject it if required.
        if (!this::tileDao.isInitialized) {
            tileDao = EntryPointAccessors.fromApplication(
                this@TileExtensions.applicationContext,
                TileExtensionsEntryPoint::class.java
            ).tileDao()
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface TileExtensionsEntryPoint {
        fun tileDao(): TileDao
    }
}
