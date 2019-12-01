package io.homeassistant.companion.android.domain.integration

import java.util.Dictionary
import java.util.Objects

data class DeviceRegistration(
    val appId: String,
    val appName: String,
    val appVersion: String,
    val deviceName: String,
    val manufacturer: String,
    val model: String,
    val osName: String,
    val osVersion: String,
    val supportsEncryption: Boolean,
    val appData: Dictionary<String, Objects>?
)
