package io.homeassistant.companion.android.domain.integration

import java.util.*

interface IntegrationUseCase {

    suspend fun registerDevice(
        appId: String,
        appName: String,
        appVersion: String,
        deviceName: String,
        manufacturer: String,
        model: String,
        osName: String,
        osVersion: String,
        supportsEncryption: Boolean,
        appData: Dictionary<String, Objects>?
    )

    suspend fun isRegistered(): Boolean

}