package io.homeassistant.companion.android.common.data.integration

import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
data class UpdateLocation(
    val gps: List<Double>?,
    val gpsAccuracy: Int?,
    /**
     * Deprecated in core 2026.6.0+; use [inZones] instead. Kept for backwards compatibility.
     */
    val locationName: String?,
    /**
     * Full zone entity IDs (e.g. `zone.home`) the device is currently in. Replaces
     * [locationName] in core 2026.6.0+, should be `null` when using core <2026.6.0.
     */
    val inZones: List<String>?,
    val speed: Int?,
    val altitude: Int?,
    val course: Int?,
    val verticalAccuracy: Int?,
    /**
     * When the fix was obtained, which can be well before the update is sent (batched or cached
     * locations). Supported by core 2026.11.0+, should be `null` when using an older core.
     */
    val locationTime: Instant?,
)
