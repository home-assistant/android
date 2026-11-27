package io.homeassistant.companion.android.tiles

import android.graphics.Bitmap
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.ResourceBuilders.Resources
import androidx.wear.tiles.EventBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.RequestBuilders.ResourcesRequest
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import io.github.timoptr.mdiicons.Mdi
import io.github.timoptr.mdiicons.generated.Fan
import io.github.timoptr.mdiicons.generated.FanOff
import io.github.timoptr.mdiicons.generated.Power
import io.github.timoptr.mdiicons.generated.Snowflake
import io.github.timoptr.mdiicons.toBitmap
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithoutContext
import io.homeassistant.companion.android.common.data.prefs.WearPrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.wear.ThermostatLayoutStyle
import io.homeassistant.companion.android.database.wear.ThermostatTile
import io.homeassistant.companion.android.database.wear.ThermostatTileDao
import io.homeassistant.companion.android.home.HomeActivity
import io.homeassistant.companion.android.tiles.ThermostatTileIds.ICON_COOL
import io.homeassistant.companion.android.tiles.ThermostatTileIds.ICON_ENHANCED_POWER
import io.homeassistant.companion.android.tiles.ThermostatTileIds.ICON_FAN
import io.homeassistant.companion.android.tiles.ThermostatTileIds.ICON_FAN_OFF
import io.homeassistant.companion.android.tiles.ThermostatTileIds.ICON_HEAT
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

internal val thermostatSettingsMutex = Mutex()

// Lifecycle writes must finish even if Wear OS destroys the TileService after the callback.
private val thermostatLifecycleScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

@AndroidEntryPoint
class ThermostatTile : TileService() {

    companion object {
        private const val TAG = "ThermostatTile"
        const val DEFAULT_REFRESH_INTERVAL = 600L
        private const val PENDING_CACHE_MAX_AGE_MS = 3_000L
        private const val CACHE_MAX_AGE_MS = 5_000L
        private const val MODE_ICON_SIZE_DP = 25f
        private const val ENTITY_FETCH_TIMEOUT_MS = 6_000L
        private const val PENDING_REFRESH_INTERVAL_SECONDS = 5L
        private const val COOL_ICON_COLOR = 0xFF38BDF8.toInt()
        private const val FAN_ICON_COLOR = 0xFF22C55E.toInt()
        private const val INACTIVE_ICON_COLOR = 0xFF9CA3AF.toInt()
    }

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    @Inject
    lateinit var serverManager: ServerManager

    @Inject
    lateinit var wearPrefsRepository: WearPrefsRepository

    @Inject
    lateinit var thermostatTileDao: ThermostatTileDao

    @Inject
    internal lateinit var commandCoordinator: ThermostatCommandCoordinator

    @Inject
    lateinit var thermostatMetadata: ThermostatMetadata

    private suspend fun shouldShowInvitation(config: ThermostatTile?, tileId: Int): Boolean {
        if (config == null || config.layoutStyle != ThermostatLayoutStyle.CLASSIC || config.entityId.isNullOrBlank()) {
            return false
        }
        return !wearPrefsRepository.isThermostatLayoutInvitationAcknowledged(tileId)
    }

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<Tile> =
        serviceScope.future {
            val tileId = requestParams.tileId
            val clickedId = requestParams.currentState.lastClickableId
            val config = resolveConfiguration(tileId, clickedId)
            if (clickedId.isNotEmpty() && wearPrefsRepository.getWearHapticFeedback()) {
                hapticClick(applicationContext)
            }
            val freshness = config?.refreshInterval?.let { if (it <= 1L) 0L else it } ?: DEFAULT_REFRESH_INTERVAL
            val tile = Tile.Builder()
                .setResourcesVersion("$TAG.resources.v5")
                .setFreshnessIntervalMillis(TimeUnit.SECONDS.toMillis(freshness))

            when {
                shouldShowInvitation(config, tileId) -> tile.setTileTimeline(
                    thermostatInvitationTimeline(this@ThermostatTile, requestParams),
                ).build()
                !serverManager.isRegistered() -> tile.setTileTimeline(
                    loggedOutTimeline(
                        this@ThermostatTile,
                        requestParams,
                        commonR.string.thermostat,
                        commonR.string.thermostat_tile_log_in,
                    ),
                ).build()
                config == null || config.entityId.isNullOrBlank() -> tile.setTileTimeline(
                    getNotConfiguredTimeline(
                        this@ThermostatTile,
                        requestParams,
                        commonR.string.thermostat_tile_no_entity_yet,
                        HomeActivity.Companion.LaunchMode.ThermostatTile,
                    ),
                ).build()
                else -> configuredTile(tile, config, requestParams, freshness)
            }
        }

