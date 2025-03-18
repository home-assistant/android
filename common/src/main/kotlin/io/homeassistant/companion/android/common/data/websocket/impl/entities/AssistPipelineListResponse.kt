package io.homeassistant.companion.android.common.data.websocket.impl.entities

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class AssistPipelineListResponse(
    val pipelines: List<AssistPipelineResponse>,
    val preferredPipeline: String?
)
