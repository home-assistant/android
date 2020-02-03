package io.homeassistant.companion.android.data.integration.entities

data class DomainResponse(
    val domain: String,
    val services: Map<String, Any>
)
