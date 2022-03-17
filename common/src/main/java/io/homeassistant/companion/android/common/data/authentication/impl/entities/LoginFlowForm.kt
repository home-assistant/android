package io.homeassistant.companion.android.common.data.authentication.impl.entities

import com.fasterxml.jackson.annotation.JsonProperty

data class LoginFlowForm(
    @JsonProperty("type")
    override val type: String,
    @JsonProperty("flow_id")
    override val flowId: String,
    @JsonProperty("step_id")
    val stepId: String,
    @JsonProperty("errors")
    val errors: Map<String, String>
) : LoginFlowResponse()
