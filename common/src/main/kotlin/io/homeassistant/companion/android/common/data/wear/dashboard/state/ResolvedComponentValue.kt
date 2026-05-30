package io.homeassistant.companion.android.common.data.wear.dashboard.state

/**
 * A resolved runtime value for a dashboard binding.
 */
sealed interface ResolvedComponentValue {

    /** A textual binding value. */
    data class TextValue(val text: String) : ResolvedComponentValue

    /** A numeric binding value. */
    data class NumberValue(val number: Double) : ResolvedComponentValue

    /** A boolean binding value. */
    data class BooleanValue(val value: Boolean) : ResolvedComponentValue

    /** A binding value that could not be classified or parsed. */
    data class Unknown(val raw: String?) : ResolvedComponentValue
}
