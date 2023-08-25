package io.homeassistant.companion.android.common.data.websocket.impl.entities

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class ThreadDatasetResponse(
    val datasetId: String,
    val extendedPanId: String,
    val networkName: String,
    val panId: String,
    val preferred: Boolean,
    val preferredBorderAgentId: String?, // only on core >= 2023.9, may still be null
    val source: String
)
