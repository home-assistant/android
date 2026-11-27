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
import io.homeassistant.companion.android.database.wear.ThermostatTile
import io.homeassistant.companion.android.database.wear.ThermostatTileDao
import io.homeassistant.companion.android.home.HomeActivity
import io.homeassistant.companion.android.tiles.ThermostatTileIds.ICON_COOL
import io.homeassistant.companion.android.tiles.ThermostatTileIds.ICON_ENHANCED_POWER
import io.homeassistant.companion.android.tiles.ThermostatTileIds.ICON_FAN
import io.homeassistant.companion.android.tiles.ThermostatTileIds.ICON_FAN_OFF
import io.homeassistant.companion.android.tiles.ThermostatTileIds.ICON_HEAT
import io.homeassistant.companion.android.tiles.ThermostatTileIds.TAP_ACTION_DOWN
import io.homeassistant.companion.android.tiles.ThermostatTileIds.TAP_ACTION_UP
import io.homeassistant.companion.android.tiles.ThermostatTileIds.TAP_FAN
import io.homeassistant.companion.android.tiles.ThermostatTileIds.TAP_MODE_PREFIX
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
    lateinit var commandCoordinator: ThermostatCommandCoordinator

    @Inject
    lateinit var thermostatMetadata: ThermostatMetadata

    private suspend fun shouldShowInvitation(config: ThermostatTile?, tileId: Int): Boolean {
        if (config == null || config.layoutStyle != "classic" || config.entityId.isNullOrBlank()) return false
        return !wearPrefsRepository.isThermostatLayoutInvitationAcknowledged(tileId)
    }

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<Tile> =
        serviceScope.future {
            val tileId = requestParams.tileId
            val invitationChoice = requestParams.currentState.lastClickableId
            val tileConfig = thermostatSettingsMutex.withLock {
                val config = thermostatTileDao.get(tileId) ?: return@withLock null
                if (invitationChoice in
                    listOf(ThermostatTileIds.INVITATION_ACCEPT, ThermostatTileIds.INVITATION_DECLINE) &&
                    shouldShowInvitation(config, tileId)
                ) {
                    val updated = if (invitationChoice == ThermostatTileIds.INVITATION_ACCEPT) {
                        config.copy(layoutStyle = "enhanced").also { thermostatTileDao.add(it) }
                    } else {
                        config
                    }
                    wearPrefsRepository.setThermostatLayoutInvitationAcknowledged(tileId, true)
                    updated
                } else {
                    config
                }
            }

            if (requestParams.currentState.lastClickableId.isNotEmpty()) {
                if (wearPrefsRepository.getWearHapticFeedback()) hapticClick(applicationContext)
            }

            val freshness = when {
                (tileConfig?.refreshInterval != null && tileConfig.refreshInterval!! <= 1) -> 0
                tileConfig?.refreshInterval != null -> tileConfig.refreshInterval!!
                else -> DEFAULT_REFRESH_INTERVAL
            }

            val tile = Tile.Builder()
                .setResourcesVersion("$TAG.resources.v5")
                .setFreshnessIntervalMillis(TimeUnit.SECONDS.toMillis(freshness))

            if (shouldShowInvitation(tileConfig, tileId)) {
                return@future tile.setTileTimeline(
                    thermostatInvitationTimeline(this@ThermostatTile, requestParams),
                ).build()
            }

            if (!serverManager.isRegistered()) {
                tile.setTileTimeline(
                    loggedOutTimeline(
                        this@ThermostatTile,
                        requestParams,
                        commonR.string.thermostat,
                        commonR.string.thermostat_tile_log_in,
                    ),
                ).build()
            } else {
                if (tileConfig?.entityId.isNullOrBlank()) {
                    tile.setTileTimeline(
                        getNotConfiguredTimeline(
                            this@ThermostatTile,
                            requestParams,
                            commonR.string.thermostat_tile_no_entity_yet,
                            HomeActivity.Companion.LaunchMode.ThermostatTile,
                        ),
                    ).build()
                } else {
                    try {
                        val entityId = checkNotNull(tileConfig.entityId)
                        val serverId = checkNotNull(serverManager.getServer()) { "No active server" }.id
                        val target = ThermostatCommandCoordinator.Target(serverId, entityId)
                        val requestStarted = android.os.SystemClock.elapsedRealtime()
                        // Render warm tiles immediately; fetch only the selected entity on a cache miss.
                        val cached = commandCoordinator.cachedEntity(target)
                        val entity = cached ?: withTimeoutOrNull(6_000L) {
                            serverManager.integrationRepository(serverId).getEntity(entityId)
                                ?.let { EntityDisplayWithoutContext(it) }
                                ?.also { commandCoordinator.rememberEntity(target, it) }
                        }
                        checkNotNull(entity) { "Thermostat state not yet available: $entityId" }
                        val climateControls = entity.climateControls
                        val metadata = thermostatMetadata.resolve(target, entity)
                        val units = ThermostatDisplayUnits(metadata.sourceUnit, tileConfig.temperatureUnit)
                        val actionPrefix = units.actionPrefix(target.actionPrefix)

                        val clickedId = requestParams.currentState.lastClickableId
                        val lastId = if (clickedId.startsWith(
                                actionPrefix,
                            )
                        ) {
                            clickedId.removePrefix(actionPrefix)
                        } else {
                            ""
                        }
                        if (clickedId.isNotEmpty()) {
                            Timber.i(
                                "Thermostat tile action resolved after %d ms; valid=%s",
                                android.os.SystemClock.elapsedRealtime() - requestStarted,
                                lastId.isNotEmpty(),
                            )
                        }
                        val controls = climateControls
                        if (controls != null && entity.rawState !in listOf("unavailable", "unknown")) {
                            val classic = tileConfig.layoutStyle == "classic"
                            when {
                                classic && lastId in listOf(TAP_ACTION_DOWN, TAP_ACTION_UP) -> {
                                    val slot = if (entity.rawState == "heat_cool") {
                                        ThermostatCommandCoordinator.TemperatureSlot.CLASSIC_RANGE
                                    } else {
                                        ThermostatCommandCoordinator.TemperatureSlot.CLASSIC_SINGLE
                                    }
                                    val amount = if (lastId == TAP_ACTION_UP) 1f else -1f
                                    commandCoordinator.adjustTemperature(target, units, slot, amount)
                                }
                                !classic && tileConfig.showHvacModes && lastId.startsWith(TAP_MODE_PREFIX) -> {
                                    val mode = lastId.removePrefix(TAP_MODE_PREFIX)
                                    if (mode in controls.hvacModes) commandCoordinator.submitHvac(target, mode)
                                }
                                !classic && tileConfig.showFanControl && lastId == TAP_FAN -> {
                                    commandCoordinator.cycleFan(target)
                                }
                                !classic && lastId.startsWith("Temp:") -> {
                                    val items = lastId.split(":")
                                    val slot = when (items.getOrNull(1)) {
                                        "single" -> ThermostatCommandCoordinator.TemperatureSlot.SINGLE
                                        "heat" -> ThermostatCommandCoordinator.TemperatureSlot.HEAT
                                        "cool" -> ThermostatCommandCoordinator.TemperatureSlot.COOL
                                        else -> null
                                    }
                                    val amount = items.getOrNull(2)?.toFloatOrNull()
                                    if (slot != null && amount != null && amount.isFinite()) {
                                        commandCoordinator.adjustTemperature(target, units, slot, amount)
                                    }
                                }
                            }
                        }

                        // State refresh happens in the background, never before dispatching a tap.
                        // Pending commands monitor acknowledgment; the coordinator suppresses redundant reads.
                        if (tileConfig.refreshInterval != 0L) {
                            val visibleAt = if (lastId.isNotEmpty()) {
                                commandCoordinator.nowMillis()
                            } else {
                                requestParams.lastVisibleTime.toEpochMilli()
                            }
                            commandCoordinator.observeVisibleTile(tileId, target, visibleAt)
                        } else {
                            commandCoordinator.stopObservingTile(tileId)
                        }
                        val pending = commandCoordinator.get(target)
                        if (pending != null) {
                            val pendingFreshness = if (freshness == 0L) 5L else minOf(freshness, 5L)
                            tile.setFreshnessIntervalMillis(TimeUnit.SECONDS.toMillis(pendingFreshness))
                        }
                        // A manual tap bypasses the cache age and retry gates, but still coalesces
                        // with an in-flight refresh or command confirmation. A cold tile already fetched above.
                        if (clickedId != MODIFIER_CLICK_REFRESH || cached != null) {
                            val maxAge = when {
                                clickedId == MODIFIER_CLICK_REFRESH -> 0L
                                pending != null -> PENDING_CACHE_MAX_AGE_MS
                                else -> CACHE_MAX_AGE_MS
                            }
                            commandCoordinator.refreshEntityIfStale(target, maxAge)
                        }
                        val temperatureUnit = metadata.sourceUnit ?: "°"
                        val displayEntity = entity.copy(name = metadata.name)
                        if (clickedId.isNotEmpty()) {
                            Timber.i(
                                "Thermostat tile action processed after %d ms; pending=%s",
                                android.os.SystemClock.elapsedRealtime() - requestStarted,
                                pending != null,
                            )
                        }
                        val views = ThermostatTileViews(this@ThermostatTile)
                        tile.setTileTimeline(
                            if (tileConfig.layoutStyle == "classic") {
                                views.classicTimeline(tileConfig, displayEntity, temperatureUnit, pending, actionPrefix)
                            } else {
                                views.enhancedTimeline(
                                    tileConfig,
                                    displayEntity,
                                    temperatureUnit,
                                    pending,
                                    actionPrefix,
                                    requestParams.deviceConfiguration.screenWidthDp,
                                    requestParams.deviceConfiguration.screenHeightDp,
                                )
                            },
                        ).build()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.e(e, "Unable to fetch entity ${tileConfig.entityId}")

                        tile.setTileTimeline(
                            primaryLayoutTimeline(
                                this@ThermostatTile,
                                requestParams,
                                null,
                                commonR.string.tile_fetch_entity_error,
                                commonR.string.refresh,
                                ActionBuilders.LoadAction.Builder().build(),
                            ),
                        ).build()
                    }
                }
            }
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
            addMdi(ICON_COOL, Mdi.Snowflake.toBitmap(iconSizePx, 0xFF38BDF8.toInt()))
            addMdi(ICON_FAN, Mdi.Fan.toBitmap(iconSizePx, 0xFF22C55E.toInt()))
            addMdi(ICON_FAN_OFF, Mdi.FanOff.toBitmap(iconSizePx, 0xFF9CA3AF.toInt()))
            addMdi(ICON_ENHANCED_POWER, Mdi.Power.toBitmap(iconSizePx, 0xFF9CA3AF.toInt()))
            resources.build()
        }

    override fun onTileAddEvent(requestParams: EventBuilders.TileAddEvent) {
        val tileId = requestParams.tileId
        launchSettingsEvent {
            if (thermostatTileDao.get(tileId) == null) {
                thermostatTileDao.add(ThermostatTile(id = tileId, layoutStyle = "enhanced"))
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
