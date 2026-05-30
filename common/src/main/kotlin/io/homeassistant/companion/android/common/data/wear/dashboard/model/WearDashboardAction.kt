package io.homeassistant.companion.android.common.data.wear.dashboard.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * An explicit action triggered from a dashboard component interaction.
 */
@Serializable
sealed interface WearDashboardAction {

    /** Toggles a Home Assistant entity that supports toggle semantics. */
    @Serializable
    @SerialName("toggle_entity")
    data class ToggleEntity(val entityId: String) : WearDashboardAction

    /** Calls a Home Assistant service with optional service data. */
    @Serializable
    @SerialName("call_service")
    data class CallService(
        val domain: String,
        val service: String,
        val data: Map<String, JsonElement> = emptyMap(),
        val requiresConfirmation: Boolean = false,
    ) : WearDashboardAction

    /** Navigates to another page within the same dashboard. */
    @Serializable
    @SerialName("navigate")
    data class Navigate(val dashboardId: String, val pageId: String) : WearDashboardAction

    /** Requests a refresh of the current dashboard surface. */
    @Serializable
    @SerialName("refresh")
    data object Refresh : WearDashboardAction

    /** Opens the full Wear dashboard activity for richer interaction. */
    @Serializable
    @SerialName("open_full_dashboard")
    data class OpenFullDashboard(val dashboardId: String, val pageId: String? = null) : WearDashboardAction
}
