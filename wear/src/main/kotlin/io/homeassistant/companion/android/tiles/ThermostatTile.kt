package io.homeassistant.companion.android.tiles

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
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithoutContext
import io.homeassistant.companion.android.common.data.prefs.WearPrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.wear.ThermostatTile
import io.homeassistant.companion.android.database.wear.ThermostatTileDao
import io.homeassistant.companion.android.home.HomeActivity
import io.homeassistant.companion.android.tiles.ThermostatTileIds.ICON_AUTO_COOL
import io.homeassistant.companion.android.tiles.ThermostatTileIds.ICON_AUTO_HEAT
import io.homeassistant.companion.android.tiles.ThermostatTileIds.ICON_COOL
import io.homeassistant.companion.android.tiles.ThermostatTileIds.ICON_FAN
import io.homeassistant.companion.android.tiles.ThermostatTileIds.ICON_HEAT
import io.homeassistant.companion.android.tiles.ThermostatTileIds.ICON_POWER
import io.homeassistant.companion.android.tiles.ThermostatTileIds.TAP_ACTION_DOWN
import io.homeassistant.companion.android.tiles.ThermostatTileIds.TAP_ACTION_UP
import io.homeassistant.companion.android.tiles.ThermostatTileIds.TAP_FAN
import io.homeassistant.companion.android.tiles.ThermostatTileIds.TAP_MODE_PREFIX
import io.homeassistant.companion.android.tiles.ThermostatTileIds.TAP_POWER_OFF
import io.homeassistant.companion.android.tiles.ThermostatTileIds.TAP_POWER_ON
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

@AndroidEntryPoint
class ThermostatTile : TileService() {

