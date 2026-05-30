package io.homeassistant.companion.android.tiles.dashboard

import io.homeassistant.companion.android.common.data.wear.dashboard.WearDashboardBindingKey
import io.homeassistant.companion.android.common.data.wear.dashboard.model.WearDashboardBinding
import io.homeassistant.companion.android.common.data.wear.dashboard.state.WearDashboardResolvedState
import io.homeassistant.companion.android.common.data.wear.dashboard.state.toDisplayString
import javax.inject.Inject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Resolves dashboard bindings from cached [WearDashboardResolvedState].
 */
class WearDashboardTileStateResolver @Inject constructor() {

    /**
     * Resolves [binding] to a display string using [state], or a constant value directly.
     */
    fun resolveString(binding: WearDashboardBinding, state: WearDashboardResolvedState): String {
        return when (binding) {
            is WearDashboardBinding.Constant -> constantToString(binding)
            else -> {
                val key = WearDashboardBindingKey.keyFor(binding)
                if (key == null) {
                    ""
                } else {
                    state.values[key]?.toDisplayString().orEmpty()
                }
            }
        }
    }

    /**
     * Returns whether [value] should be treated as true for conditional rendering.
     */
    fun isTruthy(value: String): Boolean {
        val normalized = value.trim().lowercase()
        return normalized.isNotEmpty() &&
            normalized !in FALSY_VALUES &&
            normalized != "0" &&
            normalized != "0.0"
    }

    /**
     * Parses [value] as an integer, falling back to [default] when parsing fails.
     */
    fun parseInt(value: String, default: Int = 0): Int = value.trim().toIntOrNull() ?: default

    private fun constantToString(binding: WearDashboardBinding.Constant): String {
        val element = binding.value
        return when (element) {
            is JsonPrimitive -> element.contentOrNull ?: element.toString()
            else -> element.toString()
        }
    }

    companion object {
        private val FALSY_VALUES = setOf("false", "off", "no", "unknown", "unavailable", "none", "null")
    }
}