    private suspend fun resolveConfiguration(tileId: Int, clickedId: String): ThermostatTile? =
        thermostatSettingsMutex.withLock {
            val config = thermostatTileDao.get(tileId) ?: return@withLock null
            if (clickedId !in listOf(ThermostatTileIds.INVITATION_ACCEPT, ThermostatTileIds.INVITATION_DECLINE)) {
                return@withLock config
            }
            if (!shouldShowInvitation(config, tileId)) return@withLock config
            val updated = if (clickedId == ThermostatTileIds.INVITATION_ACCEPT) {
                config.copy(layoutStyle = ThermostatLayoutStyle.ENHANCED).also { thermostatTileDao.add(it) }
            } else {
                config
            }
            wearPrefsRepository.setThermostatLayoutInvitationAcknowledged(tileId, true)
            updated
        }

    private suspend fun configuredTile(
        tile: Tile.Builder,
        config: ThermostatTile,
        request: RequestBuilders.TileRequest,
        freshness: Long,
    ): Tile = try {
        renderConfiguredTile(tile, config, request, freshness)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.e(e, "Unable to fetch entity ${config.entityId}")
        tile.setTileTimeline(
            primaryLayoutTimeline(
                this,
                request,
                null,
                commonR.string.tile_fetch_entity_error,
                commonR.string.refresh,
                ActionBuilders.LoadAction.Builder().build(),
            ),
        ).build()
    }

    private suspend fun renderConfiguredTile(
        tile: Tile.Builder,
        config: ThermostatTile,
        request: RequestBuilders.TileRequest,
        freshness: Long,
    ): Tile {
        val entityId = checkNotNull(config.entityId)
        val serverId = checkNotNull(serverManager.getServer()) { "No active server" }.id
        val target = ThermostatCommandCoordinator.Target(serverId, entityId)
        val cached = commandCoordinator.cachedEntity(target)
        val entity = cached ?: fetchEntity(target)
        checkNotNull(entity) { "Thermostat state not yet available: $entityId" }
        val metadata = thermostatMetadata.resolve(target, entity)
        val units = ThermostatDisplayUnits(metadata.sourceUnit, config.temperatureUnit)
        val actionPrefix = units.actionPrefix(target.actionPrefix)
        val clickedId = request.currentState.lastClickableId
        val action = resolveThermostatAction(clickedId, actionPrefix, config, entity.rawState, entity.climateControls)
        dispatchAction(target, units, action)
        val pending = refreshState(request, config, target, actionPrefix, cached != null)
        if (pending != null) {
            val interval = if (freshness == 0L) {
                PENDING_REFRESH_INTERVAL_SECONDS
            } else {
                minOf(freshness, PENDING_REFRESH_INTERVAL_SECONDS)
            }
            tile.setFreshnessIntervalMillis(TimeUnit.SECONDS.toMillis(interval))
        }
        val temperatureUnit = metadata.sourceUnit ?: "°"
        val displayEntity = entity.copy(name = metadata.name)
        val views = ThermostatTileViews(this)
        return tile.setTileTimeline(
            when (config.layoutStyle) {
                ThermostatLayoutStyle.CLASSIC -> views.classicTimeline(
                    config,
                    displayEntity,
                    temperatureUnit,
                    pending,
                    actionPrefix,
                )
                ThermostatLayoutStyle.ENHANCED -> views.enhancedTimeline(
                    config,
                    displayEntity,
                    temperatureUnit,
                    pending,
                    actionPrefix,
                    request.deviceConfiguration.screenWidthDp,
                    request.deviceConfiguration.screenHeightDp,
                )
            },
        ).build()
    }

