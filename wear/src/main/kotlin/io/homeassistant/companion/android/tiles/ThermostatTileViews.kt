package io.homeassistant.companion.android.tiles

import android.content.Context
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ModifiersBuilders.Clickable
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.protolayout.material.Button
import androidx.wear.protolayout.material.ButtonColors
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.integration.ClimateControls
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplay
import io.homeassistant.companion.android.database.wear.ThermostatTile
import io.homeassistant.companion.android.tiles.ThermostatTileIds.ICON_COOL
import io.homeassistant.companion.android.tiles.ThermostatTileIds.ICON_ENHANCED_POWER
import io.homeassistant.companion.android.tiles.ThermostatTileIds.ICON_FAN
import io.homeassistant.companion.android.tiles.ThermostatTileIds.ICON_FAN_OFF
import io.homeassistant.companion.android.tiles.ThermostatTileIds.ICON_HEAT
import io.homeassistant.companion.android.tiles.ThermostatTileIds.TAP_ACTION_DOWN
import io.homeassistant.companion.android.tiles.ThermostatTileIds.TAP_ACTION_UP
import io.homeassistant.companion.android.tiles.ThermostatTileIds.TAP_FAN
import io.homeassistant.companion.android.tiles.ThermostatTileIds.TAP_MODE_PREFIX
import kotlin.math.roundToInt

private const val ACTIVITY_TEXT_SP = 11f
private const val BUTTON_CORNER_DP = 14f
private const val BUTTON_TEXT_SP = 14f
private const val CLASSIC_BUTTON_GAP_DP = 20f
private const val CLASSIC_CONTROL_GAP_DP = 10f
private const val CLASSIC_NAME_ARC_DP = 30f
private const val CLASSIC_RANGE_TEXT_SP = 22f
private const val CLASSIC_TARGET_TEXT_SP = 30f
private const val AUTO_CURRENT_TEXT_SP = 26f
private const val COMPACT_ACTIVITY_TEXT_SP = 10f
private const val COMPACT_AUTO_CURRENT_TEXT_SP = 24f
private const val COMPACT_CONTROL_GAP_DP = 4f
private const val COMPACT_CURRENT_TEXT_SP = 27f
private const val COMPACT_NAME_TEXT_SP = 12f
private const val COMPACT_RANGE_BOTTOM_CLEARANCE_DP = 8f
private const val CONTROL_GAP_DP = 5f
private const val CONTROL_SECTION_GAP_DP = 5f
private const val CURRENT_TEXT_SP = 30f
private const val ENHANCED_NAME_ARC_DP = 24f
private const val ENHANCED_NAME_GAP_DP = 16f
private const val FULL_CIRCLE_DEGREES = 360f
private const val NAME_TEXT_SP = 13f
private const val RANGE_BOTTOM_CLEARANCE_DP = 12f
private const val ROW_GAP_DP = 3f
private const val SETPOINT_TEXT_SP = 16f
private const val TOP_ARC_ANCHOR_DEGREES = 180f

private const val COMPACT_MODE_HEIGHT_DP = 29f
private const val MODE_HEIGHT_DP = 34f
private const val COMPACT_MODE_WIDTH_DP = 37f
private const val MODE_WIDTH_DP = 39f
private const val COMPACT_TARGET_ROW_HEIGHT_DP = 32f
private const val TARGET_ROW_HEIGHT_DP = 36f

private const val COMPACT_WIDTH_THRESHOLD_DP = 220
private const val COMPACT_HEIGHT_THRESHOLD_DP = 225
private const val COMPACT_ROW_GAP_DP = 2f
private const val SETPOINT_ROW_GAP_DP = 2f
private const val COMPACT_SECONDARY_WIDTH_DP = 29f
private const val SECONDARY_WIDTH_DP = 32f
private const val COMPACT_PRIMARY_WIDTH_DP = 33f
private const val PRIMARY_WIDTH_DP = 37f
private const val COMPACT_VALUE_WIDTH_DP = 42f
private const val VALUE_WIDTH_DP = 48f
private const val COMPACT_SETPOINT_GAP_DP = 1f
private const val SETPOINT_GAP_DP = 2f
private const val SINGLE_ICON_DP = 25f
private const val AUTO_ICON_DP = 17f
private const val AUTO_ICON_GAP_DP = 1f
private const val CLASSIC_ACTIVITY_ARC_DP = 2f

