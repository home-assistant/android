package io.homeassistant.companion.android.common.data.wear.dashboard.state

/**
 * Returns a display string for this resolved binding value.
 */
fun ResolvedComponentValue.toDisplayString(): String = when (this) {
    is ResolvedComponentValue.TextValue -> text
    is ResolvedComponentValue.NumberValue -> number.toString()
    is ResolvedComponentValue.BooleanValue -> value.toString()
    is ResolvedComponentValue.Unknown -> raw.orEmpty()
}
