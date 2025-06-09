package io.homeassistant.companion.android.common.data.integration.impl.entities

import io.homeassistant.companion.android.common.util.LocalDateTimeSerializer
import io.homeassistant.companion.android.common.util.MapAnySerializer
import java.time.LocalDateTime
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable

@Serializable
data class EntityResponse(
    val entityId: String,
    val state: String,
    @Serializable(with = MapAnySerializer::class)
    val attributes: Map<String, @Polymorphic Any?>,
    @Serializable(with = LocalDateTimeSerializer::class)
    val lastChanged: LocalDateTime,
    @Serializable(with = LocalDateTimeSerializer::class)
    val lastUpdated: LocalDateTime,
)
