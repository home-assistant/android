package io.homeassistant.companion.android.common.data.authentication.impl.entities

import com.fasterxml.jackson.annotation.JsonProperty

data class LoginFlowCreateEntry(
    @JsonProperty("type")
    override val type: String,
    @JsonProperty("flow_id")
    override val flowId: String,
    @JsonProperty("version")
    val version: Int,
    @JsonProperty("result")
    val result: String
) : LoginFlowResponse()
