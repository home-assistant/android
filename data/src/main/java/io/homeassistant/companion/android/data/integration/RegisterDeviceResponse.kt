package io.homeassistant.companion.android.data.integration

data class RegisterDeviceResponse(
    var cloudhookUrl: String?,
    var remoteUiUrl: String?,
    var secret: String?,
    var webhookId: String
)
