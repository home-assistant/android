package io.homeassistant.companion.android.util.vehicle

import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.domain
import io.homeassistant.companion.android.common.data.integration.supportsAlarmControlPanelArmAway

val SUPPORTED_DOMAINS_WITH_STRING = mapOf(
    "alarm_control_panel" to R.string.alarm_control_panels,
    "button" to R.string.buttons,
    "cover" to R.string.covers,
    "fan" to R.string.fans,
    "input_boolean" to R.string.input_booleans,
    "input_button" to R.string.input_buttons,
    "light" to R.string.lights,
    "lock" to R.string.locks,
    "scene" to R.string.scenes,
    "script" to R.string.scripts,
    "switch" to R.string.switches,
)
val SUPPORTED_DOMAINS = SUPPORTED_DOMAINS_WITH_STRING.keys

val MAP_DOMAINS = listOf(
    "device_tracker",
    "person",
    "sensor",
    "zone",
)

val NOT_ACTIONABLE_DOMAINS = listOf(
    "alarm_control_panel",
    "binary_sensor",
    "sensor",
)

fun isVehicleDomain(entity: Entity): Boolean {
    return entity.domain in SUPPORTED_DOMAINS ||
        entity.domain in NOT_ACTIONABLE_DOMAINS ||
        canNavigate(entity)
}

fun canNavigate(entity: Entity): Boolean {
    return (
        entity.domain in MAP_DOMAINS &&
            ((entity.attributes["latitude"] as? Number)?.toDouble() != null) &&
            ((entity.attributes["longitude"] as? Number)?.toDouble() != null)
        )
}

fun alarmHasNoCode(entity: Entity): Boolean {
    return entity.domain == "alarm_control_panel" &&
        entity.attributes["code_format"] as? String == null &&
        entity.supportsAlarmControlPanelArmAway()
}
