package io.homeassistant.companion.android.domain.integration

data class Service(
    val domain: String,
    val service: String,
    val serviceData: ServiceData
)
