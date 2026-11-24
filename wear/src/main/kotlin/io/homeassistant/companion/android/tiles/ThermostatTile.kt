package io.homeassistant.companion.android.tiles

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.ModifiersBuilders
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
import io.homeassistant.companion.android.common.data.integration.EntityFilter
import io.homeassistant.companion.android.common.data.integration.display.EntitiesForDisplayManager
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplay
import io.homeassistant.companion.android.common.data.integration.display.awaitLoadedOrNull
import io.homeassistant.companion.android.common.data.prefs.WearPrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.wear.ThermostatTile
import io.homeassistant.companion.android.database.wear.ThermostatTileDao
import io.homeassistant.companion.android.home.HomeActivity
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
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
        private const val TAP_ACTION_UP = "Up"
        private const val TAP_ACTION_DOWN = "Down"
        private const val TAP_MODE_PREFIX = "Mode:"
        private const val TAP_FAN = "CycleFan"

        // Stable ProtoLayout resource IDs; the tile's resource version changes only when icons change.
        private const val ICON_HEAT = "thermostat_heat_v1"
        private const val ICON_COOL = "thermostat_cool_v1"
        private const val ICON_AUTO_HEAT = "thermostat_auto_heat_v1"
        private const val ICON_AUTO_COOL = "thermostat_auto_cool_v1"
        private const val ICON_FAN = "thermostat_fan_v1"
        private const val ICON_POWER = "thermostat_power_v1"
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
    lateinit var entitiesForDisplayManager: EntitiesForDisplayManager

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

            val effectiveFreshness = if (commandCoordinator.get(tileId) != null) minOf(freshness, 5L) else freshness
            val tile = Tile.Builder()
                .setResourcesVersion("$TAG.resources.v2")
                .setFreshnessIntervalMillis(TimeUnit.SECONDS.toMillis(effectiveFreshness))

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
                        // Render warm tiles without waiting for a network request. On a cache miss,
                        // use upstream's filtered snapshot to resolve state and display metadata.
                        val entity = commandCoordinator.cachedEntity(entityId)
                            ?: withTimeoutOrNull(6_000L) {
                                entitiesForDisplayManager.snapshot(
                                    ServerManager.SERVER_ID_ACTIVE,
                                    EntityFilter(entityIds = setOf(entityId)),
                                )
                                    .awaitLoadedOrNull()
                                    ?.entity(entityId)
                                    ?.also(commandCoordinator::rememberEntity)
                            }
                        checkNotNull(entity) { "Thermostat state not yet available: $entityId" }
                        val climateControls = entity.climateControls

                        val lastId = requestParams.currentState.lastClickableId
                        val controls = climateControls
                        if (controls != null && entity.rawState !in listOf("unavailable", "unknown")) {
                            val step = controls.targetTemperatureStep?.takeIf { it > 0 } ?: 1f
                            val min = controls.minTemperature ?: 7f
                            val max = controls.maxTemperature ?: 35f
                            // A failed command is not an authoritative temperature baseline.
                            val waitingCommand = commandCoordinator.get(tileId)
                                ?.takeIf { it.status == ThermostatCommandCoordinator.Pending.Status.WAITING }
                            when {
                                // Preserve the Classic tile's original +/- buttons, but dispatch
                                // asynchronously through the same fast coordinator as Enhanced.
                                tileConfig.layoutStyle == "classic" &&
                                    lastId in listOf(TAP_ACTION_DOWN, TAP_ACTION_UP) &&
                                    entity.rawState != "off" && controls.targetTemperature != null -> {
                                    val delta = if (lastId == TAP_ACTION_UP) step else -step
                                    val baseline = waitingCommand?.single
                                        ?: controls.targetTemperature!!
                                    val proposed = ThermostatTemperatureLogic.snap(
                                        baseline + delta, step, min, max,
                                    )
                                    commandCoordinator.submitTemperature(
                                        tileId, entity.entityId, false, null, null, proposed,
                                    )
                                }
                                lastId.startsWith(TAP_MODE_PREFIX) -> {
                                    val mode = lastId.removePrefix(TAP_MODE_PREFIX)
                                    if (mode in controls.hvacModes) {
                                        commandCoordinator.submitHvac(tileId, entity.entityId, mode)
                                    }
                                }
                                lastId == TAP_FAN && controls.fanModes.isNotEmpty() -> {
                                    val modes = controls.fanModes
                                    // WearDash uses fan on/auto. Prefer that pair if both are offered.
                                    val nextMode = if ("on" in modes && "auto" in modes) {
                                        if (controls.fanMode == "on") "auto" else "on"
                                    } else {
                                        modes[(modes.indexOf(controls.fanMode) + 1) % modes.size]
                                    }
                                    commandCoordinator.submitFan(tileId, entity.entityId, nextMode)
                                }
                                tileConfig.layoutStyle != "classic" &&
                                    lastId.startsWith("Temp:") && entity.rawState != "off" -> {
                                    val items = lastId.split(":")
                                    val slot = items.getOrNull(1)
                                    val amount = items.getOrNull(2)?.toFloatOrNull()
                                    if (amount != null && amount.isFinite() && slot in listOf("single", "heat", "cool")) {
                                        val range = entity.rawState == "heat_cool" &&
                                            controls.targetTemperatureLow != null && controls.targetTemperatureHigh != null
                                        // Ignore a tap from an outdated layout after a mode change.
                                        if (range && (slot == "heat" || slot == "cool")) {
                                            val currentLow = waitingCommand?.low ?: controls.targetTemperatureLow!!
                                            val currentHigh = waitingCommand?.high ?: controls.targetTemperatureHigh!!
                                            val revised = ThermostatTemperatureLogic.adjustRange(
                                                if (slot == "heat") currentLow + amount else currentHigh + amount,
                                                slot == "cool", currentLow, currentHigh, min, max, step,
                                            )
                                            commandCoordinator.submitTemperature(
                                                tileId, entity.entityId, true, revised.first, revised.second, null,
                                            )
                                        } else if (!range && slot == "single" && controls.targetTemperature != null) {
                                            val baseline = waitingCommand?.single
                                                ?: controls.targetTemperature!!
                                            val proposed = ThermostatTemperatureLogic.snap(
                                                baseline + amount, step, min, max,
                                            )
                                            commandCoordinator.submitTemperature(
                                                tileId, entity.entityId, false, null, null, proposed,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // State refresh happens in the background, never before dispatching a tap.
                        // A pending command also polls for acknowledgment without registry fetches.
                        commandCoordinator.refreshEntityIfStale(
                            entityId,
                            if (commandCoordinator.get(tileId) != null) 3_000L else 20_000L,
                        )
                        val temperatureUnit = controls?.temperatureUnit ?: "°"
                        val pending = commandCoordinator.get(tileId)
                        tile.setTileTimeline(
                            if (tileConfig.layoutStyle == "classic") {
                                classicTimeline(tileConfig, entity, temperatureUnit, pending)
                            } else {
                                timeline(
                                    tileConfig, entity, temperatureUnit, pending,
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
            thermostatTileDao.delete(requestParams.tileId)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }


    /**
     * Original Home Assistant thermostat layout, retained as the Classic appearance.
     * It uses the shared fast v9 entity cache and command coordinator, so switching
     * appearance does not require reconfiguring the selected entity.
     */
    private fun classicTimeline(
        tileConfig: ThermostatTile,
        entity: EntityDisplay,
        temperatureUnit: String,
        pending: ThermostatCommandCoordinator.Pending?,
    ): Timeline {
        val entityName = if (tileConfig.showEntityName == true) entity.name else ""
        val targetTemperature = pending?.single
            ?.takeIf { pending.status == ThermostatCommandCoordinator.Pending.Status.WAITING }
            ?: entity.climateControls?.targetTemperature
        return Timeline.fromLayoutElement(
        LayoutElementBuilders.Box.Builder().apply {
            val currentTemperature = entity.climateControls?.currentTemperature
            val hvacAction = entity.climateControls?.hvacAction.toString()

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
                                getTempButton(hvacAction != "off" && entity.rawState != "unavailable", TAP_ACTION_DOWN),
                            )
                            .addContent(
                                LayoutElementBuilders.Spacer.Builder()
                                    .setWidth(DimensionBuilders.dp(20f)).build(),
                            )
                            .addContent(
                                getTempButton(hvacAction != "off" && entity.rawState != "unavailable", TAP_ACTION_UP),
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
                                .setText(entityName)
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

    }

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

    /**
     * WearDash-inspired direct controls. All actions are handled with LoadAction on the tile;
     * nothing launches the old arc editor or waits for a separate Activity to appear.
     *
     * Two setpoint rows are shown only in heat_cool. Secondary adjustments share each row
     * with the primary +/-1 controls, but the mode and utility actions have their own rows
     * so they cannot run off the edges of a circular display.
     */
    private fun timeline(
        config: ThermostatTile,
        entity: EntityDisplay,
        temperatureUnit: String,
        pending: ThermostatCommandCoordinator.Pending?,
        screenWidthDp: Int,
        screenHeightDp: Int,
    ): Timeline {
        val controls = entity.climateControls
        val state = entity.rawState
        val active = state !in listOf("off", "unavailable", "unknown")
        val range = state == "heat_cool" &&
            controls?.targetTemperatureLow != null && controls.targetTemperatureHigh != null
        val current = controls?.currentTemperature?.let { nice(it) } ?: "--"
        val hvacAction = controls?.hvacAction?.takeIf { it.isNotBlank() } ?: "idle"
        val waiting = pending?.status == ThermostatCommandCoordinator.Pending.Status.WAITING
        // Compact circular watches must not crop rows at the upper/lower edges.
        val compact = screenWidthDp < 220 || screenHeightDp < 225
        val modeHeight = if (compact) 29f else 34f
        val utilityHeight = if (compact) 29f else 33f
        val targetRowHeight = if (compact) 32f else 36f

        val column = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)

        // Hide the name only in compact Auto mode where the two temperature rows need the space.
        if (config.showEntityName == true && !(compact && range)) {
            column.addContent(
                temperatureText(entity.name.take(if (compact) 12 else 21), 10f, 0xFFCBD5E1.toInt()),
            )
        }
        column.addContent(temperatureText("$current $temperatureUnit", if (compact) 27f else 30f, 0xFFFFFFFF.toInt()))
        if (config.showActivity || pending != null) {
            val displayState = when (state) {
                "heat" -> "HEAT"
                "cool" -> "COOL"
                "heat_cool", "auto" -> "AUTO"
                "off" -> "OFF"
                else -> state.uppercase()
            }
            val modeColor = when (state) {
                "heat" -> 0xFFFFA19A.toInt()
                "cool" -> 0xFF8DCBFF.toInt()
                "heat_cool", "auto" -> 0xFFD9C4FF.toInt()
                else -> 0xFFBFC9D7.toInt()
            }
            val activity = when (pending?.status) {
                ThermostatCommandCoordinator.Pending.Status.WAITING -> "Awaiting thermostat reply"
                ThermostatCommandCoordinator.Pending.Status.FAILED -> "Thermostat did not confirm"
                null -> if (active) "$displayState · $hvacAction" else displayState
            }
            val activityColor = when (pending?.status) {
                ThermostatCommandCoordinator.Pending.Status.WAITING -> 0xFFFFD778.toInt()
                ThermostatCommandCoordinator.Pending.Status.FAILED -> 0xFFFF9797.toInt()
                null -> modeColor
            }
            column.addContent(temperatureText(activity, if (compact) 10f else 11f, activityColor))
        }

        column.addContent(verticalGap(if (compact) 2f else 3f))
        if (active && controls != null) {
            if (range) {
                column.addContent(
                    temperatureRow(
                        "heat", pending?.low?.takeIf { waiting } ?: controls.targetTemperatureLow,
                        "H", 0xFFFFA19A.toInt(), config.quickAdjustOne, compact, targetRowHeight,
                    ),
                )
                column.addContent(verticalGap(2f))
                column.addContent(
                    temperatureRow(
                        "cool", pending?.high?.takeIf { waiting } ?: controls.targetTemperatureHigh,
                        "C", 0xFF8DCBFF.toInt(), config.quickAdjustOne, compact, targetRowHeight,
                    ),
                )
            } else if (controls.targetTemperature != null) {
                val accent = if (state == "cool") 0xFF8DCBFF.toInt() else 0xFFFFA19A.toInt()
                column.addContent(
                    temperatureRow(
                        "single", pending?.single?.takeIf { waiting } ?: controls.targetTemperature,
                        "", accent, config.quickAdjustOne, compact, targetRowHeight,
                    ),
                )
            }
        }

        if (controls != null && (config.showHvacModes || config.showFanControl)) {
            column.addContent(verticalGap(if (compact) 2f else 5f))
            val available = controls.hvacModes
            val modeRow = LayoutElementBuilders.Row.Builder()
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            var hasMode = false
            fun addMode(mode: String, iconId: String, description: String, color: Int, secondIconId: String? = null) {
                if (mode !in available) return
                if (hasMode) modeRow.addContent(horizontalGap(if (compact) 4f else 5f))
                modeRow.addContent(
                    iconButton(
                        iconId, TAP_MODE_PREFIX + mode, state == mode,
                        if (compact) 37f else 39f, modeHeight, color, description, secondIconId,
                    ),
                )
                hasMode = true
            }
            if (config.showHvacModes) {
                addMode("heat", ICON_HEAT, "Heat mode", 0xFF9A3D40.toInt())
                addMode("cool", ICON_COOL, "Cool mode", 0xFF286FAD.toInt())
                addMode(
                    if ("heat_cool" in available) "heat_cool" else "auto",
                    ICON_AUTO_HEAT, "Auto heat and cool mode", 0xFF6961A6.toInt(), ICON_AUTO_COOL,
                )
            }
            if (hasMode) column.addContent(modeRow.build())

            val utilityRow = LayoutElementBuilders.Row.Builder()
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            if (config.showFanControl && controls.fanModes.isNotEmpty()) {
                val on = controls.fanMode != null && controls.fanMode != "auto"
                utilityRow.addContent(
                    iconButton(
                        ICON_FAN, TAP_FAN, on, if (compact) 56f else 65f, utilityHeight,
                        0xFF277766.toInt(), if (on) "Fan on; tap to change" else "Fan auto; tap to change",
                    ),
                )
                utilityRow.addContent(horizontalGap(if (compact) 6f else 8f))
            }
            if (config.showHvacModes && "off" in available) {
                utilityRow.addContent(
                    iconButton(
                        ICON_POWER, TAP_MODE_PREFIX + "off", state == "off",
                        if (compact) 45f else 54f, utilityHeight, 0xFF7A4355.toInt(), "Turn thermostat off",
                    ),
                )
            }
            if ((config.showFanControl && controls.fanModes.isNotEmpty()) ||
                (config.showHvacModes && "off" in available)
            ) {
                column.addContent(verticalGap(if (compact) 2f else 3f))
                column.addContent(utilityRow.build())
            }
        }

        return Timeline.fromLayoutElement(
            LayoutElementBuilders.Box.Builder()
                .setWidth(DimensionBuilders.expand())
                .setHeight(DimensionBuilders.expand())
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .addContent(column.build())
                .build(),
        )
    }

    /** One row per setpoint: -3, -1, value, +1, +3. No overflowing 7-button row. */
    private fun temperatureRow(
        slot: String,
        target: Float?,
        prefix: String,
        accent: Int,
        secondaryStep: Int,
        compact: Boolean,
        height: Float,
    ): LayoutElementBuilders.LayoutElement {
        val extra = secondaryStep.coerceIn(2, 10)
        val outerWidth = if (compact) 29f else 32f
        val primaryWidth = if (compact) 33f else 37f
        val valueWidth = if (compact) 42f else 48f
        val gap = if (compact) 1f else 2f
        val row = LayoutElementBuilders.Row.Builder()
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
        row.addContent(button("−$extra", "Temp:$slot:${-extra}", false, outerWidth, height))
        row.addContent(horizontalGap(gap))
        row.addContent(button("−", "Temp:$slot:-1", false, primaryWidth, height))
        row.addContent(horizontalGap(gap))
        val value = target?.let { nice(it) } ?: "--"
        val shown = if (prefix.isEmpty()) value else "$prefix$value"
        row.addContent(
            LayoutElementBuilders.Box.Builder()
                .setWidth(DimensionBuilders.dp(valueWidth))
                .setHeight(DimensionBuilders.dp(height))
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .addContent(temperatureText("$shown°", 16f, accent))
                .build(),
        )
        row.addContent(horizontalGap(gap))
        row.addContent(button("+", "Temp:$slot:1", false, primaryWidth, height))
        row.addContent(horizontalGap(gap))
        row.addContent(button("+$extra", "Temp:$slot:$extra", false, outerWidth, height))
        return row.build()
    }

    private fun button(
        label: String,
        action: String,
        selected: Boolean,
        width: Float,
        height: Float,
        activeColor: Int = 0xFF35445C.toInt(),
    ): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Box.Builder()
            .setWidth(DimensionBuilders.dp(width))
            .setHeight(DimensionBuilders.dp(height))
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .addContent(temperatureText(label, if (label.length > 4) 10f else 14f, 0xFFFFFFFF.toInt()))
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(ColorBuilders.argb(if (selected) activeColor else 0xFF293447.toInt()))
                            .setCorner(ModifiersBuilders.Corner.Builder().setRadius(DimensionBuilders.dp(14f)).build())
                            .build(),
                    )
                    .setClickable(
                        Clickable.Builder().setId(action).setOnClick(ActionBuilders.LoadAction.Builder().build()).build(),
                    ).build(),
            ).build()

    /** The five mode/utility buttons use drawables, not font-dependent symbols. */
    private fun iconButton(
        iconId: String,
        action: String,
        selected: Boolean,
        width: Float,
        height: Float,
        activeColor: Int,
        description: String,
        secondIconId: String? = null,
    ): LayoutElementBuilders.LayoutElement {
        val content: LayoutElementBuilders.LayoutElement = if (secondIconId == null) {
            tileIcon(iconId, 25f)
        } else {
            // Auto is two readable symbols: coral heat and blue cool.
            LayoutElementBuilders.Row.Builder()
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .addContent(tileIcon(iconId, 17f))
                .addContent(horizontalGap(1f))
                .addContent(tileIcon(secondIconId, 17f))
                .build()
        }
        return LayoutElementBuilders.Box.Builder()
            .setWidth(DimensionBuilders.dp(width))
            .setHeight(DimensionBuilders.dp(height))
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .addContent(content)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(ColorBuilders.argb(if (selected) activeColor else 0xFF293447.toInt()))
                            .setCorner(ModifiersBuilders.Corner.Builder().setRadius(DimensionBuilders.dp(14f)).build())
                            .build(),
                    )
                    .setClickable(
                        Clickable.Builder().setId(action).setOnClick(ActionBuilders.LoadAction.Builder().build()).build(),
                    )
                    .setSemantics(
                        ModifiersBuilders.Semantics.Builder()
                            .setContentDescription(if (selected) "$description, selected" else description)
                            .build(),
                    )
                    .build(),
            )
            .build()
    }

    private fun tileIcon(id: String, size: Float): LayoutElementBuilders.Image =
        LayoutElementBuilders.Image.Builder()
            .setResourceId(id)
            .setWidth(DimensionBuilders.dp(size))
            .setHeight(DimensionBuilders.dp(size))
            .build()

    private fun horizontalGap(width: Float): LayoutElementBuilders.Spacer = LayoutElementBuilders.Spacer.Builder()
        .setWidth(DimensionBuilders.dp(width)).build()

    private fun verticalGap(height: Float): LayoutElementBuilders.Spacer = LayoutElementBuilders.Spacer.Builder()
        .setHeight(DimensionBuilders.dp(height)).build()

    private fun temperatureText(text: String, size: Float, color: Int): LayoutElementBuilders.Text =
        LayoutElementBuilders.Text.Builder()
            .setText(text)
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(DimensionBuilders.sp(size))
                    .setColor(ColorBuilders.argb(color))
                    .build(),
            ).build()

    private fun nice(value: Float): String =
        if (value % 1f == 0f) value.roundToInt().toString() else "%.1f".format(java.util.Locale.US, value)
}
