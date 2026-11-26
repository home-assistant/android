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
import io.homeassistant.companion.android.common.data.integration.EntityFilter
import io.homeassistant.companion.android.common.data.integration.display.EntitiesForDisplayManager
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplay
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithoutContext
import io.homeassistant.companion.android.common.data.integration.display.awaitLoadedOrNull
import io.homeassistant.companion.android.common.data.prefs.WearPrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.wear.ThermostatTile
import io.homeassistant.companion.android.database.wear.ThermostatTileDao
import io.homeassistant.companion.android.home.HomeActivity
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Raw state shown while a turn on is not reported by the server yet, the climate entity then reports its actual mode. */
private const val PRESUMED_ON_STATE = "heat"

/** How long a presumed power state is shown at most, in case the server never reports it. */
private val PRESUMED_POWER_TIMEOUT = 30.seconds

/** How soon the system asks for the tile again after a toggle, so the presumed state is replaced by the actual one. */
private val TOGGLE_FALLBACK_REFRESH = 3.seconds

@AndroidEntryPoint
class ThermostatTile : TileService() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    @Inject
    lateinit var serverManager: ServerManager

    @Inject
    lateinit var wearPrefsRepository: WearPrefsRepository

    @Inject
    lateinit var thermostatTileDao: ThermostatTileDao

    @Inject
    lateinit var entitiesForDisplayManager: EntitiesForDisplayManager

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
                .setResourcesVersion("$TAG$tileId.${System.currentTimeMillis()}")
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
                        val lastId = requestParams.currentState.lastClickableId
                        val entity = tileConfig.entityId?.let {
                            entitiesForDisplayManager.snapshot(
                                ServerManager.SERVER_ID_ACTIVE,
                                EntityFilter(entityIds = setOf(it)),
                            )
                                .awaitLoadedOrNull()
                                ?.entity(it)
                        }
                        checkNotNull(entity)
                        val climateControls = entity.climateControls

                        // A toggle isn't reported by the server right away, so keep showing what the user asked for
                        val serverOn = entity.rawState != "off"
                        val presumedAge = System.currentTimeMillis() - (tileConfig.presumedPowerAt ?: 0L)
                        val pendingOn = tileConfig.presumedPowerOn?.takeIf {
                            it != serverOn && presumedAge < PRESUMED_POWER_TIMEOUT.inWholeMilliseconds
                        }
                        var updatedConfig = tileConfig.copy(
                            targetTemperature = null,
                            presumedPowerOn = pendingOn,
                            presumedPowerAt = tileConfig.presumedPowerAt.takeIf { pendingOn != null },
                        )

                        var targetTemp = tileConfig.targetTemperature ?: climateControls?.targetTemperature

                        // The entity reports its unit, which spares opening the websocket for the server config
                        val temperatureUnit = climateControls?.temperatureUnit
                            ?: serverManager.webSocketRepository().getConfig()
                                ?.unitSystem?.getValue("temperature").toString()

                        if (targetTemp != null && (lastId == TAP_ACTION_UP || lastId == TAP_ACTION_DOWN)) {
                            val stepSize = climateControls?.targetTemperatureStep
                                ?: if (temperatureUnit == "°F") 1.0f else 0.5f
                            val updatedTargetTemp = targetTemp + if (lastId == TAP_ACTION_UP) +stepSize else -stepSize

                            serverManager.integrationRepository().callAction(
                                entity.domain,
                                "set_temperature",
                                hashMapOf(
                                    "entity_id" to entity.entityId,
                                    "temperature" to updatedTargetTemp,
                                ),
                            )
                            updatedConfig = updatedConfig.copy(targetTemperature = updatedTargetTemp)
                            targetTemp = updatedTargetTemp
                        }

                        var shownOn = pendingOn ?: serverOn
                        if (lastId == TAP_ACTION_TOGGLE) {
                            // Toggle what is shown, as the server may still report the previous state
                            serverManager.integrationRepository().callAction(
                                entity.domain,
                                if (shownOn) "turn_off" else "turn_on",
                                hashMapOf("entity_id" to entity.entityId),
                            )
                            shownOn = !shownOn
                            updatedConfig = updatedConfig.copy(
                                presumedPowerOn = shownOn.takeIf { it != serverOn },
                                presumedPowerAt = System.currentTimeMillis().takeIf { shownOn != serverOn },
                            )
                            // The system then asks for the tile again, which replaces the presumed state
                            tile.setFreshnessIntervalMillis(TOGGLE_FALLBACK_REFRESH.inWholeMilliseconds)
                        }
                        thermostatTileDao.add(updatedConfig)

                        val shownEntity = if (shownOn == serverOn) entity else presumedEntity(entity, shownOn)

                        val name = shownEntity.name.takeIf { tileConfig.showEntityName == true }

                        tile.setTileTimeline(
                            thermostatTimeline(
                                this@ThermostatTile,
                                ThermostatTileContent(
                                    entity = shownEntity,
                                    entityName = name,
                                    targetTemperature = targetTemp,
                                    temperatureUnit = temperatureUnit,
                                    showPowerButton = tileConfig.showPowerButton == true,
                                ),
                            ),
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

    /** The [entity] as it is expected to look once it is turned [on] or off, until the server reports it. */
    private fun presumedEntity(entity: EntityDisplay, on: Boolean): EntityDisplay = EntityDisplayWithoutContext(
        entityId = entity.entityId,
        name = entity.name,
        icon = entity.icon,
        statelessIcon = entity.statelessIcon,
        rawState = if (on) PRESUMED_ON_STATE else "off",
        climateControls = entity.climateControls?.copy(hvacAction = if (on) "idle" else "off"),
    )

    override fun onTileResourcesRequest(requestParams: ResourcesRequest): ListenableFuture<Resources> =
        serviceScope.future {
            Resources.Builder()
                .setVersion(requestParams.version)
                .addIdToImageMapping(
                    RESOURCE_REFRESH,
                    ResourceBuilders.ImageResource.Builder()
                        .setAndroidResourceByResId(
                            ResourceBuilders.AndroidImageResourceByResId.Builder()
                                .setResourceId(R.drawable.ic_refresh)
                                .build(),
                        ).build(),
                )
                .addIdToImageMapping(
                    RESOURCE_POWER,
                    ResourceBuilders.ImageResource.Builder()
                        .setAndroidResourceByResId(
                            ResourceBuilders.AndroidImageResourceByResId.Builder()
                                .setResourceId(R.drawable.ic_power)
                                .build(),
                        ).build(),
                )
                .build()
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
            thermostatTileDao.delete(requestParams.tileId)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        private const val TAG = "ThermostatTile"
        const val DEFAULT_REFRESH_INTERVAL = 600L
        const val TAP_ACTION_UP = "Up"
        const val TAP_ACTION_DOWN = "Down"
        const val TAP_ACTION_TOGGLE = "Toggle"
    }
}
