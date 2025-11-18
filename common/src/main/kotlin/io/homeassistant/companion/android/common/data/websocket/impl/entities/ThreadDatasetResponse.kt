package io.homeassistant.companion.android.common.data.websocket.impl.entities

import kotlinx.serialization.Serializable

@Serializable
data class ThreadDatasetResponse(
    val datasetId: String,
    val extendedPanId: String,
    val networkName: String?,
    val panId: String?,
    val preferred: Boolean,
    // only on core >= 2023.9, may still be null
    val preferredBorderAgentId: String? = null,
    val source: String,
)
