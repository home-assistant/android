package io.homeassistant.companion.android.data.integration.entities

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class RegisterDeviceRequest(
    var appId: String?,
    var appName: String?,
    var appVersion: String?,
    var deviceName: String?,
    var manufacturer: String?,
    var model: String?,
    var osName: String?,
    var osVersion: String?,
    var supportsEncryption: Boolean?,
    var appData: HashMap<String, String>?,
    // Added in HA 0.104.0
    var deviceId: String?
)
