package io.homeassistant.companion.android.tiles

import android.util.Log
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
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.data.integration.friendlyName
import io.homeassistant.companion.android.common.data.prefs.WearPrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.wear.ThermostatTile
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

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

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<Tile> = serviceScope.future {
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

        if (tileConfig?.entityId.isNullOrBlank()) {
            tile.setTileTimeline(
                Timeline.fromLayoutElement(
                    LayoutElementBuilders.Box.Builder()
                        .addContent(
                            LayoutElementBuilders.Text.Builder()
                                .setText(getString(R.string.thermostat_tile_no_entity_yet))
                                .setMaxLines(10)
                                .build()
                        ).build()
                )
            ).build()
        } else {
            try {
                val entity = tileConfig?.entityId?.let {
                    serverManager.integrationRepository().getEntity(it)
                }

                val lastId = requestParams.currentState.lastClickableId
                var targetTemp = tileConfig?.targetTemperature ?: entity?.attributes?.get("temperature").toString().toFloat()

                if (lastId == TAP_ACTION_UP || lastId == TAP_ACTION_DOWN) {
                    val entityStr = entity?.entityId.toString()
                    val stepSize = entity?.attributes?.get("target_temp_step").toString().toFloat()
                    val updatedTargetTemp = targetTemp + if (lastId == TAP_ACTION_UP) +stepSize else -stepSize

                    serverManager.integrationRepository().callAction(
                        entityStr.split(".")[0],
                        "set_temperature",
                        hashMapOf(
                            "entity_id" to entityStr,
                            "temperature" to updatedTargetTemp
                        )
                    )
                    val updated = tileConfig?.copy(targetTemperature = updatedTargetTemp) ?: ThermostatTile(id = tileId, targetTemperature = updatedTargetTemp)
                    thermostatTileDao.add(updated)
                    targetTemp = updatedTargetTemp
                } else {
                    val updated = tileConfig?.copy(targetTemperature = null) ?: ThermostatTile(id = tileId, targetTemperature = null)
                    thermostatTileDao.add(updated)
                }

                tile.setTileTimeline(
                    if (serverManager.isRegistered()) {
                        timeline(
                            tileConfig,
                            targetTemp
                        )
                    } else {
                        loggedOutTimeline(
                            this@ThermostatTile,
                            requestParams,
                            R.string.thermostat,
                            R.string.thermostat_tile_log_in
                        )
                    }
                ).build()
            } catch (e: Exception) {
                Log.e(TAG, "Unable to fetch entity ${tileConfig?.entityId}", e)

                tile.setTileTimeline(
                    primaryLayoutTimeline(
                        this@ThermostatTile,
                        requestParams,
                        null,
                        R.string.tile_fetch_entity_error,
                        R.string.refresh,
                        ActionBuilders.LoadAction.Builder().build()
                    )
                ).build()
            }
        }
    }

    override fun onTileResourcesRequest(requestParams: ResourcesRequest): ListenableFuture<Resources> = serviceScope.future {
        Resources.Builder()
            .setVersion(requestParams.version)
            .addIdToImageMapping(
                RESOURCE_REFRESH,
                ResourceBuilders.ImageResource.Builder()
                    .setAndroidResourceByResId(
                        ResourceBuilders.AndroidImageResourceByResId.Builder()
                            .setResourceId(io.homeassistant.companion.android.R.drawable.ic_refresh)
                            .build()
                    ).build()
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

    private suspend fun timeline(tileConfig: ThermostatTile?, targetTemperature: Float): Timeline = Timeline.fromLayoutElement(
        LayoutElementBuilders.Box.Builder().apply {
            val entity = tileConfig?.entityId?.let {
                serverManager.integrationRepository().getEntity(it)
            }

            val currentTemperature = entity?.attributes?.get("current_temperature").toString()
            val config = serverManager.webSocketRepository().getConfig()
            val temperatureUnit = config?.unitSystem?.getValue("temperature").toString()

            val hvacAction = entity?.attributes?.get("hvac_action").toString()
            val hvacActionColor = when (hvacAction) {
                "heating" -> getColor(R.color.colorDeviceControlsThermostatHeat)
                "cooling" -> getColor(R.color.colorDeviceControlsDefaultOn)
                else -> 0x00000000
            }
            val friendlyHvacAction = when (hvacAction) {
                "heating" -> getString(R.string.climate_heating)
                "cooling" -> getString(R.string.climate_cooling)
                "idle" -> getString(R.string.state_idle)
                "off" -> getString(R.string.state_off)
                else -> hvacAction.replaceFirstChar { it.uppercase() }
            }

            addContent(
                LayoutElementBuilders.Column.Builder()
                    .addContent(
                        LayoutElementBuilders.Text.Builder()
                            .setText(friendlyHvacAction)
                            .build()
                    )
                    .addContent(
                        LayoutElementBuilders.Text.Builder()
                            .setText(if (hvacAction == "off") "-- $temperatureUnit" else "$targetTemperature $temperatureUnit")
                            .setFontStyle(
                                LayoutElementBuilders.FontStyle.Builder().setSize(
                                    DimensionBuilders.sp(30f)
                                ).build()
                            )
                            .build()
                    )
                    .addContent(
                        LayoutElementBuilders.Text.Builder()
                            .setText("$currentTemperature $temperatureUnit")
                            .build()
                    )
                    .addContent(
                        LayoutElementBuilders.Spacer.Builder()
                            .setHeight(DimensionBuilders.dp(10f)).build()
                    )
                    .addContent(
                        LayoutElementBuilders.Row.Builder()
                            .addContent(getTempButton(hvacAction != "off", TAP_ACTION_DOWN))
                            .addContent(
                                LayoutElementBuilders.Spacer.Builder()
                                    .setWidth(DimensionBuilders.dp(20f)).build()
                            )
                            .addContent(getTempButton(hvacAction != "off", TAP_ACTION_UP))
                            .build()
                    )
                    .build()
            )
            addContent(
                LayoutElementBuilders.Arc.Builder()
                    .addContent(
                        LayoutElementBuilders.ArcLine.Builder()
                            .setLength(DimensionBuilders.DegreesProp.Builder(360f).build())
                            .setThickness(DimensionBuilders.DpProp.Builder(2f).build())
                            .setColor(ColorBuilders.argb(hvacActionColor))
                            .build()
                    )
                    .build()
            )
            addContent(
                LayoutElementBuilders.Arc.Builder()
                    .setAnchorAngle(
                        DimensionBuilders.DegreesProp.Builder(180f).build()
                    )
                    .setAnchorType(LayoutElementBuilders.ARC_ANCHOR_CENTER)
                    .addContent(
                        LayoutElementBuilders.ArcLine.Builder()
                            .setLength(DimensionBuilders.DegreesProp.Builder(360f).build())
                            .setThickness(DimensionBuilders.DpProp.Builder(30f).build())
                            .setColor(ColorBuilders.argb(0x00000000)) // Fully transparent
                            .build()
                    )
                    .addContent(
                        LayoutElementBuilders.ArcText.Builder()
                            .setText(if (tileConfig?.showEntityName == true) entity?.friendlyName.toString() else "")
                            .build()
                    )
                    .build()
            )
            // Refresh button
            addContent(getRefreshButton())
            setModifiers(getRefreshModifiers())
        }.build()
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
                    ColorBuilders.argb(getColor(if (enabled) R.color.colorPrimary else R.color.colorDeviceControlsOff)),
                    ColorBuilders.argb(getColor(R.color.colorWidgetButtonLabelBlack))
                )
            )
            .build()
    }
}
