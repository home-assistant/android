package io.homeassistant.companion.android.util.vehicle

import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.domain

val SUPPORTED_DOMAINS_WITH_STRING = mapOf(
    "button" to R.string.buttons,
    "cover" to R.string.covers,
    "input_boolean" to R.string.input_booleans,
    "input_button" to R.string.input_buttons,
    "light" to R.string.lights,
    "lock" to R.string.locks,
    "scene" to R.string.scenes,
    "script" to R.string.scripts,
    "switch" to R.string.switches
)
val SUPPORTED_DOMAINS = SUPPORTED_DOMAINS_WITH_STRING.keys

val MAP_DOMAINS = listOf(
    "device_tracker",
    "person",
    "sensor",
    "zone"
)

val NOT_ACTIONABLE_DOMAINS = listOf(
    "binary_sensor",
    "sensor"
)

fun isVehicleDomain(entity: Entity<*>): Boolean {
    return entity.domain in SUPPORTED_DOMAINS ||
        entity.domain in NOT_ACTIONABLE_DOMAINS ||
        canNavigate(entity)
}

fun canNavigate(entity: Entity<*>): Boolean {
    return (
        entity.domain in MAP_DOMAINS &&
            ((entity.attributes as? Map<*, *>)?.get("latitude") as? Double != null) &&
            ((entity.attributes as? Map<*, *>)?.get("longitude") as? Double != null)
        )
}
