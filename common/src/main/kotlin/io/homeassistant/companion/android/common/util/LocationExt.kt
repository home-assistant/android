package io.homeassistant.companion.android.common.util

import android.location.Location
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
fun Location.instant(): Instant {
    return Instant.fromEpochMilliseconds(time)
}
