package io.homeassistant.companion.android.domain.integration

import java.util.Calendar

data class Entity<T>(
    val entityId: String,
    val state: String,
    val attributes: T,
    val lastChanged: Calendar,
    val lastUpdated: Calendar,
    val context: Map<String, Any>
)
