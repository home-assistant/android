package io.homeassistant.companion.android.common.data.wear.dashboard

import io.homeassistant.companion.android.common.data.wear.dashboard.model.WearDashboardBinding

/**
 * Generates stable cache lookup keys for [WearDashboardBinding] values.
 */
object WearDashboardBindingKey {
    /**
     * Returns a cache key for bindings that resolve at runtime, or null for constants.
     */
    fun keyFor(binding: WearDashboardBinding): String? = when (binding) {
        is WearDashboardBinding.Constant -> null
        is WearDashboardBinding.EntityState -> buildString {
            append("entity:")
            append(binding.entityId)
            binding.attribute?.let { attribute ->
                append(":attr:")
                append(attribute)
            }
        }
        is WearDashboardBinding.Template -> "template:${binding.template}"
    }
}
