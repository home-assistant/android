package io.homeassistant.companion.android.common.data.websocket.impl.entities

data class AssistPipelineListResponse(
    val pipelines: List<AssistPipelineResponse>,
    val preferredPipeline: String?
)
