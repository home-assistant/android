package io.homeassistant.companion.android.common.data.authentication.impl.entities

import com.fasterxml.jackson.annotation.JsonProperty

data class LoginFlowInit(
    @JsonProperty("type")
    val type: String,
    @JsonProperty("flow_id")
    val flowId: String,
    @JsonProperty("step_id")
    val stepId: String,
    @JsonProperty("errors")
    val errors: Map<String, String>
)
