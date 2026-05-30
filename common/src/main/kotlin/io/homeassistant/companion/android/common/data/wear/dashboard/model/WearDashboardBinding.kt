package io.homeassistant.companion.android.common.data.wear.dashboard.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Describes how a dashboard value is resolved from Home Assistant state or templates.
 */
@Serializable
sealed interface WearDashboardBinding {

    /** A fixed value that does not change at runtime. */
    @Serializable
    @SerialName("constant")
    data class Constant(val value: JsonElement) : WearDashboardBinding

    /** The state or attribute of a Home Assistant entity. */
    @Serializable
    @SerialName("entity_state")
    data class EntityState(val entityId: String, val attribute: String? = null) : WearDashboardBinding

    /** A Home Assistant Jinja template evaluated at runtime. */
    @Serializable
    @SerialName("template")
    data class Template(val template: String) : WearDashboardBinding
}