    companion object {
        private const val TAG = "ThermostatTile"
        const val DEFAULT_REFRESH_INTERVAL = 600L
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

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<Tile> =
        serviceScope.future {
            val tileId = requestParams.tileId
            val tileConfig = thermostatTileDao.get(tileId)

            if (requestParams.currentState.lastClickableId.isNotEmpty()) {
                if (wearPrefsRepository.getWearHapticFeedback()) hapticClick(applicationContext)
            }

            val freshness = when {
                (tileConfig?.refreshInterval != null && tileConfig.refreshInterval!! <= 1) -> 0
                tileConfig?.refreshInterval != null -> tileConfig.refreshInterval!!
                else -> DEFAULT_REFRESH_INTERVAL
            }

            val tile = Tile.Builder()
                .setResourcesVersion("$TAG.resources.v2")
                .setFreshnessIntervalMillis(TimeUnit.SECONDS.toMillis(freshness))

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
                        val actionPrefix = target.actionPrefix
                        val requestStarted = android.os.SystemClock.elapsedRealtime()
                        // Render warm tiles immediately; fetch only the selected entity on a cache miss.
                        val entity = commandCoordinator.cachedEntity(target)
                            ?: withTimeoutOrNull(6_000L) {
                                serverManager.integrationRepository(serverId).getEntity(entityId)
                                    ?.let { EntityDisplayWithoutContext(it) }
                                    ?.also { commandCoordinator.rememberEntity(target, it) }
                            }
                        checkNotNull(entity) { "Thermostat state not yet available: $entityId" }
                        val climateControls = entity.climateControls

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
                            val step = controls.targetTemperatureStep?.takeIf { it > 0 } ?: 1f
                            val min = controls.minTemperature ?: 7f
                            val max = controls.maxTemperature ?: 35f
                            // A failed command is not an authoritative temperature baseline.
                            val waitingCommand = commandCoordinator.get(tileId, target)
                                ?.takeIf { it.status == ThermostatCommandCoordinator.Pending.Status.WAITING }
                            when {
                                // Preserve the Classic tile's original +/- buttons, but dispatch
                                // asynchronously through the same fast coordinator as Enhanced.
                                tileConfig.layoutStyle == "classic" &&
                                    lastId in listOf(TAP_ACTION_DOWN, TAP_ACTION_UP) &&
                                    entity.rawState != "off" &&
                                    controls.targetTemperature != null &&
                                    waitingCommand?.value !in listOf("on", "off") -> {
                                    val delta = if (lastId == TAP_ACTION_UP) step else -step
                                    val baseline = waitingCommand?.single
                                        ?: controls.targetTemperature!!
                                    val proposed = ThermostatTemperatureLogic.snap(
                                        baseline + delta,
                                        step,
                                        min,
                                        max,
                                    )
                                    commandCoordinator.submitTemperature(
                                        tileId,
                                        target,
                                        false,
                                        null,
                                        null,
                                        proposed,
                                    )
                                }
                                tileConfig.layoutStyle == "classic" &&
                                    tileConfig.showPowerButton &&
                                    waitingCommand == null &&
                                    lastId == TAP_POWER_ON &&
                                    entity.rawState == "off" &&
                                    controls.supportsTurnOn -> {
                                    commandCoordinator.submitPower(tileId, target, true)
                                }
                                tileConfig.layoutStyle == "classic" &&
                                    tileConfig.showPowerButton &&
                                    waitingCommand == null &&
                                    lastId == TAP_POWER_OFF &&
                                    entity.rawState != "off" &&
                                    controls.supportsTurnOff -> {
                                    commandCoordinator.submitPower(tileId, target, false)
                                }
                                tileConfig.layoutStyle != "classic" &&
                                    tileConfig.showHvacModes &&
                                    lastId.startsWith(TAP_MODE_PREFIX) -> {
                                    val mode = lastId.removePrefix(TAP_MODE_PREFIX)
                                    if (mode in controls.hvacModes) {
                                        commandCoordinator.submitHvac(tileId, target, mode)
                                    }
                                }
                                tileConfig.layoutStyle != "classic" &&
                                    tileConfig.showFanControl &&
                                    lastId == TAP_FAN &&
                                    controls.fanModes.isNotEmpty() -> {
                                    val modes = controls.fanModes
                                    // WearDash uses fan on/auto. Prefer that pair if both are offered.
                                    val nextMode = if ("on" in modes && "auto" in modes) {
                                        if (controls.fanMode == "on") "auto" else "on"
                                    } else {
                                        modes[(modes.indexOf(controls.fanMode) + 1) % modes.size]
                                    }
                                    commandCoordinator.submitFan(tileId, target, nextMode)
                                }
                                tileConfig.layoutStyle != "classic" &&
                                    lastId.startsWith("Temp:") &&
                                    entity.rawState != "off" -> {
                                    val items = lastId.split(":")
                                    val slot = items.getOrNull(1)
                                    val amount = items.getOrNull(2)?.toFloatOrNull()
                                    if (amount != null &&
                                        amount.isFinite() &&
                                        slot in listOf("single", "heat", "cool")
                                    ) {
                                        val range = entity.rawState == "heat_cool" &&
                                            controls.targetTemperatureLow != null &&
                                            controls.targetTemperatureHigh != null
                                        // Ignore a tap from an outdated layout after a mode change.
                                        if (range && (slot == "heat" || slot == "cool")) {
                                            val currentLow = waitingCommand?.low ?: controls.targetTemperatureLow!!
                                            val currentHigh = waitingCommand?.high ?: controls.targetTemperatureHigh!!
                                            val revised = ThermostatTemperatureLogic.adjustRange(
                                                if (slot == "heat") currentLow + amount else currentHigh + amount,
                                                slot == "cool",
                                                currentLow,
                                                currentHigh,
                                                min,
                                                max,
                                                step,
                                            )
                                            commandCoordinator.submitTemperature(
                                                tileId,
                                                target,
                                                true,
                                                revised.first,
                                                revised.second,
                                                null,
                                            )
                                        } else if (!range && slot == "single" && controls.targetTemperature != null) {
                                            val baseline = waitingCommand?.single
                                                ?: controls.targetTemperature!!
                                            val proposed = ThermostatTemperatureLogic.snap(
                                                baseline + amount,
                                                step,
                                                min,
                                                max,
                                            )
                                            commandCoordinator.submitTemperature(
                                                tileId,
                                                target,
                                                false,
                                                null,
                                                null,
                                                proposed,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // State refresh happens in the background, never before dispatching a tap.
                        // Pending commands monitor acknowledgment; the coordinator suppresses redundant reads.
                        if (tileConfig.refreshInterval != 0L) {
                            val visibleAt = if (lastId.isNotEmpty()) {
                                System.currentTimeMillis()
                            } else {
                                requestParams.lastVisibleTime.toEpochMilli()
                            }
                            commandCoordinator.observeVisibleTile(tileId, target, visibleAt)
                        } else {
                            commandCoordinator.stopObservingTile(tileId)
                        }
                        val pending = commandCoordinator.get(tileId, target)
                        if (pending != null) {
                            val pendingFreshness = if (freshness == 0L) 5L else minOf(freshness, 5L)
                            tile.setFreshnessIntervalMillis(TimeUnit.SECONDS.toMillis(pendingFreshness))
                        }
                        commandCoordinator.refreshEntityIfStale(
                            target,
                            if (pending != null) 3_000L else 5_000L,
                        )
                        val temperatureUnit = controls?.temperatureUnit ?: "°"
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
                                views.classicTimeline(tileConfig, entity, temperatureUnit, pending, actionPrefix)
                            } else {
                                views.enhancedTimeline(
                                    tileConfig,
                                    entity,
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
            addDrawable(RESOURCE_REFRESH, R.drawable.ic_refresh)
            addDrawable(ICON_HEAT, R.drawable.ic_thermostat_heat)
            addDrawable(ICON_COOL, R.drawable.ic_thermostat_cool)
            addDrawable(ICON_AUTO_HEAT, R.drawable.ic_thermostat_auto_heat)
            addDrawable(ICON_AUTO_COOL, R.drawable.ic_thermostat_auto_cool)
            addDrawable(ICON_FAN, R.drawable.ic_thermostat_fan)
            addDrawable(ICON_POWER, R.drawable.ic_thermostat_power)
            resources.build()
        }

    override fun onTileAddEvent(requestParams: EventBuilders.TileAddEvent) = runBlocking {
        withContext(Dispatchers.IO) {
            if (thermostatTileDao.get(requestParams.tileId) == null) {
                thermostatTileDao.add(ThermostatTile(id = requestParams.tileId))
            } // else already existing, don't overwrite existing tile data
        }
    }

    override fun onTileRemoveEvent(requestParams: EventBuilders.TileRemoveEvent) = runBlocking {
        withContext(Dispatchers.IO) {
            commandCoordinator.stopObservingTile(requestParams.tileId)
            thermostatTileDao.delete(requestParams.tileId)
        }
    }

    override fun onRecentInteractionEventsAsync(
        events: List<EventBuilders.TileInteractionEvent>,
    ): ListenableFuture<Void?> = serviceScope.future {
        // Wear OS 6+ can batch ENTER and LEAVE, so process in time order and ignore stale enters.
        for (event in events.sortedBy { it.timestamp }) {
            when (event.eventType) {
                EventBuilders.TileInteractionEvent.ENTER -> {
                    val config = thermostatTileDao.get(event.tileId) ?: continue
                    val entityId = config.entityId?.takeIf { it.isNotBlank() } ?: continue
                    if (config.refreshInterval == 0L || !serverManager.isRegistered()) continue
                    val serverId = serverManager.getServer()?.id ?: continue
                    commandCoordinator.observeVisibleTile(
                        event.tileId,
                        ThermostatCommandCoordinator.Target(serverId, entityId),
                        event.timestamp.toEpochMilli(),
                    )
                }
                EventBuilders.TileInteractionEvent.LEAVE ->
                    commandCoordinator.stopObservingTile(event.tileId, event.timestamp.toEpochMilli())
            }
        }
        if (isRefreshOnViewedSupported()) {
            requestUpdateOnEnter(events) { tileId -> (thermostatTileDao.get(tileId)?.refreshInterval ?: 0L) >= 1L }
        }
        null
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
