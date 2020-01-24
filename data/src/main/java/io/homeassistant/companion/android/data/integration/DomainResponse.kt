package io.homeassistant.companion.android.data.integration

data class DomainResponse(
    val domain: String,
    val services: Map<String, Any>
)