    // Render warm tiles immediately; fetch only the selected entity on a cache miss.
    private suspend fun fetchEntity(target: ThermostatCommandCoordinator.Target): EntityDisplayWithoutContext? =
        withTimeoutOrNull(ENTITY_FETCH_TIMEOUT_MS) {
            serverManager.integrationRepository(target.serverId).getEntity(target.entityId)
                ?.let { EntityDisplayWithoutContext(it) }
                ?.also { commandCoordinator.rememberEntity(target, it) }
        }

    private suspend fun dispatchAction(
        target: ThermostatCommandCoordinator.Target,
        units: ThermostatDisplayUnits,
        action: ThermostatTileAction?,
    ) {
        when (action) {
            is ThermostatTileAction.Adjust -> commandCoordinator.adjustTemperature(
                target,
                units,
                action.slot,
                action.amount,
            )
            is ThermostatTileAction.Mode -> commandCoordinator.submitHvac(target, action.mode)
            ThermostatTileAction.Fan -> commandCoordinator.cycleFan(target)
            null -> Unit
        }
    }

    private suspend fun refreshState(
        request: RequestBuilders.TileRequest,
        config: ThermostatTile,
        target: ThermostatCommandCoordinator.Target,
        actionPrefix: String,
        hadCachedEntity: Boolean,
    ): ThermostatCommandCoordinator.Pending? {
        val clickedId = request.currentState.lastClickableId
        if (config.refreshInterval != 0L) {
            val visibleAt = if (clickedId.startsWith(actionPrefix) && clickedId.length > actionPrefix.length) {
                commandCoordinator.nowMillis()
            } else {
                request.lastVisibleTime.toEpochMilli()
            }
            commandCoordinator.observeVisibleTile(request.tileId, target, visibleAt)
        } else {
            commandCoordinator.stopObservingTile(request.tileId)
        }
        val pending = commandCoordinator.get(target)
        // A manual tap bypasses cache age and retry gates; a cold tile already fetched above.
        if (clickedId != MODIFIER_CLICK_REFRESH || hadCachedEntity) {
            val maxAge = when {
                clickedId == MODIFIER_CLICK_REFRESH -> 0L
                pending != null -> PENDING_CACHE_MAX_AGE_MS
                else -> CACHE_MAX_AGE_MS
            }
            commandCoordinator.refreshEntityIfStale(target, maxAge)
        }
        return pending
    }

    override fun onTileResourcesRequest(requestParams: ResourcesRequest): ListenableFuture<Resources> =
        serviceScope.future {
            val resources = Resources.Builder().setVersion(requestParams.version)
            fun addDrawable(id: String, drawableId: Int) {
                resources.addIdToImageMapping(
                    id,
                    ResourceBuilders.ImageResource.Builder()
                        .setAndroidResourceByResId(
                            ResourceBuilders.AndroidImageResourceByResId.Builder()
                                .setResourceId(drawableId)
                                .build(),
                        ).build(),
                )
            }
            val iconSizePx = (MODE_ICON_SIZE_DP * requestParams.deviceConfiguration.screenDensity)
                .roundToInt().coerceAtLeast(1)
            fun addMdi(id: String, bitmap: Bitmap) {
                // Encode as PNG to preserve transparency over both selected and unselected buttons.
                val data = ByteArrayOutputStream().use { stream ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
                    stream.toByteArray()
                }
                resources.addIdToImageMapping(
                    id,
                    ResourceBuilders.ImageResource.Builder()
                        .setInlineResource(
                            ResourceBuilders.InlineImageResource.Builder()
                                .setData(data)
                                .setWidthPx(iconSizePx)
                                .setHeightPx(iconSizePx)
                                .setFormat(ResourceBuilders.IMAGE_FORMAT_UNDEFINED)
                                .build(),
                        ).build(),
                )
            }
            addDrawable(RESOURCE_REFRESH, R.drawable.ic_refresh)
            addDrawable(ICON_HEAT, R.drawable.ic_thermostat_flame_two_tone)
            addMdi(ICON_COOL, Mdi.Snowflake.toBitmap(iconSizePx, COOL_ICON_COLOR))
            addMdi(ICON_FAN, Mdi.Fan.toBitmap(iconSizePx, FAN_ICON_COLOR))
            addMdi(ICON_FAN_OFF, Mdi.FanOff.toBitmap(iconSizePx, INACTIVE_ICON_COLOR))
            addMdi(ICON_ENHANCED_POWER, Mdi.Power.toBitmap(iconSizePx, INACTIVE_ICON_COLOR))
            resources.build()
        }

