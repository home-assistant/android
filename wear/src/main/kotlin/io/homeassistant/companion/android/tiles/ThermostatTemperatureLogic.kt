package io.homeassistant.companion.android.tiles

import kotlin.math.round

/** Pure temperature rules used by tile buttons and the drag editor. */
internal object ThermostatTemperatureLogic {
    const val START_DEGREES = 205f
    const val SWEEP_DEGREES = 130f
    private const val MIDPOINT_FRACTION = 0.5f
    fun snap(value: Float, step: Float, minimum: Float, maximum: Float): Float {
        require(step > 0f && minimum <= maximum)
        val bounded = value.coerceIn(minimum, maximum)
        return (minimum + round((bounded - minimum) / step) * step).coerceIn(minimum, maximum)
    }

    /** Invalid range proposals are rejected, never silently swapped. */
    // Explicit bounds and setpoints keep the pure calculation independent of tile state.
    @Suppress("LongParameterList")
    fun adjustRange(
        proposed: Float,
        adjustingHigh: Boolean,
        low: Float,
        high: Float,
        minimum: Float,
        maximum: Float,
        step: Float,
        separation: Float = step,
    ): Pair<Float, Float> {
        val newValue = snap(proposed, step, minimum, maximum)
        return if (adjustingHigh) {
            low to newValue.coerceAtLeast(low + separation).coerceAtMost(maximum)
        } else {
            newValue.coerceAtMost(high - separation).coerceAtLeast(minimum) to high
        }
    }

    fun normalized(value: Float, minimum: Float, maximum: Float): Float =
        if (maximum <= minimum) MIDPOINT_FRACTION else ((value - minimum) / (maximum - minimum)).coerceIn(0f, 1f)

    /** Normalizes clockwise top semicircle: 0=left side, 1=right side. */
    fun fractionFromTopArc(x: Float, y: Float, centerX: Float, centerY: Float): Float {
        val degrees = Math.toDegrees(kotlin.math.atan2((y - centerY).toDouble(), (x - centerX).toDouble()))
        val topAngle = if (degrees < 0) degrees + 360 else degrees
        return (
            (topAngle - START_DEGREES) /
                SWEEP_DEGREES
            ).toFloat().coerceIn(0f, 1f)
    }

    fun chooseHighMarker(touchFraction: Float, lowFraction: Float, highFraction: Float): Boolean =
        kotlin.math.abs(touchFraction - highFraction) < kotlin.math.abs(touchFraction - lowFraction)
}
