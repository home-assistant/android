package io.homeassistant.companion.android.notifications

import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@OptIn(ExperimentalTime::class)
class ChronometerTimingTest {

    private val now = Instant.fromEpochSeconds(1_700_000_000)

    @Test
    fun `Given absolute when in the future when resolving then it counts down to that instant`() {
        val data = mapOf(MessagingManager.WHEN to "1700000120")

        val timing = resolveChronometerTiming(data, now)

        assertEquals(ChronometerTiming(whenAt = now + 120.seconds, countDown = true), timing)
    }

    @Test
    fun `Given absolute when in the past when resolving then it counts up from that instant`() {
        val data = mapOf(MessagingManager.WHEN to "1699999880")

        val timing = resolveChronometerTiming(data, now)

        assertEquals(ChronometerTiming(whenAt = now - 120.seconds, countDown = false), timing)
    }

    @Test
    fun `Given relative positive when when resolving then it counts down from now plus when`() {
        val data = mapOf(MessagingManager.WHEN to "120", MessagingManager.WHEN_RELATIVE to "true")

        val timing = resolveChronometerTiming(data, now)

        assertEquals(ChronometerTiming(whenAt = now + 120.seconds, countDown = true), timing)
    }

    @Test
    fun `Given relative negative when without when_start when resolving then it counts up from now minus when`() {
        val data = mapOf(MessagingManager.WHEN to "-120", MessagingManager.WHEN_RELATIVE to "true")

        val timing = resolveChronometerTiming(data, now)

        assertEquals(ChronometerTiming(whenAt = now - 120.seconds, countDown = false), timing)
    }

    @Test
    fun `Given relative negative when with when_start when resolving then it counts up from when_start`() {
        val data = mapOf(
            MessagingManager.WHEN to "-120",
            MessagingManager.WHEN_RELATIVE to "true",
            MessagingManager.WHEN_START to "1699999400",
        )

        val timing = resolveChronometerTiming(data, now)

        assertEquals(ChronometerTiming(whenAt = now - 10.minutes, countDown = false), timing)
    }

    @Test
    fun `Given relative positive when with when_start when resolving then when_start is ignored`() {
        val data = mapOf(
            MessagingManager.WHEN to "120",
            MessagingManager.WHEN_RELATIVE to "true",
            MessagingManager.WHEN_START to "1699999400",
        )

        val timing = resolveChronometerTiming(data, now)

        assertEquals(ChronometerTiming(whenAt = now + 120.seconds, countDown = true), timing)
    }

    @Test
    fun `Given absolute when with when_start when resolving then when_start is ignored`() {
        val data = mapOf(MessagingManager.WHEN to "1699999880", MessagingManager.WHEN_START to "1699999400")

        val timing = resolveChronometerTiming(data, now)

        assertEquals(ChronometerTiming(whenAt = now - 120.seconds, countDown = false), timing)
    }

    @Test
    fun `Given non-numeric when_start when resolving then it counts up from now minus when`() {
        val data = mapOf(
            MessagingManager.WHEN to "-120",
            MessagingManager.WHEN_RELATIVE to "true",
            MessagingManager.WHEN_START to "soon",
        )

        val timing = resolveChronometerTiming(data, now)

        assertEquals(ChronometerTiming(whenAt = now - 120.seconds, countDown = false), timing)
    }

    @Test
    fun `Given fractional when when resolving then milliseconds are kept`() {
        val data = mapOf(MessagingManager.WHEN to "1.5", MessagingManager.WHEN_RELATIVE to "true")

        val timing = resolveChronometerTiming(data, now)

        assertEquals(ChronometerTiming(whenAt = now + 1500.milliseconds, countDown = true), timing)
    }

    @ParameterizedTest
    @ValueSource(strings = ["0", "abc", "", "NaN", "Infinity"])
    fun `Given unusable when when resolving then there is no timing`(value: String) {
        assertNull(resolveChronometerTiming(mapOf(MessagingManager.WHEN to value), now))
    }

    @Test
    fun `Given when_start without when when resolving then there is no timing`() {
        assertNull(resolveChronometerTiming(mapOf(MessagingManager.WHEN_START to "1699999400"), now))
    }
}
