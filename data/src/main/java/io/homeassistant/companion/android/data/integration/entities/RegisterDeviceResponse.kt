package io.homeassistant.companion.android.data.integration.entities

data class RegisterDeviceResponse(
    var cloudhookUrl: String?,
    var remoteUiUrl: String?,
    var secret: String?,
    var webhookId: String
)
