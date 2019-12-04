package io.homeassistant.companion.android.data.integration

import java.util.Calendar

data class Entity(
    val entityId: String,
    val state: String,
    val attributes: Map<String, String>,
    val lastChanged: Calendar,
    val lastUpdated: Calendar,
    val context: Map<String, String>
)