private const val TRANSPARENT_COLOR = 0x00000000
private const val PRIMARY_TEXT_COLOR = 0xFFFFFFFF.toInt()
private const val NAME_TEXT_COLOR = 0xFFCBD5E1.toInt()
private const val WAITING_TEXT_COLOR = 0xFFFFD778.toInt()
private const val FAILED_TEXT_COLOR = 0xFFFF9797.toInt()
private const val HEAT_TEXT_COLOR = 0xFFFFA19A.toInt()
private const val COOL_TEXT_COLOR = 0xFF8DCBFF.toInt()
private const val AUTO_TEXT_COLOR = 0xFFD9C4FF.toInt()
private const val IDLE_TEXT_COLOR = 0xFFBFC9D7.toInt()
private const val HEAT_SELECTED_COLOR = 0xFF9A3D40.toInt()
private const val COOL_SELECTED_COLOR = 0xFF286FAD.toInt()
private const val AUTO_SELECTED_COLOR = 0xFF6961A6.toInt()
private const val FAN_SELECTED_COLOR = 0xFF277766.toInt()
private const val OFF_SELECTED_COLOR = 0xFF7A4355.toInt()
private const val BUTTON_BACKGROUND_COLOR = 0xFF293447.toInt()

private data class ButtonSize(val width: Float, val height: Float)

internal object ThermostatTileIds {
    const val TAP_ACTION_UP = "Up"
    const val TAP_ACTION_DOWN = "Down"
    const val TAP_MODE_PREFIX = "Mode:"
    const val TAP_FAN = "CycleFan"
    const val INVITATION_ACCEPT = "thermostat_invitation_accept"
    const val INVITATION_DECLINE = "thermostat_invitation_decline"

    // Stable ProtoLayout resource IDs; the tile's resource version changes only when icons change.
    const val ICON_HEAT = "thermostat_heat_v1"
    const val ICON_COOL = "thermostat_cool_v1"
    const val ICON_FAN = "thermostat_fan_v1"
    const val ICON_FAN_OFF = "thermostat_fan_off_v1"
    const val ICON_ENHANCED_POWER = "thermostat_enhanced_power_v1"
}

/** Builds thermostat timelines without performing network requests or writing configuration. */
// Keep the two layouts and their shared ProtoLayout builders together; no network or mutable state.
@Suppress("TooManyFunctions", "LargeClass")
internal class ThermostatTileViews(private val context: Context) {

