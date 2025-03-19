package io.homeassistant.companion.android.common.data.integration.impl.entities

data class RegisterDeviceResponse(
    val cloudhookUrl: String?,
    val remoteUiUrl: String?,
    val secret: String?,
    val webhookId: String
)
