package io.homeassistant.companion.android.common.data.wear.dashboard.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Describes when a dashboard surface should refresh its resolved state.
 */
@Serializable
sealed interface WearDashboardRefreshPolicy {

    /** Refresh when the user enters the dashboard surface. */
    @Serializable
    @SerialName("on_enter")
    data object OnEnter : WearDashboardRefreshPolicy

    /** Refresh when a referenced entity changes state. */
    @Serializable
    @SerialName("on_entity_change")
    data object OnEntityChange : WearDashboardRefreshPolicy

    /** Refresh on a fixed interval. */
    @Serializable
    @SerialName("interval")
    data class Interval(val seconds: Int) : WearDashboardRefreshPolicy

    /** Refresh only when explicitly requested by the user or an action. */
    @Serializable
    @SerialName("manual")
    data object Manual : WearDashboardRefreshPolicy
}
