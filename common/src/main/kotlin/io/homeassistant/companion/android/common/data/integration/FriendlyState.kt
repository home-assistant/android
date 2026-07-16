package io.homeassistant.companion.android.common.data.integration

import android.content.Context
import android.os.Build
import android.text.format.DateUtils
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.util.SdkVersion
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * Entity state prepared for display without a context: string resources and time-relative
 * values are resolved to their final localized text with [resolve] at render time, so the
 * translation always uses the configuration of the displaying context.
 */
@Immutable
sealed interface FriendlyState {
    /** Resolves the display text, localizing with [context]. */
    fun resolve(context: Context): String

    /** A translated state, see the `state_*` string resources. */
    data class Resource(@StringRes val resId: Int) : FriendlyState {
        override fun resolve(context: Context): String = context.getString(resId)
    }

    /** Text needing no localization, like a precision-formatted number. */
    data class Literal(val value: String) : FriendlyState {
        override fun resolve(context: Context): String = value
    }

    /** A timestamp state shown relative to now, resolved at render time so it stays current. */
    data class RelativeTime(val epochMillis: Long) : FriendlyState {
        override fun resolve(context: Context): String = DateUtils.getRelativeTimeSpanString(
            epochMillis,
            System.currentTimeMillis(),
            0,
            DateUtils.FORMAT_ABBREV_ALL,
        ).toString()
    }

    /** A state with its unit of measurement appended. */
    data class WithUnit(val state: FriendlyState, val unit: String) : FriendlyState {
        override fun resolve(context: Context): String = "${state.resolve(context)} $unit"
    }
}

/**
 * Core [friendlyState] resolution, exposed to display consumers through `EntityDisplayItem.state`.
 * Context-free: the returned [FriendlyState] localizes at render time.
 */
internal fun Entity.friendlyState(displayPrecision: Int?, appendUnitOfMeasurement: Boolean = false): FriendlyState {
    val resource = stateResource()
    val friendlyState = when {
        resource != null -> FriendlyState.Resource(resource)
        else -> relativeTimeState()
            ?: precisionState(displayPrecision)
            ?: FriendlyState.Literal(titleCasedState())
    }

    if (appendUnitOfMeasurement) {
        val unit = attributes["unit_of_measurement"]?.toString()
        if (unit?.isNotBlank() == true) {
            return FriendlyState.WithUnit(friendlyState, unit)
        }
    }
    return friendlyState
}

