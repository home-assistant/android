package io.homeassistant.companion.android.qs

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.getSystemService
import androidx.core.service.quicksettings.PendingIntentActivityWrapper
import androidx.core.service.quicksettings.TileServiceCompat
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import com.mikepenz.iconics.utils.sizeDp
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.EntityExt
import io.homeassistant.companion.android.common.data.integration.getIcon
import io.homeassistant.companion.android.common.data.integration.isActive
import io.homeassistant.companion.android.common.data.integration.onEntityPressedWithoutState
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.SdkVersion
import io.homeassistant.companion.android.database.qs.TileDao
import io.homeassistant.companion.android.database.qs.TileEntity
import io.homeassistant.companion.android.database.qs.getHighestInUse
import io.homeassistant.companion.android.database.qs.isSetup
import io.homeassistant.companion.android.database.qs.numberedId
import io.homeassistant.companion.android.settings.SettingsActivity
import io.homeassistant.companion.android.settings.qs.TileId
import io.homeassistant.companion.android.settings.qs.updateActiveTileServices
import io.homeassistant.companion.android.util.icondialog.getIconByMdiName
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import timber.log.Timber

@RequiresApi(Build.VERSION_CODES.N)
@AndroidEntryPoint
internal abstract class TileExtensions : TileService() {

    abstract val tileId: TileId
    abstract fun getTile(): Tile?

    @Inject
    lateinit var serverManager: ServerManager

    @Inject
    lateinit var tileDao: TileDao

    private val mainScope = MainScope()

    private var stateUpdateJob: Job? = null

    override fun onClick() {
        super.onClick()
        getTile()?.let { tile ->
            mainScope.launch {
                setTileData(tile)
                tileClicked(tile, isUnlock = false)
            }
        }
    }

    override fun onTileAdded() {
        super.onTileAdded()
        Timber.d("Tile: $tileId added")
        handleInject()
        getTile()?.let { tile ->
            mainScope.launch {
                setTileData(tile)
            }
        }
        mainScope.launch {
            setTileAdded(added = true)
        }
    }

