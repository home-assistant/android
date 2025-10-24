package io.homeassistant.companion.android.testing.unit

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class FakeClock : Clock {
    var currentInstant: Instant = Clock.System.now()

    override fun now(): Instant {
        return currentInstant
    }
}
