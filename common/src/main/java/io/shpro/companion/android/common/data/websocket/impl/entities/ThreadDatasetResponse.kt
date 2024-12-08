package io.shpro.companion.android.common.data.websocket.impl.entities

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class ThreadDatasetResponse(
    val datasetId: String,
    val extendedPanId: String,
    val networkName: String,
    val panId: String,
    val preferred: Boolean,
    // only on core >= 2023.9, may still be null
    val preferredBorderAgentId: String?,
    val source: String
)