    override fun onTileRemoved() {
        super.onTileRemoved()
        Timber.d("Tile: $tileId removed")
        handleInject()
        runBlocking {
            setTileAdded(added = false)
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        Timber.d("Tile: $tileId is in view")
        getTile()?.let { tile ->
            mainScope.launch {
                setTileData(tile)
            }
            stateUpdateJob = mainScope.launch {
                val tileData = tileDao.get(tileId.value)
                if (tileData != null &&
                    tileData.isSetup &&
                    tileData.entityId.split('.')[0] in toggleDomainsWithLock &&
                    serverManager.getServer(tileData.serverId) != null
                ) {
                    serverManager.integrationRepository(
                        tileData.serverId,
                    ).getEntityUpdates(listOf(tileData.entityId))?.collect {
                        tile.state =
                            if (it.isActive()) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                        getTileIcon(tileData.iconName, it, applicationContext)?.let { icon ->
                            tile.icon = Icon.createWithBitmap(icon)
                        }
                        tile.updateTile()
                    }
                }
            }
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        Timber.d("Tile: $tileId is no longer in view")
        stateUpdateJob?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        mainScope.cancel()
    }

    private suspend fun setTileData(tile: Tile): Boolean {
        Timber.d("Attempting to set tile data for tile ID: $tileId")
        val context = applicationContext
        val tileData = tileDao.get(tileId.value)
        try {
            return if (tileData != null && tileData.isSetup) {
                tile.label = tileData.label
                if (SdkVersion.isAtLeast(Build.VERSION_CODES.Q)) {
                    tile.subtitle = tileData.subtitle
                }
                val state: Entity? =
                    if (
                        tileData.entityId.split(".")[0] in toggleDomainsWithLock ||
                        tileData.iconName == null
                    ) {
                        withContext(Dispatchers.IO) {
                            try {
                                serverManager.integrationRepository(tileData.serverId).getEntity(tileData.entityId)
                            } catch (e: Exception) {
                                Timber.e(e, "Unable to get state for tile")
                                null
                            }
                        }
                    } else {
                        null
                    }
                if (tileData.entityId.split('.')[0] in toggleDomainsWithLock) {
                    tile.state = when {
                        state?.isActive() == true -> Tile.STATE_ACTIVE
                        state?.state != null && !state.isActive() -> Tile.STATE_INACTIVE
                        else -> Tile.STATE_UNAVAILABLE
                    }
                } else {
                    tile.state = Tile.STATE_INACTIVE
                }

                getTileIcon(tileData.iconName, state, context)?.let { icon ->
                    tile.icon = Icon.createWithBitmap(icon)
                }
                Timber.d("Tile data set for tile ID: $tileId")
                tile.updateTile()
                true
            } else {
                if (tileData != null) {
                    Timber.d("Tile data found but not setup for tile ID: $tileId")
                } else {
                    Timber.d("No tile data found for tile ID: $tileId")
                }
                tile.state =
                    if (serverManager.isRegistered()) {
                        Tile.STATE_INACTIVE
                    } else {
                        Tile.STATE_UNAVAILABLE
                    }
                if (SdkVersion.isAtLeast(Build.VERSION_CODES.Q)) {
                    tile.subtitle = getString(commonR.string.tile_not_setup)
                }
                tile.updateTile()
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "Unable to set tile data for tile ID: $tileId")
            return false
        }
    }

    private suspend fun tileClicked(tile: Tile, isUnlock: Boolean) {
        Timber.d("Click detected for tile ID: $tileId")
        val context = applicationContext
        val tileData = tileDao.get(tileId.value)
        val vm = getSystemService<Vibrator>()
        if (!isUnlock) {
            if (tileData?.shouldVibrate == true) {
                if (SdkVersion.isAtLeast(Build.VERSION_CODES.Q)) {
                    vm?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                } else {
                    @Suppress("DEPRECATION")
                    vm?.vibrate(500)
                }
            }
            if (tileData?.authRequired == true && isSecure) {
                unlockAndRun {
                    mainScope.launch { tileClicked(tile, isUnlock = true) }
                }
                return
            }
        }

        val hasTile = setTileData(tile)
        val needsUpdate = tileData != null && tileData.entityId.split('.')[0] !in toggleDomainsWithLock
        if (hasTile) {
            if (tileData?.serverId == null || serverManager.getServer(tileData.serverId) == null) {
                tileClickedError(tileData, null)
                return
            }
            if (needsUpdate) {
                tile.state = Tile.STATE_ACTIVE
                tile.updateTile()
            }
            withContext(Dispatchers.IO) {
                try {
                    onEntityPressedWithoutState(
                        tileData.entityId,
                        serverManager.integrationRepository(tileData.serverId),
                    )
                    Timber.d("Service call sent for tile ID: $tileId")
                } catch (e: Exception) {
                    tileClickedError(tileData, e)
                }
            }
            if (needsUpdate) {
                tile.state = Tile.STATE_INACTIVE
                tile.updateTile()
            }
        } else {
            Timber.d("No tile data found for tile ID: $tileId")
            val tileSettingIntent = SettingsActivity.newInstance(
                context,
                SettingsActivity.Deeplink.QSTile(tileId.value),
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
            }
            withContext(Dispatchers.Main) {
                TileServiceCompat.startActivityAndCollapse(
                    this@TileExtensions,
                    PendingIntentActivityWrapper(
                        context,
                        tileId.value.hashCode(),
                        tileSettingIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT,
                        false,
                    ),
                )
            }
        }
    }

    private suspend fun tileClickedError(tileData: TileEntity?, e: Exception?) {
        if (e != null) Timber.e(e, "Unable to call service for tile ID: ${tileData?.id}")
        if (tileData != null && tileData.shouldVibrate) {
            val vm = getSystemService<Vibrator>()
            if (SdkVersion.isAtLeast(Build.VERSION_CODES.Q)) {
                vm?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
            } else {
                @Suppress("DEPRECATION")
                vm?.vibrate(1000)
            }
        }
        withContext(Dispatchers.Main) {
            Toast.makeText(
                applicationContext,
                commonR.string.action_failure,
                Toast.LENGTH_SHORT,
            )
                .show()
        }
    }

    private suspend fun setTileAdded(added: Boolean) {
        tileDao.get(tileId.value)?.let {
            tileDao.add(it.copy(added = added))
        } ?: run {
            if (added) { // Store an empty tile in the database to track added
                tileDao.add(
                    TileEntity(
                        tileId = tileId.value,
                        added = true,
                        serverId = 0,
                        iconName = null,
                        entityId = "",
                        label = "",
                        subtitle = null,
                        shouldVibrate = false,
                        authRequired = false,
                    ),
                )
            } // else if it doesn't exist and is removed we don't have to save anything
        }

        val highestInUse = tileDao.getHighestInUse()?.numberedId ?: 0
        Timber.d("Highest tile in use: $highestInUse")
        updateActiveTileServices(highestInUse, applicationContext)
    }

    private fun getTileIcon(tileIconName: String?, entity: Entity?, context: Context): Bitmap? {
        // Create an icon pack and load all drawables.
        if (!tileIconName.isNullOrBlank()) {
            val icon = CommunityMaterial.getIconByMdiName(tileIconName) ?: return null
            val iconDrawable = IconicsDrawable(context, icon)
            return iconDrawable.toBitmap()
        } else {
            entity?.getIcon(context)?.let {
                return IconicsDrawable(context, it).apply {
                    sizeDp = 48
                }.toBitmap()
            }
        }

        return null
    }

    companion object {
        private val toggleDomainsWithLock = EntityExt.DOMAINS_TOGGLE
    }

    private fun handleInject() {
        // onTileAdded/onTileRemoved might be called outside onCreate - onDestroy, which usually
        // handles injection. Because we need the DAO to save added/removed, inject it if required.
        if (!this::tileDao.isInitialized) {
            tileDao = EntryPointAccessors.fromApplication(
                this@TileExtensions.applicationContext,
                TileExtensionsEntryPoint::class.java,
            ).tileDao()
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface TileExtensionsEntryPoint {
        fun tileDao(): TileDao
    }
}
