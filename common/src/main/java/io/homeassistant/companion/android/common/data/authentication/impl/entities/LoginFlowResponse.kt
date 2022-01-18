package io.homeassistant.companion.android.common.data.authentication.impl.entities

sealed class LoginFlowResponse {
    abstract val type: String
    abstract val flowId: String
}
