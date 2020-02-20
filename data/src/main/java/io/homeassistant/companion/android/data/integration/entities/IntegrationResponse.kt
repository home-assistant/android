package io.homeassistant.companion.android.data.integration.entities

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import io.homeassistant.companion.android.data.integration.IntegrationDeserializer

@JsonDeserialize(using = IntegrationDeserializer::class)
data class IntegrationResponse<T>(
    val encrypted: Boolean?,
    val body: T
)
