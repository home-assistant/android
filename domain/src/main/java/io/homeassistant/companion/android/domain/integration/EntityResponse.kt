package io.homeassistant.companion.android.domain.integration

import java.util.Calendar

data class EntityResponse(
    val entityId: String,
    val state: String,
    val attributes: Map<String, String>,
    val lastChanged: Calendar,
    val lastUpdated: Calendar,
    val context: Map<String, String>
)