/** The `state_*` string resource translating the state, or null when there is none. */
private fun Entity.stateResource(): Int? = when (domain) {
    "binary_sensor" -> {
        // https://github.com/home-assistant/core/blob/dev/homeassistant/components/binary_sensor/strings.json#L113
        when (attributes["device_class"]) {
            "battery" -> if (state ==
                "on"
            ) {
                commonR.string.state_low
            } else {
                commonR.string.state_normal
            }

            "battery_charging" -> if (state ==
                "on"
            ) {
                commonR.string.state_charging
            } else {
                commonR.string.state_not_charging
            }

            "cold" -> if (state ==
                "on"
            ) {
                commonR.string.state_cold
            } else {
                commonR.string.state_off
            }

            "connectivity" -> if (state ==
                "on"
            ) {
                commonR.string.state_connected
            } else {
                commonR.string.state_disconnected
            }

            "door", "window", "garage_door", "opening" -> if (state ==
                "on"
            ) {
                commonR.string.state_open
            } else {
                commonR.string.state_closed
            }

            "gas" -> if (state ==
                "on"
            ) {
                commonR.string.state_detected
            } else {
                commonR.string.state_clear
            }

            "heat" -> if (state ==
                "on"
            ) {
                commonR.string.state_hot
            } else {
                commonR.string.state_off
            }

            "light" -> if (state ==
                "on"
            ) {
                commonR.string.state_light_detected
            } else {
                commonR.string.state_no_light
            }

            "lock" -> if (state ==
                "on"
            ) {
                commonR.string.state_unlocked
            } else {
                commonR.string.state_locked
            }

            "moisture" -> if (state ==
                "on"
            ) {
                commonR.string.state_wet
            } else {
                commonR.string.state_dry
            }

            "moving" -> if (state ==
                "on"
            ) {
                commonR.string.state_moving
            } else {
                commonR.string.state_not_moving
            }

            "plug" -> if (state ==
                "on"
            ) {
                commonR.string.state_plugged_in
            } else {
                commonR.string.state_unplugged
            }

            "presence" -> if (state ==
                "on"
            ) {
                commonR.string.state_home
            } else {
                commonR.string.state_not_home
            }

            "problem" -> if (state ==
                "on"
            ) {
                commonR.string.state_problem
            } else {
                commonR.string.state_ok
            }

            "running" -> if (state ==
                "on"
            ) {
                commonR.string.state_running
            } else {
                commonR.string.state_not_running
            }

            "safety" -> if (state ==
                "on"
            ) {
                commonR.string.state_unsafe
            } else {
                commonR.string.state_safe
            }

            "tamper" -> if (state ==
                "on"
            ) {
                commonR.string.state_tampering_detected
            } else {
                commonR.string.state_off
            }

            "update" -> if (state ==
                "on"
            ) {
                commonR.string.state_update_available
            } else {
                commonR.string.state_up_to_date
            }

            else -> if (state ==
                "on"
            ) {
                commonR.string.state_on
            } else {
                commonR.string.state_off
            }
        }
    }

    else -> {
        // https://github.com/home-assistant/frontend/blob/dev/src/common/entity/get_states.ts#L5
        when (state) {
            "above_horizon" -> commonR.string.state_above_horizon
            "active" -> commonR.string.state_active
            "armed_away" -> commonR.string.state_armed_away
            "armed_custom_bypass" -> commonR.string.state_armed_custom_bypass
            "armed_home" -> commonR.string.state_armed_home
            "armed_night" -> commonR.string.state_armed_night
            "armed_vacation" -> commonR.string.state_armed_vacation
            "arming" -> commonR.string.state_arming
            "auto" -> commonR.string.state_auto
            "below_horizon" -> commonR.string.state_below_horizon
            "buffering" -> commonR.string.state_buffering
            "cleaning" -> commonR.string.state_cleaning
            "clear-night" -> commonR.string.state_clear_night
            "cloudy" -> commonR.string.state_cloudy
            "closed" -> commonR.string.state_closed
            "closing" -> commonR.string.state_closing
            "cool" -> commonR.string.state_cool
            "disarmed" -> commonR.string.state_disarmed
            "disarming" -> commonR.string.state_disarming
            "docked" -> commonR.string.state_docked
            "dry" -> commonR.string.state_dry
            "error" -> commonR.string.state_error
            "exceptional" -> commonR.string.state_exceptional
            "fan_only" -> commonR.string.state_fan_only
            "fog" -> commonR.string.state_fog
            "hail" -> commonR.string.state_hail
            "heat" -> commonR.string.state_heat
            "heat_cool" -> commonR.string.state_heat_cool
            "home" -> commonR.string.state_home
            "idle" -> commonR.string.state_idle
            "jammed" -> commonR.string.state_jammed
            "lightning-raining" -> commonR.string.state_lightning_raining
            "lightning" -> commonR.string.state_lightning
            "locked" -> commonR.string.state_locked
            "locking" -> commonR.string.state_locking
            "mowing" -> commonR.string.state_mowing
            "not_home" -> commonR.string.state_not_home
            "off" -> commonR.string.state_off
            "on" -> commonR.string.state_on
            "open" -> commonR.string.state_open
            "opening" -> commonR.string.state_opening
            "partlycloudy" -> commonR.string.state_partlycloudy
            "paused" -> commonR.string.state_paused
            "pending" -> commonR.string.state_pending
            "playing" -> commonR.string.state_playing
            "problem" -> commonR.string.state_problem
            "pouring" -> commonR.string.state_pouring
            "rainy" -> commonR.string.state_rainy
            "recording" -> commonR.string.state_recording
            "returning" -> commonR.string.state_returning
            "snowy-rainy" -> commonR.string.state_snowy_rainy
            "snowy" -> commonR.string.state_snowy
            "standby" -> commonR.string.state_standby
            "streaming" -> commonR.string.state_streaming
            "sunny" -> commonR.string.state_sunny
            "triggered" -> commonR.string.state_triggered
            "unavailable" -> commonR.string.state_unavailable
            "unlocked" -> commonR.string.state_unlocked
            "unlocking" -> commonR.string.state_unlocking
            "unknown" -> commonR.string.state_unknown
            "windy", "windy-variant" -> commonR.string.state_windy
            else -> null
        }
    }
}

private fun Entity.relativeTimeState(): FriendlyState.RelativeTime? {
    if (!SdkVersion.isAtLeast(Build.VERSION_CODES.O)) return null
    return try {
        val stateInMillis = ZonedDateTime.parse(state, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            .toInstant()
            .toEpochMilli()
        FriendlyState.RelativeTime(stateInMillis)
    } catch (e: DateTimeParseException) {
        /* Not a timestamp */
        null
    }
}

private fun Entity.precisionState(displayPrecision: Int?): FriendlyState.Literal? {
    if (displayPrecision == null || !canSupportPrecision()) return null
    return FriendlyState.Literal(String.format(Locale.getDefault(), "%.${displayPrecision}f", state.toDouble()))
}

private fun Entity.titleCasedState(): String = state.split("_").joinToString(" ") { word ->
    word.replaceFirstChar {
        if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
    }
}