    /**
     * Original Home Assistant thermostat layout, retained as the Classic appearance.
     * Receives the same entity state and pending command as the Enhanced layout.
     */
    // Retain the original Classic layout hierarchy and accessibility behavior.
    @Suppress("CyclomaticComplexMethod", "LongMethod")
    fun classicTimeline(
        tileConfig: ThermostatTile,
        entity: EntityDisplay,
        temperatureUnit: String,
        pending: ThermostatCommandCoordinator.Pending?,
        actionPrefix: String,
    ): Timeline {
        val entityName = if (tileConfig.showEntityName == true) entity.name else ""
        val controls = entity.climateControls
        val units = ThermostatDisplayUnits(temperatureUnit, tileConfig.temperatureUnit)
        val available = entity.rawState !in listOf("off", "unavailable", "unknown")
        val classicRangeMode = entity.rawState == "heat_cool"
        val rangeAvailable = controls?.targetTemperatureLow != null && controls.targetTemperatureHigh != null
        val temperatureEnabled = available &&
            (if (classicRangeMode) rangeAvailable else controls?.targetTemperature != null)
        val targetTemperature = if (classicRangeMode) {
            val waiting = pending?.takeIf { it.status == ThermostatCommandCoordinator.Pending.Status.WAITING }
            val low = units.display(waiting?.low ?: controls?.targetTemperatureLow)
            val high = units.display(waiting?.high ?: controls?.targetTemperatureHigh)
            if (low != null && high != null) "$low–$high" else null
        } else {
            units.display(
                pending?.single?.takeIf { pending.status == ThermostatCommandCoordinator.Pending.Status.WAITING }
                    ?: controls?.targetTemperature,
            )
        }
        return Timeline.fromLayoutElement(
            LayoutElementBuilders.Box.Builder().apply {
                val currentTemperature = units.display(controls?.currentTemperature)
                val hvacAction = controls?.hvacAction.orEmpty()

                val hvacActionColor = when (hvacAction) {
                    "heating" -> context.getColor(commonR.color.colorDeviceControlsThermostatHeat)
                    "cooling" -> context.getColor(commonR.color.colorDeviceControlsDefaultOn)
                    else -> TRANSPARENT_COLOR
                }

                val friendlyHvacAction = stateLabel(hvacAction.ifBlank { entity.rawState })

                addContent(
                    LayoutElementBuilders.Column.Builder()
                        .addContent(
                            LayoutElementBuilders.Text.Builder()
                                .setText(pendingStatusText(pending) ?: friendlyHvacAction)
                                .build(),
                        )
                        .addContent(
                            LayoutElementBuilders.Text.Builder()
                                .setText(
                                    if (targetTemperature ==
                                        null
                                    ) {
                                        "-- ${units.symbol}"
                                    } else {
                                        "$targetTemperature ${units.symbol}"
                                    },
                                )
                                .setFontStyle(
                                    LayoutElementBuilders.FontStyle.Builder().setSize(
                                        DimensionBuilders.sp(
                                            if (classicRangeMode) CLASSIC_RANGE_TEXT_SP else CLASSIC_TARGET_TEXT_SP,
                                        ),
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
                                        "-- ${units.symbol}"
                                    } else {
                                        "$currentTemperature ${units.symbol}"
                                    },
                                )
                                .build(),
                        )
                        .addContent(
                            LayoutElementBuilders.Spacer.Builder()
                                .setHeight(DimensionBuilders.dp(CLASSIC_CONTROL_GAP_DP)).build(),
                        )
                        .addContent(
                            LayoutElementBuilders.Row.Builder()
                                .addContent(getTempButton(temperatureEnabled, TAP_ACTION_DOWN, actionPrefix))
                                .addContent(horizontalGap(CLASSIC_BUTTON_GAP_DP))
                                .addContent(getTempButton(temperatureEnabled, TAP_ACTION_UP, actionPrefix))
                                .build(),
                        )
                        .build(),
                )
                addContent(
                    LayoutElementBuilders.Arc.Builder()
                        .addContent(
                            LayoutElementBuilders.ArcLine.Builder()
                                .setLength(DimensionBuilders.DegreesProp.Builder(FULL_CIRCLE_DEGREES).build())
                                .setThickness(DimensionBuilders.DpProp.Builder(CLASSIC_ACTIVITY_ARC_DP).build())
                                .setColor(ColorBuilders.argb(hvacActionColor))
                                .build(),
                        )
                        .build(),
                )
                if (tileConfig.showEntityName == true) {
                    addContent(
                        LayoutElementBuilders.Arc.Builder()
                            .setAnchorAngle(
                                DimensionBuilders.DegreesProp.Builder(TOP_ARC_ANCHOR_DEGREES).build(),
                            )
                            .setAnchorType(LayoutElementBuilders.ARC_ANCHOR_CENTER)
                            .addContent(
                                LayoutElementBuilders.ArcLine.Builder()
                                    .setLength(DimensionBuilders.DegreesProp.Builder(FULL_CIRCLE_DEGREES).build())
                                    .setThickness(DimensionBuilders.DpProp.Builder(CLASSIC_NAME_ARC_DP).build())
                                    .setColor(ColorBuilders.argb(TRANSPARENT_COLOR)) // Fully transparent
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

    private fun getTempButton(enabled: Boolean, action: String, actionPrefix: String): LayoutElement {
        val clickable = Clickable.Builder()
        if (enabled) {
            clickable.setOnClick(ActionBuilders.LoadAction.Builder().build())
                .setId(actionPrefix + action)
        }

        return Button.Builder(context, clickable.build())
            .setTextContent(if (action == TAP_ACTION_DOWN) "−" else "+")
            .setContentDescription(
                context.getString(
                    if (action ==
                        TAP_ACTION_DOWN
                    ) {
                        commonR.string.thermostat_tile_decrease_target
                    } else {
                        commonR.string.thermostat_tile_increase_target
                    },
                ),
            )
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

    /**
     * Enhanced direct controls. Actions are handled by the tile service via LoadAction.
     *
     * Two setpoint rows are shown only in heat_cool. Optional secondary adjustments share each row
     * with the primary +/-1 controls, but the mode and utility actions have their own rows
     * so they cannot run off the edges of a circular display.
     */
    // Tile configuration, entity state and screen dimensions come directly from the renderer.
    @Suppress("LongParameterList")
    fun enhancedTimeline(
        config: ThermostatTile,
        entity: EntityDisplay,
        temperatureUnit: String,
        pending: ThermostatCommandCoordinator.Pending?,
        actionPrefix: String,
        screenWidthDp: Int,
        screenHeightDp: Int,
    ): Timeline {
        val controls = entity.climateControls
        val state = entity.rawState
        val range = state == "heat_cool" &&
            controls?.targetTemperatureLow != null &&
            controls.targetTemperatureHigh != null
        val units = ThermostatDisplayUnits(temperatureUnit, config.temperatureUnit)
        val current = units.display(controls?.currentTemperature)?.let { nice(it) } ?: "--"
        // Compact circular watches must not crop rows at the upper/lower edges.
        val compact = screenWidthDp < COMPACT_WIDTH_THRESHOLD_DP || screenHeightDp < COMPACT_HEIGHT_THRESHOLD_DP
        val layout = EnhancedLayout(config, entity, pending, actionPrefix, compact, units)
        val column = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
        val showName = config.showEntityName == true && !(compact && range)
        if (showName) {
            column.addContent(verticalGap(ENHANCED_NAME_GAP_DP))
        }
        column.addContent(
            temperatureText(
                units.enhancedText(current),
                enhancedCurrentTextSize(state, compact),
                PRIMARY_TEXT_COLOR,
            ),
        )
        addActivity(column, layout)
        column.addContent(verticalGap(if (compact) COMPACT_ROW_GAP_DP else ROW_GAP_DP))
        addSetpoints(column, layout, range)
        addControlRows(column, layout, controls)
        addRangeBottomClearance(column, range, compact)

        return Timeline.fromLayoutElement(
            LayoutElementBuilders.Box.Builder()
                .setWidth(DimensionBuilders.expand())
                .setHeight(DimensionBuilders.expand())
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .addContent(column.build())
                .setModifiers(getRefreshModifiers())
                .apply {
                    if (showName) {
                        addContent(
                            enhancedNameArc(entity.name, compact),
                        )
                    }
                }
                .build(),
        )
    }

    private fun enhancedCurrentTextSize(state: String, compact: Boolean): Float = when (state) {
        "heat_cool", "auto" -> if (compact) COMPACT_AUTO_CURRENT_TEXT_SP else AUTO_CURRENT_TEXT_SP
        else -> if (compact) COMPACT_CURRENT_TEXT_SP else CURRENT_TEXT_SP
    }

    private fun addRangeBottomClearance(
        column: LayoutElementBuilders.Column.Builder,
        range: Boolean,
        compact: Boolean,
    ) {
        if (range) {
            // Reserve clearance from the lower round-screen edge without shrinking touch targets.
            val clearance = if (compact) COMPACT_RANGE_BOTTOM_CLEARANCE_DP else RANGE_BOTTOM_CLEARANCE_DP
            column.addContent(verticalGap(clearance))
        }
    }

    private fun addControlRows(
        column: LayoutElementBuilders.Column.Builder,
        layout: EnhancedLayout,
        controls: ClimateControls?,
    ) {
        if (layout.entity.rawState in listOf("unavailable", "unknown")) return
        if (controls != null && (layout.config.showHvacModes || layout.config.showFanControl)) {
            column.addContent(verticalGap(if (layout.compact) COMPACT_ROW_GAP_DP else CONTROL_SECTION_GAP_DP))
            addModes(column, layout, controls)
            addUtilities(column, layout, controls)
        }
    }

    private fun enhancedNameArc(name: String, compact: Boolean): LayoutElementBuilders.Arc =
        LayoutElementBuilders.Arc.Builder()
            .setAnchorAngle(DimensionBuilders.DegreesProp.Builder(TOP_ARC_ANCHOR_DEGREES).build())
            .setAnchorType(LayoutElementBuilders.ARC_ANCHOR_CENTER)
            .addContent(
                LayoutElementBuilders.ArcLine.Builder()
                    .setLength(DimensionBuilders.DegreesProp.Builder(FULL_CIRCLE_DEGREES).build())
                    .setThickness(DimensionBuilders.DpProp.Builder(ENHANCED_NAME_ARC_DP).build())
                    .setColor(ColorBuilders.argb(TRANSPARENT_COLOR))
                    .build(),
            )
            .addContent(
                LayoutElementBuilders.ArcText.Builder()
                    .setText(name)
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setSize(
                                DimensionBuilders.sp(
                                    if (compact) COMPACT_NAME_TEXT_SP else NAME_TEXT_SP,
                                ),
                            )
                            .setColor(ColorBuilders.argb(NAME_TEXT_COLOR))
                            .build(),
                    )
                    .build(),
            )
            .build()

    private data class EnhancedLayout(
        val config: ThermostatTile,
        val entity: EntityDisplay,
        val pending: ThermostatCommandCoordinator.Pending?,
        val actionPrefix: String,
        val compact: Boolean,
        val units: ThermostatDisplayUnits,
    ) {
        val modeHeight: Float get() = if (compact) COMPACT_MODE_HEIGHT_DP else MODE_HEIGHT_DP
        val modeWidth: Float get() = if (compact) COMPACT_MODE_WIDTH_DP else MODE_WIDTH_DP
        val targetRowHeight: Float get() = if (compact) COMPACT_TARGET_ROW_HEIGHT_DP else TARGET_ROW_HEIGHT_DP
        val secondaryStep: Int? get() = config.secondaryStep.takeIf { config.showSecondaryButtons }
    }

    private fun pendingStatusText(pending: ThermostatCommandCoordinator.Pending?): String? = when (pending?.status) {
        ThermostatCommandCoordinator.Pending.Status.WAITING -> context.getString(commonR.string.thermostat_tile_waiting)
        ThermostatCommandCoordinator.Pending.Status.FAILED -> context.getString(
            commonR.string.thermostat_tile_unconfirmed,
        )
        null -> null
    }

    private fun addActivity(column: LayoutElementBuilders.Column.Builder, layout: EnhancedLayout) {
        val config = layout.config
        val compact = layout.compact
        val pending = layout.pending
        val state = layout.entity.rawState
        val hvacAction = layout.entity.climateControls?.hvacAction?.takeIf { it.isNotBlank() } ?: "idle"
        if (config.showActivity || pending != null) {
            val displayState = stateLabel(
                if (state ==
                    "heat_cool"
                ) {
                    "auto"
                } else {
                    state
                },
            ).uppercase(java.util.Locale.getDefault())
            val modeColor = activityModeColor(state)
            val activity = pendingStatusText(pending) ?: if (showHvacAction(state, hvacAction)) {
                context.getString(
                    commonR.string.thermostat_tile_activity_summary,
                    displayState,
                    stateLabel(hvacAction),
                )
            } else {
                displayState
            }
            val activityColor = when (pending?.status) {
                ThermostatCommandCoordinator.Pending.Status.WAITING -> WAITING_TEXT_COLOR
                ThermostatCommandCoordinator.Pending.Status.FAILED -> FAILED_TEXT_COLOR
                null -> modeColor
            }
            column.addContent(
                temperatureText(activity, if (compact) COMPACT_ACTIVITY_TEXT_SP else ACTIVITY_TEXT_SP, activityColor),
            )
        }
    }

    private fun showHvacAction(state: String, hvacAction: String): Boolean = when (state) {
        "off" -> hvacAction == "fan" || hvacAction == "fan_only"
        "unavailable", "unknown" -> false
        else -> true
    }

    private fun activityModeColor(state: String): Int = when (state) {
        "heat" -> HEAT_TEXT_COLOR
        "cool" -> COOL_TEXT_COLOR
        "heat_cool", "auto" -> AUTO_TEXT_COLOR
        else -> IDLE_TEXT_COLOR
    }

    private fun addSetpoints(column: LayoutElementBuilders.Column.Builder, layout: EnhancedLayout, range: Boolean) {
        val compact = layout.compact
        val controls = layout.entity.climateControls
        val state = layout.entity.rawState
        val active = state !in listOf("off", "unavailable", "unknown")
        val pending = layout.pending?.takeIf { it.status == ThermostatCommandCoordinator.Pending.Status.WAITING }
        val targetRowHeight = layout.targetRowHeight
        val actionPrefix = layout.actionPrefix
        if (active && controls != null) {
            if (range) {
                column.addContent(
                    temperatureRow(
                        "heat",
                        layout.units.display(pending?.low ?: controls.targetTemperatureLow),
                        context.getString(commonR.string.thermostat_tile_heat_short),
                        HEAT_TEXT_COLOR,
                        layout.secondaryStep,
                        compact,
                        targetRowHeight,
                        actionPrefix,
                    ),
                )
                column.addContent(verticalGap(SETPOINT_ROW_GAP_DP))
                column.addContent(
                    temperatureRow(
                        "cool",
                        layout.units.display(pending?.high ?: controls.targetTemperatureHigh),
                        context.getString(commonR.string.thermostat_tile_cool_short),
                        COOL_TEXT_COLOR,
                        layout.secondaryStep,
                        compact,
                        targetRowHeight,
                        actionPrefix,
                    ),
                )
            } else if (controls.targetTemperature != null) {
                val accent = if (state == "cool") COOL_TEXT_COLOR else HEAT_TEXT_COLOR
                column.addContent(
                    temperatureRow(
                        "single",
                        layout.units.display(pending?.single ?: controls.targetTemperature),
                        "",
                        accent,
                        layout.secondaryStep,
                        compact,
                        targetRowHeight,
                        actionPrefix,
                    ),
                )
            }
        }
    }

    private fun addModes(
        column: LayoutElementBuilders.Column.Builder,
        layout: EnhancedLayout,
        controls: ClimateControls,
    ) {
        val config = layout.config
        val compact = layout.compact
        val state = layout.entity.rawState
        val actionPrefix = layout.actionPrefix
        val modeWidth = layout.modeWidth
        val modeHeight = layout.modeHeight
        val available = controls.hvacModes
        val modeRow = LayoutElementBuilders.Row.Builder()
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
        var hasMode = false
        fun addMode(mode: String, iconId: String, description: String, color: Int, secondIconId: String? = null) {
            if (mode !in available) return
            if (hasMode) modeRow.addContent(horizontalGap(if (compact) COMPACT_CONTROL_GAP_DP else CONTROL_GAP_DP))
            modeRow.addContent(
                iconButton(
                    iconId,
                    actionPrefix + TAP_MODE_PREFIX + mode,
                    state == mode,
                    modeWidth,
                    modeHeight,
                    color,
                    description,
                    secondIconId,
                ),
            )
            hasMode = true
        }
        if (config.showHvacModes) {
            addMode(
                "heat",
                ICON_HEAT,
                context.getString(commonR.string.thermostat_tile_heat_mode),
                HEAT_SELECTED_COLOR,
            )
            addMode(
                "cool",
                ICON_COOL,
                context.getString(commonR.string.thermostat_tile_cool_mode),
                COOL_SELECTED_COLOR,
            )
            addMode(
                if ("heat_cool" in available) "heat_cool" else "auto",
                ICON_HEAT,
                context.getString(
                    if ("heat_cool" in
                        available
                    ) {
                        commonR.string.thermostat_tile_heat_cool_mode
                    } else {
                        commonR.string.thermostat_tile_auto_mode
                    },
                ),
                AUTO_SELECTED_COLOR,
                ICON_COOL,
            )
        }
        if (hasMode) column.addContent(modeRow.build())
    }

    private fun addUtilities(
        column: LayoutElementBuilders.Column.Builder,
        layout: EnhancedLayout,
        controls: ClimateControls,
    ) {
        val config = layout.config
        val compact = layout.compact
        val state = layout.entity.rawState
        val actionPrefix = layout.actionPrefix
        val modeWidth = layout.modeWidth
        val modeHeight = layout.modeHeight
        val available = controls.hvacModes
        val utilityRow = LayoutElementBuilders.Row.Builder()
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
        val showFan = config.showFanControl && controls.fanModes.isNotEmpty()
        val showOff = config.showHvacModes && "off" in available
        if (showFan) {
            val on = controls.fanMode != null && controls.fanMode !in listOf("auto", "off")
            utilityRow.addContent(
                iconButton(
                    if (on) ICON_FAN else ICON_FAN_OFF,
                    actionPrefix + TAP_FAN,
                    on,
                    modeWidth,
                    modeHeight,
                    FAN_SELECTED_COLOR,
                    context.getString(
                        commonR.string.thermostat_tile_fan_mode,
                        stateLabel(controls.fanMode.orEmpty()),
                    ),
                ),
            )
            utilityRow.addContent(horizontalGap(if (compact) COMPACT_CONTROL_GAP_DP else CONTROL_GAP_DP))
        }
        if (showOff) {
            utilityRow.addContent(
                iconButton(
                    ICON_ENHANCED_POWER,
                    actionPrefix + TAP_MODE_PREFIX + "off",
                    state == "off",
                    modeWidth,
                    modeHeight,
                    OFF_SELECTED_COLOR,
                    context.getString(commonR.string.thermostat_tile_turn_off),
                ),
            )
        }
        if (showFan || showOff) {
            column.addContent(verticalGap(if (compact) COMPACT_ROW_GAP_DP else ROW_GAP_DP))
            column.addContent(utilityRow.build())
        }
    }

    /** One centered row per setpoint, with optional outer large-step buttons. */
    // Explicit geometry and accessibility parameters keep row rendering deterministic.
    @Suppress("LongParameterList", "LongMethod")
    private fun temperatureRow(
        slot: String,
        target: Float?,
        prefix: String,
        accent: Int,
        secondaryStep: Int?,
        compact: Boolean,
        height: Float,
        actionPrefix: String,
    ): LayoutElementBuilders.LayoutElement {
        val extra = secondaryStep?.coerceIn(2, 10)
        val targetDescription = context.getString(
            when (slot) {
                "heat" -> commonR.string.thermostat_tile_heat_target
                "cool" -> commonR.string.thermostat_tile_cool_target
                else -> commonR.string.thermostat_tile_target
            },
        )
        fun adjustmentDescription(amount: Int): String {
            val quantity = kotlin.math.abs(amount)
            return context.resources.getQuantityString(
                if (amount < 0) commonR.plurals.thermostat_tile_decrease else commonR.plurals.thermostat_tile_increase,
                quantity,
                targetDescription,
                quantity,
            )
        }
        val outerWidth = if (compact) COMPACT_SECONDARY_WIDTH_DP else SECONDARY_WIDTH_DP
        val primaryWidth = if (compact) COMPACT_PRIMARY_WIDTH_DP else PRIMARY_WIDTH_DP
        val valueWidth = if (compact) COMPACT_VALUE_WIDTH_DP else VALUE_WIDTH_DP
        val gap = if (compact) COMPACT_SETPOINT_GAP_DP else SETPOINT_GAP_DP
        val row = LayoutElementBuilders.Row.Builder()
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
        if (extra != null) {
            row.addContent(
                button(
                    "−$extra",
                    actionPrefix + "Temp:$slot:${-extra}",
                    outerWidth,
                    height,
                    adjustmentDescription(-extra),
                ),
            )
            row.addContent(horizontalGap(gap))
        }
        row.addContent(
            button("−", actionPrefix + "Temp:$slot:-1", primaryWidth, height, adjustmentDescription(-1)),
        )
        row.addContent(horizontalGap(gap))
        val value = target?.let { nice(it) } ?: "--"
        val shown = "$prefix$value"
        row.addContent(
            LayoutElementBuilders.Box.Builder()
                .setWidth(DimensionBuilders.dp(valueWidth))
                .setHeight(DimensionBuilders.dp(height))
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .addContent(temperatureText("$shown°", SETPOINT_TEXT_SP, accent))
                .setModifiers(
                    ModifiersBuilders.Modifiers.Builder()
                        .setSemantics(
                            ModifiersBuilders.Semantics.Builder()
                                .setContentDescription(
                                    context.getString(
                                        commonR.string.thermostat_tile_target_value,
                                        targetDescription,
                                        "$value°",
                                    ),
                                )
                                .build(),
                        ).build(),
                )
                .build(),
        )
        row.addContent(horizontalGap(gap))
        row.addContent(
            button("+", actionPrefix + "Temp:$slot:1", primaryWidth, height, adjustmentDescription(1)),
        )
        if (extra != null) {
            row.addContent(horizontalGap(gap))
            row.addContent(
                button(
                    "+$extra",
                    actionPrefix + "Temp:$slot:$extra",
                    outerWidth,
                    height,
                    adjustmentDescription(extra),
                ),
            )
        }
        return row.build()
    }

    private fun button(
        label: String,
        action: String,
        width: Float,
        height: Float,
        description: String,
    ): LayoutElement = enhancedButton(
        temperatureText(label, BUTTON_TEXT_SP, PRIMARY_TEXT_COLOR),
        action,
        ButtonSize(width, height),
        description,
        BUTTON_BACKGROUND_COLOR,
    )

    /** The five mode/utility buttons use image resources, not font-dependent symbols. */
    // ProtoLayout button geometry and action semantics are independent inputs.
    @Suppress("LongParameterList")
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
            tileIcon(iconId, SINGLE_ICON_DP)
        } else {
            // Auto is two readable symbols: red/yellow heat and blue cool.
            LayoutElementBuilders.Row.Builder()
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .addContent(tileIcon(iconId, AUTO_ICON_DP))
                .addContent(horizontalGap(AUTO_ICON_GAP_DP))
                .addContent(tileIcon(secondIconId, AUTO_ICON_DP))
                .build()
        }
        return enhancedButton(
            content,
            action,
            ButtonSize(width, height),
            if (selected) context.getString(commonR.string.thermostat_tile_selected, description) else description,
            if (selected) activeColor else BUTTON_BACKGROUND_COLOR,
        )
    }

    private fun enhancedButton(
        content: LayoutElement,
        action: String,
        size: ButtonSize,
        description: String,
        backgroundColor: Int,
    ): LayoutElement = LayoutElementBuilders.Box.Builder()
        .setWidth(DimensionBuilders.dp(size.width))
        .setHeight(DimensionBuilders.dp(size.height))
        .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
        .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
        .addContent(content)
        .setModifiers(
            ModifiersBuilders.Modifiers.Builder()
                .setBackground(
                    ModifiersBuilders.Background.Builder()
                        .setColor(ColorBuilders.argb(backgroundColor))
                        .setCorner(
                            ModifiersBuilders.Corner.Builder()
                                .setRadius(DimensionBuilders.dp(BUTTON_CORNER_DP)).build(),
                        )
                        .build(),
                )
                .setClickable(
                    Clickable.Builder()
                        .setId(action)
                        .setOnClick(ActionBuilders.LoadAction.Builder().build())
                        .build(),
                )
                .setSemantics(ModifiersBuilders.Semantics.Builder().setContentDescription(description).build())
                .build(),
        ).build()

    private fun tileIcon(id: String, size: Float): LayoutElementBuilders.Image = LayoutElementBuilders.Image.Builder()
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

    // Exhaustive state-to-localized-label mapping; branching is intentional.
    @Suppress("CyclomaticComplexMethod")
    private fun stateLabel(state: String): String = when (state) {
        "heat" -> context.getString(commonR.string.state_heat)
        "cool" -> context.getString(commonR.string.state_cool)
        "heat_cool" -> context.getString(commonR.string.state_heat_cool)
        "auto" -> context.getString(commonR.string.state_auto)
        "off" -> context.getString(commonR.string.state_off)
        "on" -> context.getString(commonR.string.state_on)
        "idle" -> context.getString(commonR.string.state_idle)
        "heating" -> context.getString(commonR.string.climate_heating)
        "cooling" -> context.getString(commonR.string.climate_cooling)
        "dry", "drying" -> context.getString(commonR.string.state_dry)
        "fan", "fan_only" -> context.getString(commonR.string.state_fan_only)
        "unavailable" -> context.getString(commonR.string.state_unavailable)
        "unknown", "" -> context.getString(commonR.string.state_unknown)
        else -> state.replace('_', ' ').replaceFirstChar { it.titlecase(java.util.Locale.getDefault()) }
    }

    private fun nice(value: Float): String =
        if (value % 1f == 0f) value.roundToInt().toString() else "%.1f".format(java.util.Locale.US, value)
}
