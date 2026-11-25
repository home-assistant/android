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
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplay
import io.homeassistant.companion.android.database.wear.ThermostatTile
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
import kotlin.math.roundToInt

internal object ThermostatTileIds {
    const val TAP_ACTION_UP = "Up"
    const val TAP_ACTION_DOWN = "Down"
    const val TAP_MODE_PREFIX = "Mode:"
    const val TAP_FAN = "CycleFan"
    const val TAP_POWER_ON = "PowerOn"
    const val TAP_POWER_OFF = "PowerOff"

    // Stable ProtoLayout resource IDs; the tile's resource version changes only when icons change.
    const val ICON_HEAT = "thermostat_heat_v1"
    const val ICON_COOL = "thermostat_cool_v1"
    const val ICON_AUTO_HEAT = "thermostat_auto_heat_v1"
    const val ICON_AUTO_COOL = "thermostat_auto_cool_v1"
    const val ICON_FAN = "thermostat_fan_v1"
    const val ICON_POWER = "thermostat_power_v1"
}

/** Builds thermostat timelines without performing network requests or writing configuration. */
internal class ThermostatTileViews(private val context: Context) {

    /**
     * Original Home Assistant thermostat layout, retained as the Classic appearance.
     * Receives the same entity state and pending command as the Enhanced layout.
     */
    fun classicTimeline(
        tileConfig: ThermostatTile,
        entity: EntityDisplay,
        temperatureUnit: String,
        pending: ThermostatCommandCoordinator.Pending?,
        actionPrefix: String,
    ): Timeline {
        val entityName = if (tileConfig.showEntityName == true) entity.name else ""
        val controls = entity.climateControls
        val showPower = tileConfig.showPowerButton &&
            (controls?.supportsTurnOn == true || controls?.supportsTurnOff == true)
        val available = entity.rawState !in listOf("unavailable", "unknown")
        val powerOn = entity.rawState != "off"
        val powerEnabled = available &&
            pending?.status != ThermostatCommandCoordinator.Pending.Status.WAITING &&
            (if (powerOn) controls?.supportsTurnOff == true else controls?.supportsTurnOn == true)
        val temperatureEnabled = available &&
            powerOn &&
            controls?.targetTemperature != null &&
            !(
                pending?.status == ThermostatCommandCoordinator.Pending.Status.WAITING &&
                    pending.value in listOf("on", "off")
                )
        val targetTemperature = pending?.single
            ?.takeIf { pending.status == ThermostatCommandCoordinator.Pending.Status.WAITING }
            ?: entity.climateControls?.targetTemperature
        return Timeline.fromLayoutElement(
            LayoutElementBuilders.Box.Builder().apply {
                val currentTemperature = entity.climateControls?.currentTemperature
                val hvacAction = controls?.hvacAction.orEmpty()

                val hvacActionColor = when (hvacAction) {
                    "heating" -> context.getColor(commonR.color.colorDeviceControlsThermostatHeat)
                    "cooling" -> context.getColor(commonR.color.colorDeviceControlsDefaultOn)
                    else -> 0x00000000
                }

                val friendlyHvacAction = stateLabel(hvacAction.ifBlank { entity.rawState })

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
                                .addContent(getTempButton(temperatureEnabled, TAP_ACTION_DOWN, actionPrefix, showPower))
                                .apply {
                                    if (showPower) {
                                        addContent(horizontalGap(6f))
                                        addContent(classicPowerButton(powerOn, powerEnabled, actionPrefix))
                                        addContent(horizontalGap(6f))
                                    } else {
                                        addContent(horizontalGap(20f))
                                    }
                                }
                                .addContent(getTempButton(temperatureEnabled, TAP_ACTION_UP, actionPrefix, showPower))
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

    private fun classicPowerButton(powerOn: Boolean, enabled: Boolean, actionPrefix: String): LayoutElement {
        val clickable = Clickable.Builder()
        if (enabled) {
            clickable.setOnClick(ActionBuilders.LoadAction.Builder().build())
                .setId(actionPrefix + (if (powerOn) TAP_POWER_OFF else TAP_POWER_ON))
        }
        return Button.Builder(context, clickable.build())
            .setSize(40f)
            .setIconContent(ICON_POWER)
            .setContentDescription(
                context.getString(
                    if (powerOn) commonR.string.thermostat_tile_turn_off else commonR.string.thermostat_tile_turn_on,
                ),
            )
            .setButtonColors(
                ButtonColors(
                    ColorBuilders.argb(
                        context.getColor(
                            if (enabled &&
                                powerOn
                            ) {
                                commonR.color.colorPrimary
                            } else {
                                commonR.color.colorDeviceControlsOff
                            },
                        ),
                    ),
                    ColorBuilders.argb(0xFFFFFFFF.toInt()),
                ),
            )
            .build()
    }

    private fun getTempButton(enabled: Boolean, action: String, actionPrefix: String, compact: Boolean): LayoutElement {
        val clickable = Clickable.Builder()
        if (enabled) {
            clickable.setOnClick(ActionBuilders.LoadAction.Builder().build())
                .setId(actionPrefix + action)
        }

        return Button.Builder(context, clickable.build())
            .apply { if (compact) setSize(40f) }
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
     * Two setpoint rows are shown only in heat_cool. Secondary adjustments share each row
     * with the primary +/-1 controls, but the mode and utility actions have their own rows
     * so they cannot run off the edges of a circular display.
     */
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
        val active = state !in listOf("off", "unavailable", "unknown")
        val range = state == "heat_cool" &&
            controls?.targetTemperatureLow != null &&
            controls.targetTemperatureHigh != null
        val current = controls?.currentTemperature?.let { nice(it) } ?: "--"
        val hvacAction = controls?.hvacAction?.takeIf { it.isNotBlank() } ?: "idle"
        val waiting = pending?.status == ThermostatCommandCoordinator.Pending.Status.WAITING
        // Compact circular watches must not crop rows at the upper/lower edges.
        val compact = screenWidthDp < 220 || screenHeightDp < 225
        val modeHeight = if (compact) 29f else 34f
        val modeWidth = if (compact) 37f else 39f
        val targetRowHeight = if (compact) 32f else 36f

        val column = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)

        val showName = config.showEntityName == true && !(compact && range)
        if (showName) {
            column.addContent(verticalGap(16f))
        }
        column.addContent(temperatureText("$current $temperatureUnit", if (compact) 27f else 30f, 0xFFFFFFFF.toInt()))
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
            val modeColor = when (state) {
                "heat" -> 0xFFFFA19A.toInt()
                "cool" -> 0xFF8DCBFF.toInt()
                "heat_cool", "auto" -> 0xFFD9C4FF.toInt()
                else -> 0xFFBFC9D7.toInt()
            }
            val activity = when (pending?.status) {
                ThermostatCommandCoordinator.Pending.Status.WAITING -> context.getString(
                    commonR.string.thermostat_tile_waiting,
                )
                ThermostatCommandCoordinator.Pending.Status.FAILED -> context.getString(
                    commonR.string.thermostat_tile_unconfirmed,
                )
                null -> if (active) {
                    context.getString(
                        commonR.string.thermostat_tile_activity_summary,
                        displayState,
                        stateLabel(hvacAction),
                    )
                } else {
                    displayState
                }
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
                        "heat",
                        pending?.low?.takeIf { waiting } ?: controls.targetTemperatureLow,
                        context.getString(commonR.string.thermostat_tile_heat_short),
                        0xFFFFA19A.toInt(),
                        config.quickAdjustOne,
                        compact,
                        targetRowHeight,
                        actionPrefix,
                    ),
                )
                column.addContent(verticalGap(2f))
                column.addContent(
                    temperatureRow(
                        "cool",
                        pending?.high?.takeIf { waiting } ?: controls.targetTemperatureHigh,
                        context.getString(commonR.string.thermostat_tile_cool_short),
                        0xFF8DCBFF.toInt(),
                        config.quickAdjustOne,
                        compact,
                        targetRowHeight,
                        actionPrefix,
                    ),
                )
            } else if (controls.targetTemperature != null) {
                val accent = if (state == "cool") 0xFF8DCBFF.toInt() else 0xFFFFA19A.toInt()
                column.addContent(
                    temperatureRow(
                        "single",
                        pending?.single?.takeIf { waiting } ?: controls.targetTemperature,
                        "",
                        accent,
                        config.quickAdjustOne,
                        compact,
                        targetRowHeight,
                        actionPrefix,
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
                    0xFF9A3D40.toInt(),
                )
                addMode(
                    "cool",
                    ICON_COOL,
                    context.getString(commonR.string.thermostat_tile_cool_mode),
                    0xFF286FAD.toInt(),
                )
                addMode(
                    if ("heat_cool" in available) "heat_cool" else "auto",
                    ICON_AUTO_HEAT,
                    context.getString(
                        if ("heat_cool" in
                            available
                        ) {
                            commonR.string.thermostat_tile_heat_cool_mode
                        } else {
                            commonR.string.thermostat_tile_auto_mode
                        },
                    ),
                    0xFF6961A6.toInt(),
                    ICON_AUTO_COOL,
                )
            }
            if (hasMode) column.addContent(modeRow.build())

            val utilityRow = LayoutElementBuilders.Row.Builder()
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            if (config.showFanControl && controls.fanModes.isNotEmpty()) {
                val on = controls.fanMode != null && controls.fanMode != "auto"
                utilityRow.addContent(
                    iconButton(
                        ICON_FAN,
                        actionPrefix + TAP_FAN,
                        on,
                        modeWidth,
                        modeHeight,
                        0xFF277766.toInt(),
                        context.getString(
                            commonR.string.thermostat_tile_fan_mode,
                            stateLabel(controls.fanMode.orEmpty()),
                        ),
                    ),
                )
                utilityRow.addContent(horizontalGap(if (compact) 4f else 5f))
            }
            if (config.showHvacModes && "off" in available) {
                utilityRow.addContent(
                    iconButton(
                        ICON_POWER,
                        actionPrefix + TAP_MODE_PREFIX + "off",
                        state == "off",
                        modeWidth,
                        modeHeight,
                        0xFF7A4355.toInt(),
                        context.getString(commonR.string.thermostat_tile_turn_off),
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
                .apply {
                    if (showName) {
                        addContent(
                            LayoutElementBuilders.Arc.Builder()
                                .setAnchorAngle(DimensionBuilders.DegreesProp.Builder(180f).build())
                                .setAnchorType(LayoutElementBuilders.ARC_ANCHOR_CENTER)
                                .addContent(
                                    LayoutElementBuilders.ArcLine.Builder()
                                        .setLength(DimensionBuilders.DegreesProp.Builder(360f).build())
                                        .setThickness(DimensionBuilders.DpProp.Builder(24f).build())
                                        .setColor(ColorBuilders.argb(0x00000000))
                                        .build(),
                                )
                                .addContent(
                                    LayoutElementBuilders.ArcText.Builder()
                                        .setText(entity.name)
                                        .setFontStyle(
                                            LayoutElementBuilders.FontStyle.Builder()
                                                .setSize(DimensionBuilders.sp(if (compact) 12f else 13f))
                                                .setColor(ColorBuilders.argb(0xFFCBD5E1.toInt()))
                                                .build(),
                                        )
                                        .build(),
                                )
                                .build(),
                        )
                    }
                }
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
        actionPrefix: String,
    ): LayoutElementBuilders.LayoutElement {
        val extra = secondaryStep.coerceIn(2, 10)
        val targetDescription = context.getString(
            when (slot) {
                "heat" -> commonR.string.thermostat_tile_heat_target
                "cool" -> commonR.string.thermostat_tile_cool_target
                else -> commonR.string.thermostat_tile_target
            },
        )
        fun adjustmentDescription(amount: Int): String = context.getString(
            if (amount < 0) commonR.string.thermostat_tile_decrease else commonR.string.thermostat_tile_increase,
            targetDescription,
            kotlin.math.abs(amount),
        )
        val outerWidth = if (compact) 29f else 32f
        val primaryWidth = if (compact) 33f else 37f
        val valueWidth = if (compact) 42f else 48f
        val gap = if (compact) 1f else 2f
        val row = LayoutElementBuilders.Row.Builder()
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
        row.addContent(
            button(
                "−$extra",
                actionPrefix + "Temp:$slot:${-extra}",
                false,
                outerWidth,
                height,
                adjustmentDescription(-extra),
            ),
        )
        row.addContent(horizontalGap(gap))
        row.addContent(
            button("−", actionPrefix + "Temp:$slot:-1", false, primaryWidth, height, adjustmentDescription(-1)),
        )
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
            button("+", actionPrefix + "Temp:$slot:1", false, primaryWidth, height, adjustmentDescription(1)),
        )
        row.addContent(horizontalGap(gap))
        row.addContent(
            button(
                "+$extra",
                actionPrefix + "Temp:$slot:$extra",
                false,
                outerWidth,
                height,
                adjustmentDescription(extra),
            ),
        )
        return row.build()
    }

    private fun button(
        label: String,
        action: String,
        selected: Boolean,
        width: Float,
        height: Float,
        description: String,
        activeColor: Int = 0xFF35445C.toInt(),
    ): LayoutElementBuilders.LayoutElement = LayoutElementBuilders.Box.Builder()
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
                    Clickable.Builder().setId(
                        action,
                    ).setOnClick(ActionBuilders.LoadAction.Builder().build()).build(),
                )
                .setSemantics(ModifiersBuilders.Semantics.Builder().setContentDescription(description).build())
                .build(),
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
                        Clickable.Builder().setId(
                            action,
                        ).setOnClick(ActionBuilders.LoadAction.Builder().build()).build(),
                    )
                    .setSemantics(
                        ModifiersBuilders.Semantics.Builder()
                            .setContentDescription(
                                if (selected) {
                                    context.getString(
                                        commonR.string.thermostat_tile_selected,
                                        description,
                                    )
                                } else {
                                    description
                                },
                            )
                            .build(),
                    )
                    .build(),
            )
            .build()
    }

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
