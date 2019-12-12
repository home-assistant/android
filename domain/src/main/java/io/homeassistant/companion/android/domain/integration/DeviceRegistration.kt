package io.homeassistant.companion.android.domain.integration

data class DeviceRegistration(
    val appId: String? = null,
    val appName: String? = null,
    val appVersion: String? = null,
    val deviceName: String? = null,
    val manufacturer: String? = null,
    val model: String? = null,
    val osName: String? = null,
    val osVersion: String? = null,
    val supportsEncryption: Boolean? = null,
    val appData: HashMap<String, String>? = null
)
