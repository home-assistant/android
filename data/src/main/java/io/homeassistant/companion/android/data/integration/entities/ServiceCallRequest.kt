package io.homeassistant.companion.android.data.integration.entities

data class ServiceCallRequest(
    val domain: String,
    val service: String,
    val serviceData: HashMap<String, Any>
)
