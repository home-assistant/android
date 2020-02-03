package io.homeassistant.companion.android.data.integration.entities

import io.homeassistant.companion.android.domain.integration.ServiceData

data class DomainResponse(
    val domain: String,
    val services: Map<String, ServiceData>
)
