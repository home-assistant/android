@file:OptIn(ExperimentalTime::class)

package io.homeassistant.companion.android.fakes

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class FakeClock : Clock {
    var time: Instant = Instant.fromEpochMilliseconds(0L)

    override fun now(): Instant {
        return time
    }
}
