package io.homeassistant.companion.android.tiles

import kotlin.math.abs
import kotlin.math.round

/** Converts presentation values and adjustments at the boundary of the server-unit command pipeline. */
internal class ThermostatDisplayUnits(source: String?, preference: String) {
    private val sourceUnit = Unit.fromSymbol(source)
    private val requestedUnit = Unit.fromSymbol(preference)
    val hasOverride = sourceUnit != null && requestedUnit != null
    private val displayUnit = if (hasOverride) requestedUnit else sourceUnit
    val symbol = if (hasOverride) checkNotNull(displayUnit).symbol else source ?: "°"
    private val converts = hasOverride && sourceUnit != displayUnit

    // Reject actions from a tile rendered with a different unit preference.
    fun actionPrefix(prefix: String): String {
        if (!hasOverride) return prefix
        return "$prefix${checkNotNull(displayUnit).symbol}:"
    }

    /** Round converted display values only, never values used as command baselines. */
    fun display(value: Float?): Float? = value?.let {
        if (converts) {
            val converted = if (sourceUnit == Unit.CELSIUS) it * SCALE + OFFSET else (it - OFFSET) / SCALE
            round(converted * DISPLAY_PRECISION) / DISPLAY_PRECISION
        } else {
            it
        }
    }

    /** A temperature difference has no offset. Respect the smallest supported server step. */
    fun adjustment(amount: Float, serverStep: Float): Float {
        if (!hasOverride || amount == 0f) return amount
        val delta = when {
            !converts -> amount
            sourceUnit == Unit.CELSIUS -> amount / SCALE
            else -> amount * SCALE
        }
        return if (abs(delta) < serverStep) {
            if (delta < 0f) -serverStep else serverStep
        } else {
            delta
        }
    }

    /** Fallback bounds are expressed in the server unit, not the display unit. */
    fun minimum(): Float = if (sourceUnit == Unit.FAHRENHEIT) MIN_FAHRENHEIT else MIN_CELSIUS

    fun maximum(): Float = if (sourceUnit == Unit.FAHRENHEIT) MAX_FAHRENHEIT else MAX_CELSIUS

    fun enhancedText(value: String): String = when (symbol) {
        "°C", "°F" -> "$value° ${symbol.last()}"
        else -> "$value $symbol"
    }

    private enum class Unit(val symbol: String) {
        CELSIUS("°C"),
        FAHRENHEIT("°F"),
        ;

        companion object {
            fun fromSymbol(value: String?): Unit? = when (value) {
                "C", "°C" -> CELSIUS
                "F", "°F" -> FAHRENHEIT
                else -> null
            }
        }
    }

    private companion object {
        const val SCALE = 1.8f
        const val OFFSET = 32f
        const val DISPLAY_PRECISION = 10f
        const val MIN_FAHRENHEIT = 45f
        const val MAX_FAHRENHEIT = 95f
        const val MIN_CELSIUS = 7f
        const val MAX_CELSIUS = 35f
    }
}
