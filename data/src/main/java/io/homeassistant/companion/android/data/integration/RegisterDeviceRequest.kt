package io.homeassistant.companion.android.data.integration

import com.fasterxml.jackson.annotation.JsonInclude
import java.util.Dictionary
import java.util.Objects

data class RegisterDeviceRequest(
    var appId: String,
    var appName: String,
    var appVersion: String,
    var deviceName: String,
    var manufacturer: String,
    var model: String,
    var osName: String,
    var osVersion: String,
    var supportsEncryption: Boolean,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    var appData: Dictionary<String, Objects>?
)
