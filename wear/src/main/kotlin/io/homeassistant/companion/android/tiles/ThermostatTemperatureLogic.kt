package io.homeassistant.companion.android.tiles

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.round

internal data class SetpointRange(val low: Float, val high: Float)

/** Pure temperature rules used by tile buttons. */
internal object ThermostatTemperatureLogic {
    fun snap(value: Float, step: Float, minimum: Float, maximum: Float): Float {
        require(step > 0f && minimum <= maximum)
        val bounded = value.coerceIn(minimum, maximum)
        return (round(bounded / step) * step).coerceIn(minimum, maximum)
    }

    /** Invalid range proposals are rejected, never silently swapped. */
    // Explicit bounds and setpoints keep the pure calculation independent of tile state.
    @Suppress("LongParameterList")
    fun adjustRange(
        proposed: Float,
        adjustingHigh: Boolean,
        setpoints: SetpointRange,
        minimum: Float,
        maximum: Float,
        step: Float,
        separation: Float = step,
    ): SetpointRange {
        val (low, high) = setpoints
        val newValue = snap(proposed, step, minimum, maximum)
        return if (adjustingHigh) {
            SetpointRange(low, newValue.coerceAtLeast(low + separation).coerceAtMost(maximum))
        } else {
            SetpointRange(newValue.coerceAtMost(high - separation).coerceAtLeast(minimum), high)
        }
    }

    /** Move both setpoints together without changing their separation. */
    fun shiftRange(
        delta: Float,
        setpoints: SetpointRange,
        minimum: Float,
        maximum: Float,
        step: Float,
    ): SetpointRange {
        val (low, high) = setpoints
        val invalidRange = low < minimum || high > maximum || low > high
        if (step <= 0f || invalidRange) return setpoints
        val minimumSteps = ceil((minimum - low) / step)
        val maximumSteps = floor((maximum - high) / step)
        val shift = round(delta / step).coerceIn(minimumSteps, maximumSteps) * step
        return SetpointRange(low + shift, high + shift)
    }
}
