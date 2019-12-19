package io.homeassistant.companion.android.domain.integration

data class DeviceRegistration(
    val appVersion: String? = null,
    val deviceName: String? = null,
    var pushToken: String? = null
)
