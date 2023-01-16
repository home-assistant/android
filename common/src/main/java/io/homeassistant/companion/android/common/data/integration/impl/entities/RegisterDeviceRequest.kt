package io.homeassistant.companion.android.common.data.integration.impl.entities

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class RegisterDeviceRequest(
    var appId: String?,
    var appName: String?,
    val appVersion: String?,
    val deviceName: String?,
    val manufacturer: String?,
    val model: String?,
    var osName: String?,
    val osVersion: String?,
    var supportsEncryption: Boolean?,
    val appData: Map<String, Any>?,
    // Added in HA 0.104.0
    var deviceId: String?
)
