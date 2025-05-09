package io.homeassistant.companion.android.common.data.integration.impl.entities

import io.homeassistant.companion.android.common.util.CalendarSerializer
import io.homeassistant.companion.android.common.util.MapAnySerializer
import java.util.Calendar
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable

@Serializable
data class EntityResponse(
    val entityId: String,
    val state: String,
    @Serializable(with = MapAnySerializer::class)
    val attributes: Map<String, @Polymorphic Any?>,
    @Serializable(with = CalendarSerializer::class)
    val lastChanged: Calendar,
    @Serializable(with = CalendarSerializer::class)
    val lastUpdated: Calendar,
)
