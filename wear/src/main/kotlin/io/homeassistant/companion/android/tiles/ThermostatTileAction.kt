package io.homeassistant.companion.android.tiles

import io.homeassistant.companion.android.common.data.integration.ClimateControls
import io.homeassistant.companion.android.database.wear.ThermostatLayoutStyle
import io.homeassistant.companion.android.database.wear.ThermostatTile
import io.homeassistant.companion.android.tiles.ThermostatCommandCoordinator.TemperatureSlot
import io.homeassistant.companion.android.tiles.ThermostatTileIds.TAP_ACTION_DOWN
import io.homeassistant.companion.android.tiles.ThermostatTileIds.TAP_ACTION_UP
import io.homeassistant.companion.android.tiles.ThermostatTileIds.TAP_FAN
import io.homeassistant.companion.android.tiles.ThermostatTileIds.TAP_MODE_PREFIX

private const val TEMPERATURE_ACTION_PARTS = 3
private const val MIN_SECONDARY_STEP = 2
private const val MAX_SECONDARY_STEP = 10

/** Resolve only the gesture; the coordinator owns the current and pending command baselines. */
internal sealed interface ThermostatTileAction {
    data class Adjust(val slot: TemperatureSlot, val amount: Float) : ThermostatTileAction
    data class Mode(val mode: String) : ThermostatTileAction
    data object Fan : ThermostatTileAction
}

internal fun resolveThermostatAction(
    clickedId: String,
    actionPrefix: String,
    config: ThermostatTile,
    state: String,
    controls: ClimateControls?,
): ThermostatTileAction? {
    if (!clickedId.startsWith(actionPrefix) || controls == null || state in listOf("unavailable", "unknown")) {
        return null
    }
    val action = clickedId.removePrefix(actionPrefix)
    return when (config.layoutStyle) {
        ThermostatLayoutStyle.CLASSIC -> classicAction(action, state)
        ThermostatLayoutStyle.ENHANCED -> enhancedAction(action, config, controls)
    }
}

private fun classicAction(action: String, state: String): ThermostatTileAction? {
    if (action !in listOf(TAP_ACTION_DOWN, TAP_ACTION_UP) || state == "off") return null
    val slot = if (state == "heat_cool") TemperatureSlot.CLASSIC_RANGE else TemperatureSlot.CLASSIC_SINGLE
    return ThermostatTileAction.Adjust(slot, if (action == TAP_ACTION_UP) 1f else -1f)
}

private fun enhancedAction(action: String, config: ThermostatTile, controls: ClimateControls): ThermostatTileAction? =
    when {
        config.showHvacModes && action.startsWith(TAP_MODE_PREFIX) -> {
            val mode = action.removePrefix(TAP_MODE_PREFIX)
            if (mode in controls.hvacModes) ThermostatTileAction.Mode(mode) else null
        }
        config.showFanControl && action == TAP_FAN -> ThermostatTileAction.Fan
        action.startsWith("Temp:") -> temperatureAction(action, config)
        else -> null
    }

private fun temperatureAction(action: String, config: ThermostatTile): ThermostatTileAction? {
    val items = action.split(":")
    if (items.size != TEMPERATURE_ACTION_PARTS) return null
    val slot = when (items[1]) {
        "single" -> TemperatureSlot.SINGLE
        "heat" -> TemperatureSlot.HEAT
        "cool" -> TemperatureSlot.COOL
        else -> null
    }
    val amount = items[2].toFloatOrNull()
    return if (slot != null && amount != null && isDisplayedAdjustment(amount, config)) {
        ThermostatTileAction.Adjust(slot, amount)
    } else {
        null
    }
}

private fun isDisplayedAdjustment(amount: Float, config: ThermostatTile): Boolean {
    val magnitude = kotlin.math.abs(amount)
    val secondary = config.secondaryStep.coerceIn(MIN_SECONDARY_STEP, MAX_SECONDARY_STEP).toFloat()
    return magnitude == 1f || (config.showSecondaryButtons && magnitude == secondary)
}
