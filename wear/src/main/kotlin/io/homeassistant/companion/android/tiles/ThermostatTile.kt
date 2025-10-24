package io.homeassistant.companion.android.tiles

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
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.domain
import io.homeassistant.companion.android.common.data.integration.friendlyName
import io.homeassistant.companion.android.common.data.prefs.WearPrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.wear.ThermostatTile
import io.homeassistant.companion.android.home.HomeActivity
import java.util.concurrent.TimeUnit
import javax.inject.Inject
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

    companion object {
        private const val TAG = "ThermostatTile"
        const val DEFAULT_REFRESH_INTERVAL = 600L
        const val TAP_ACTION_UP = "Up"
        const val TAP_ACTION_DOWN = "Down"
    }

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    @Inject
    lateinit var serverManager: ServerManager

    @Inject
    lateinit var wearPrefsRepository: WearPrefsRepository

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<Tile> =
        serviceScope.future {
            val tileId = requestParams.tileId
            val thermostatTileDao = AppDatabase.getInstance(this@ThermostatTile).thermostatTileDao()
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
                        val entity = tileConfig.entityId?.let {
                            serverManager.integrationRepository().getEntity(it)
                        }
                        check(entity != null)

                        val lastId = requestParams.currentState.lastClickableId
                        var targetTemp =
                            tileConfig.targetTemperature ?: entity.attributes["temperature"]?.toString()?.toFloat()

                        val config = serverManager.webSocketRepository().getConfig()
                        val temperatureUnit = config?.unitSystem?.getValue("temperature").toString()

                        if (targetTemp != null && (lastId == TAP_ACTION_UP || lastId == TAP_ACTION_DOWN)) {
                            val attrStepSize = (entity.attributes["target_temp_step"] as? Number)?.toFloat()
                            val stepSize = attrStepSize ?: if (temperatureUnit == "°F") 1.0f else 0.5f
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

                        tile.setTileTimeline(
                            timeline(
                                tileConfig,
                                entity,
                                targetTemp,
                                temperatureUnit,
                            ),
                        ).build()
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
                .build()
        }

    override fun onTileAddEvent(requestParams: EventBuilders.TileAddEvent) = runBlocking {
        withContext(Dispatchers.IO) {
            val dao = AppDatabase.getInstance(this@ThermostatTile).thermostatTileDao()
            if (dao.get(requestParams.tileId) == null) {
                dao.add(ThermostatTile(id = requestParams.tileId))
            } // else already existing, don't overwrite existing tile data
        }
    }

    override fun onTileRemoveEvent(requestParams: EventBuilders.TileRemoveEvent) = runBlocking {
        withContext(Dispatchers.IO) {
            AppDatabase.getInstance(this@ThermostatTile)
                .thermostatTileDao()
                .delete(requestParams.tileId)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun timeline(
        tileConfig: ThermostatTile,
        entity: Entity,
        targetTemperature: Float?,
        temperatureUnit: String,
    ): Timeline = Timeline.fromLayoutElement(
        LayoutElementBuilders.Box.Builder().apply {
            val currentTemperature = entity.attributes["current_temperature"]
            val hvacAction = entity.attributes["hvac_action"].toString()

            val hvacActionColor = when (hvacAction) {
                "heating" -> getColor(commonR.color.colorDeviceControlsThermostatHeat)
                "cooling" -> getColor(commonR.color.colorDeviceControlsDefaultOn)
                else -> 0x00000000
            }

            val friendlyHvacAction = when (hvacAction) {
                "heating" -> getString(commonR.string.climate_heating)
                "cooling" -> getString(commonR.string.climate_cooling)
                "idle" -> getString(commonR.string.state_idle)
                "off" -> getString(commonR.string.state_off)
                else -> hvacAction.replaceFirstChar { it.uppercase() }
            }

            addContent(
                LayoutElementBuilders.Column.Builder()
                    .addContent(
                        LayoutElementBuilders.Text.Builder()
                            .setText(friendlyHvacAction)
                            .build(),
                    )
                    .addContent(
                        LayoutElementBuilders.Text.Builder()
                            .setText(
                                if (targetTemperature ==
                                    null
                                ) {
                                    "-- $temperatureUnit"
                                } else {
                                    "$targetTemperature $temperatureUnit"
                                },
                            )
                            .setFontStyle(
                                LayoutElementBuilders.FontStyle.Builder().setSize(
                                    DimensionBuilders.sp(30f),
                                ).build(),
                            )
                            .build(),
                    )
                    .addContent(
                        LayoutElementBuilders.Text.Builder()
                            .setText(
                                if (currentTemperature ==
                                    null
                                ) {
                                    "-- $temperatureUnit"
                                } else {
                                    "$currentTemperature $temperatureUnit"
                                },
                            )
                            .build(),
                    )
                    .addContent(
                        LayoutElementBuilders.Spacer.Builder()
                            .setHeight(DimensionBuilders.dp(10f)).build(),
                    )
                    .addContent(
                        LayoutElementBuilders.Row.Builder()
                            .addContent(
                                getTempButton(hvacAction != "off" && entity.state != "unavailable", TAP_ACTION_DOWN),
                            )
                            .addContent(
                                LayoutElementBuilders.Spacer.Builder()
                                    .setWidth(DimensionBuilders.dp(20f)).build(),
                            )
                            .addContent(
                                getTempButton(hvacAction != "off" && entity.state != "unavailable", TAP_ACTION_UP),
                            )
                            .build(),
                    )
                    .build(),
            )
            addContent(
                LayoutElementBuilders.Arc.Builder()
                    .addContent(
                        LayoutElementBuilders.ArcLine.Builder()
                            .setLength(DimensionBuilders.DegreesProp.Builder(360f).build())
                            .setThickness(DimensionBuilders.DpProp.Builder(2f).build())
                            .setColor(ColorBuilders.argb(hvacActionColor))
                            .build(),
                    )
                    .build(),
            )
            if (tileConfig.showEntityName == true) {
                addContent(
                    LayoutElementBuilders.Arc.Builder()
                        .setAnchorAngle(
                            DimensionBuilders.DegreesProp.Builder(180f).build(),
                        )
                        .setAnchorType(LayoutElementBuilders.ARC_ANCHOR_CENTER)
                        .addContent(
                            LayoutElementBuilders.ArcLine.Builder()
                                .setLength(DimensionBuilders.DegreesProp.Builder(360f).build())
                                .setThickness(DimensionBuilders.DpProp.Builder(30f).build())
                                .setColor(ColorBuilders.argb(0x00000000)) // Fully transparent
                                .build(),
                        )
                        .addContent(
                            LayoutElementBuilders.ArcText.Builder()
                                .setText(entity.friendlyName)
                                .build(),
                        )
                        .build(),
                )
            }
            // Refresh button
            addContent(getRefreshButton())
            setModifiers(getRefreshModifiers())
        }.build(),
    )

    private fun getTempButton(enabled: Boolean, action: String): LayoutElement {
        val clickable = Clickable.Builder()
        if (enabled) {
            clickable.setOnClick(ActionBuilders.LoadAction.Builder().build())
                .setId(action)
        }

        return Button.Builder(this, clickable.build())
            .setTextContent(if (action == TAP_ACTION_DOWN) "—" else "+")
            .setButtonColors(
                ButtonColors(
                    ColorBuilders.argb(
                        getColor(if (enabled) commonR.color.colorPrimary else commonR.color.colorDeviceControlsOff),
                    ),
                    ColorBuilders.argb(getColor(commonR.color.colorWidgetButtonLabelBlack)),
                ),
            )
            .build()
    }
}
