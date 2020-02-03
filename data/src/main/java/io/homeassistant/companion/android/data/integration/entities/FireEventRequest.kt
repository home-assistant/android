package io.homeassistant.companion.android.data.integration.entities

data class FireEventRequest(
    val eventType: String,
    val eventData: Map<String, Any>
)
