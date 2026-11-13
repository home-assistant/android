package io.homeassistant.companion.android.tiles

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.ModifiersBuilders.Clickable
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.ResourceBuilders.Resources
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.protolayout.material.Button
import androidx.wear.protolayout.material.ButtonColors
import androidx.wear.tiles.EventBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.RequestBuilders.ResourcesRequest
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.integration.display.EntitiesForDisplayManager
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplay
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithoutContext
import io.homeassistant.companion.android.common.data.integration.display.awaitLoadedOrNull
import io.homeassistant.companion.android.common.data.prefs.WearPrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.wear.ThermostatTile
import io.homeassistant.companion.android.database.wear.ThermostatTileDao
import io.homeassistant.companion.android.home.HomeActivity
import java.util.concurrent.ConcurrentHashMap
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
import timber.log.Timber

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
                        val isTap = lastId == TAP_ACTION_UP || lastId == TAP_ACTION_DOWN || lastId == TAP_ACTION_TOGGLE
                        val entity = tileConfig.entityId?.let {
                            fetchEntity(tileId, it, tileConfig.showEntityName == true, isTap)
                        }
                        checkNotNull(entity)
                        val climateControls = entity.climateControls

                        var targetTemp = tileConfig.targetTemperature ?: climateControls?.targetTemperature

                        val config = serverManager.webSocketRepository().getConfig()
                        val temperatureUnit = config?.unitSystem?.getValue("temperature").toString()

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
                            val updated = tileConfig.copy(targetTemperature = updatedTargetTemp)
                            thermostatTileDao.add(updated)
                            targetTemp = updatedTargetTemp
                        } else {
                            val updated = tileConfig.copy(targetTemperature = null)
                            thermostatTileDao.add(updated)
                        }

                        if (lastId == TAP_ACTION_TOGGLE) {
                            serverManager.integrationRepository().callAction(
                                entity.domain,
                                if (entity.rawState == "off") "turn_on" else "turn_off",
                                hashMapOf("entity_id" to entity.entityId),
                            )
                        }

                        val name = entity.name.takeIf { tileConfig.showEntityName == true }

                        tile.setTileTimeline(
                            timeline(
                                this@ThermostatTile,
                                entity,
                                name,
                                targetTemp,
                                temperatureUnit,
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

    /**
     * Fetches [entityId] for display. A tap only needs the entity itself, so it skips the entity
     * registry the display snapshot fetches, using the name cached by the last full fetch.
     */
    private suspend fun fetchEntity(tileId: Int, entityId: String, showName: Boolean, isTap: Boolean): EntityDisplay? {
        val cachedName = entityNames[tileId]?.takeIf { it.first == entityId }?.second
        if (isTap && (!showName || cachedName != null)) {
            val entity = serverManager.integrationRepository().getEntity(entityId)
            if (entity != null) return EntityDisplayWithoutContext(entity = entity, name = cachedName.orEmpty())
        }

        val display = entitiesForDisplayManager.snapshot(ServerManager.SERVER_ID_ACTIVE, listOf(entityId))
            .awaitLoadedOrNull()
            ?.entity(entityId)
        if (display != null) entityNames[tileId] = entityId to display.name
        return display
    }

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
            entityNames.remove(requestParams.tileId)
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
        private const val RESOURCE_POWER = "power"
        private const val BUTTON_SIZE_DP = 40f
        private const val TARGET_TEMPERATURE_SIZE_SP = 30f
        private const val SPACING_DP = 10f
        private const val NAME_ARC_THICKNESS_DP = 30f
        private const val FULL_CIRCLE_DEGREES = 360f
        private const val HALF_CIRCLE_DEGREES = 180f

        /** Entity id and name per tile, as of the last full fetch, so a tap doesn't fetch the registry. */
        private val entityNames = ConcurrentHashMap<Int, Pair<String, String>>()

        /** [entityName] is null when the name isn't shown on the tile. */
        @VisibleForTesting
        internal fun timeline(
            context: Context,
            entity: EntityDisplay,
            entityName: String?,
            targetTemperature: Float?,
            temperatureUnit: String,
        ): Timeline = Timeline.fromLayoutElement(
            LayoutElementBuilders.Box.Builder().apply {
                addContent(getControlsColumn(context, entity, targetTemperature, temperatureUnit))
                addContent(getStatusRing(context, entity))
                if (entityName != null) addContent(getNameArc(entityName))
                // Refresh button
                addContent(getRefreshButton())
                setModifiers(getRefreshModifiers())
            }.build(),
        )

        private fun getControlsColumn(
            context: Context,
            entity: EntityDisplay,
            targetTemperature: Float?,
            temperatureUnit: String,
        ): LayoutElement {
            val hvacAction = entity.climateControls?.hvacAction.toString()

            return LayoutElementBuilders.Column.Builder()
                .addContent(
                    LayoutElementBuilders.Text.Builder()
                        .setText(getFriendlyHvacAction(context, hvacAction))
                        .build(),
                )
                .addContent(
                    LayoutElementBuilders.Text.Builder()
                        .setText("${targetTemperature ?: "--"} $temperatureUnit")
                        .setFontStyle(
                            LayoutElementBuilders.FontStyle.Builder().setSize(
                                DimensionBuilders.sp(TARGET_TEMPERATURE_SIZE_SP),
                            ).build(),
                        )
                        .build(),
                )
                .addContent(
                    LayoutElementBuilders.Text.Builder()
                        .setText("${entity.climateControls?.currentTemperature ?: "--"} $temperatureUnit")
                        .build(),
                )
                .addContent(
                    LayoutElementBuilders.Spacer.Builder()
                        .setHeight(DimensionBuilders.dp(SPACING_DP)).build(),
                )
                .addContent(getButtonsRow(context, entity, hvacAction))
                .build()
        }

        private fun getButtonsRow(context: Context, entity: EntityDisplay, hvacAction: String): LayoutElement {
            val buttonsEnabled = hvacAction != "off" && entity.rawState != "unavailable"

            return LayoutElementBuilders.Row.Builder()
                .addContent(getTempButton(context, buttonsEnabled, TAP_ACTION_DOWN))
                .addContent(getSpacer())
                .addContent(getPowerButton(context, entity.rawState != "unavailable"))
                .addContent(getSpacer())
                .addContent(getTempButton(context, buttonsEnabled, TAP_ACTION_UP))
                .build()
        }

        private fun getSpacer(): LayoutElement = LayoutElementBuilders.Spacer.Builder()
            .setWidth(DimensionBuilders.dp(SPACING_DP)).build()

        private fun getFriendlyHvacAction(context: Context, hvacAction: String): String = when (hvacAction) {
            "heating" -> context.getString(commonR.string.climate_heating)
            "cooling" -> context.getString(commonR.string.climate_cooling)
            "idle" -> context.getString(commonR.string.state_idle)
            "off" -> context.getString(commonR.string.state_off)
            else -> hvacAction.replaceFirstChar { it.uppercase() }
        }

        private fun getStatusRing(context: Context, entity: EntityDisplay): LayoutElement {
            val hvacActionColor = when (entity.climateControls?.hvacAction.toString()) {
                "heating" -> context.getColor(commonR.color.colorDeviceControlsThermostatHeat)
                "cooling" -> context.getColor(commonR.color.colorDeviceControlsDefaultOn)
                else -> 0x00000000
            }

            return LayoutElementBuilders.Arc.Builder()
                .addContent(
                    LayoutElementBuilders.ArcLine.Builder()
                        .setLength(DimensionBuilders.DegreesProp.Builder(FULL_CIRCLE_DEGREES).build())
                        .setThickness(DimensionBuilders.DpProp.Builder(2f).build())
                        .setColor(ColorBuilders.argb(hvacActionColor))
                        .build(),
                )
                .build()
        }

        private fun getNameArc(entityName: String): LayoutElement = LayoutElementBuilders.Arc.Builder()
            .setAnchorAngle(
                DimensionBuilders.DegreesProp.Builder(HALF_CIRCLE_DEGREES).build(),
            )
            .setAnchorType(LayoutElementBuilders.ARC_ANCHOR_CENTER)
            .addContent(
                LayoutElementBuilders.ArcLine.Builder()
                    .setLength(DimensionBuilders.DegreesProp.Builder(FULL_CIRCLE_DEGREES).build())
                    .setThickness(DimensionBuilders.DpProp.Builder(NAME_ARC_THICKNESS_DP).build())
                    .setColor(ColorBuilders.argb(0x00000000)) // Fully transparent
                    .build(),
            )
            .addContent(
                LayoutElementBuilders.ArcText.Builder()
                    .setText(entityName)
                    .build(),
            )
            .build()

        private fun getTempButton(context: Context, enabled: Boolean, action: String): LayoutElement {
            val clickable = Clickable.Builder()
            if (enabled) {
                clickable.setOnClick(ActionBuilders.LoadAction.Builder().build())
                    .setId(action)
            }

            return Button.Builder(context, clickable.build())
                .setSize(BUTTON_SIZE_DP)
                .setTextContent(if (action == TAP_ACTION_DOWN) "—" else "+")
                .setButtonColors(
                    ButtonColors(
                        ColorBuilders.argb(
                            context.getColor(
                                if (enabled) commonR.color.colorPrimary else commonR.color.colorDeviceControlsOff,
                            ),
                        ),
                        ColorBuilders.argb(context.getColor(commonR.color.colorWidgetButtonLabelBlack)),
                    ),
                )
                .build()
        }

        private fun getPowerButton(context: Context, enabled: Boolean): LayoutElement {
            val clickable = Clickable.Builder()
            if (enabled) {
                clickable.setOnClick(ActionBuilders.LoadAction.Builder().build())
                    .setId(TAP_ACTION_TOGGLE)
            }

            return Button.Builder(context, clickable.build())
                .setSize(BUTTON_SIZE_DP)
                .setIconContent(RESOURCE_POWER)
                .setButtonColors(
                    ButtonColors(
                        ColorBuilders.argb(
                            context.getColor(
                                if (enabled) {
                                    android.R.color.white
                                } else {
                                    commonR.color.colorDeviceControlsOff
                                },
                            ),
                        ),
                        ColorBuilders.argb(context.getColor(commonR.color.colorWidgetButtonLabelBlack)),
                    ),
                )
                .build()
        }
    }
}
