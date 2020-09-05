package io.homeassistant.companion.android.common.data.integration

data class Service(
    val domain: String,
    val service: String,
    val serviceData: ServiceData
)
