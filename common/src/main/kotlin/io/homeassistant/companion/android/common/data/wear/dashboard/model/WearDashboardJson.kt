package io.homeassistant.companion.android.common.data.wear.dashboard.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

/**
 * Kotlinx serialization [Json] instance for Wear Dashboard configuration.
 *
 * Uses camelCase property names and a `type` discriminator for polymorphic types.
 * Unknown JSON keys are ignored so older clients can read configs from newer schema versions.
 */
@OptIn(ExperimentalSerializationApi::class)
val wearDashboardJson = Json {
    ignoreUnknownKeys = true
    classDiscriminator = "type"
    encodeDefaults = false
    prettyPrint = false
}
