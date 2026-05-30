package io.homeassistant.companion.android.common.data.wear.dashboard.model

import kotlinx.serialization.Serializable

/**
 * A single page within a Wear Dashboard configuration.
 */
@Serializable
data class WearDashboardPage(val id: String, val title: String? = null, val root: WearDashboardComponent)