    override fun onTileAddEvent(requestParams: EventBuilders.TileAddEvent) {
        val tileId = requestParams.tileId
        launchSettingsEvent {
            if (thermostatTileDao.get(tileId) == null) {
                thermostatTileDao.add(ThermostatTile(id = tileId, layoutStyle = ThermostatLayoutStyle.ENHANCED))
                wearPrefsRepository.setThermostatLayoutInvitationAcknowledged(tileId, true)
            }
        }
    }

    override fun onTileRemoveEvent(requestParams: EventBuilders.TileRemoveEvent) {
        val tileId = requestParams.tileId
        launchSettingsEvent {
            commandCoordinator.stopObservingTile(tileId)
            thermostatTileDao.delete(tileId)
            wearPrefsRepository.setThermostatLayoutInvitationAcknowledged(tileId, false)
        }
    }

    private fun launchSettingsEvent(action: suspend () -> Unit) {
        // Main.immediate starts lock acquisition in callback order without blocking Main.
        thermostatLifecycleScope.launch {
            try {
                thermostatSettingsMutex.withLock {
                    withContext(Dispatchers.IO) { action() }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Timber.e(error, "Thermostat tile lifecycle update failed")
            }
        }
    }

    // TileService declares a Java Void future, not a Kotlin Unit future.
    @Suppress("ForbiddenVoid")
    override fun onRecentInteractionEventsAsync(
        events: List<EventBuilders.TileInteractionEvent>,
    ): ListenableFuture<Void?> = serviceScope.future {
        // Wear OS 6+ can batch ENTER and LEAVE, so process in time order and ignore stale enters.
        for (event in events.sortedBy { it.timestamp }) {
            when (event.eventType) {
                EventBuilders.TileInteractionEvent.ENTER -> observeEnteredTile(event)
                EventBuilders.TileInteractionEvent.LEAVE -> {
                    commandCoordinator.stopObservingTile(event.tileId, event.timestamp.toEpochMilli())
                }
                EventBuilders.TileInteractionEvent.UNKNOWN -> Unit
            }
        }
        if (isRefreshOnViewedSupported()) {
            requestUpdateOnEnter(events) { tileId -> (thermostatTileDao.get(tileId)?.refreshInterval ?: 0L) >= 1L }
        }
        null
    }

    private suspend fun observeEnteredTile(event: EventBuilders.TileInteractionEvent) {
        val config = thermostatTileDao.get(event.tileId)
        val entityId = config?.entityId?.takeIf { it.isNotBlank() }
        if (config == null || entityId == null || config.refreshInterval == 0L) return
        val serverId = if (serverManager.isRegistered()) serverManager.getServer()?.id else null
        if (serverId != null) {
            commandCoordinator.observeVisibleTile(
                event.tileId,
                ThermostatCommandCoordinator.Target(serverId, entityId),
                event.timestamp.toEpochMilli(),
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
