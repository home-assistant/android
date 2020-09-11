package io.homeassistant.companion.android.common.data.integration.impl.entities

data class RegisterDeviceResponse(
    var cloudhookUrl: String?,
    var remoteUiUrl: String?,
    var secret: String?,
    var webhookId: String
)
