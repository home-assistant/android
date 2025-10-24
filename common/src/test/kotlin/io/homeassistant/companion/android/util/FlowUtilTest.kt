package io.homeassistant.companion.android.util

import app.cash.turbine.test
import io.homeassistant.companion.android.testing.unit.FakeClock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FlowUtilTest {
    @Test
    fun `Given events sent before delay when consuming a flow with delayFirstThrottle then emits only latest after delay`() = runTest {
        flowOf(1, 2, 3)
            .delayFirstThrottle(1.seconds)
            .test {
                expectNoEvents()
                advanceTimeBy(500.milliseconds)
                expectNoEvents()
                advanceTimeBy(500.milliseconds)
                assertEquals(3, awaitItem())
                awaitComplete()
            }
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun `Given event sent before delay and after delay when consuming a flow with delayFirstThrottle then emits at the right time`() = runTest {
        val channel = Channel<Int>(Channel.UNLIMITED)
        val fakeClock = FakeClock()

        channel.consumeAsFlow()
            .delayFirstThrottle(1.seconds, fakeClock).test {
                expectNoEvents()
                channel.trySend(1)
                expectNoEvents()
                channel.trySend(2)
                expectNoEvents()
                advanceTimeBy(1.seconds)
                runCurrent()
                assertEquals(2, awaitItem())

                // We need to modify the clock manually since it is not advancing with advanceTimeBy
                fakeClock.currentInstant = fakeClock.currentInstant.plus(1.seconds)
                channel.trySend(3)
                assertEquals(3, awaitItem())
                channel.trySend(4)
                assertEquals(4, awaitItem())

                channel.close()
                awaitComplete()
            }
    }

    @Test
    fun `Given events before delay when consuming a flow with delayFirstThrottle then does not block upstream`() = runTest {
        val channel = Channel<Int>(Channel.UNLIMITED)
        var currentValueInUpstream: Int? = null

        channel.consumeAsFlow()
            .onEach { currentValueInUpstream = it }
            .delayFirstThrottle(1.seconds).test {
                expectNoEvents()
                channel.trySend(1)
                assertEquals(1, currentValueInUpstream)
                channel.trySend(2)
                assertEquals(2, currentValueInUpstream)
                expectNoEvents()
                advanceTimeBy(1.seconds)
                runCurrent()
                assertEquals(2, awaitItem())
                channel.close()
                awaitComplete()
            }
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun `Given events after delay when consuming a flow with delayFirstThrottle then no delay apply`() = runTest {
        val channel = Channel<Int>(Channel.UNLIMITED)
        val fakeClock = FakeClock()

        channel.consumeAsFlow()
            .delayFirstThrottle(90.seconds, fakeClock).test {
                expectNoEvents()
                advanceTimeBy(90.seconds)
                runCurrent()
                // We need to modify the clock manually since it is not advancing with advanceTimeBy
                fakeClock.currentInstant = fakeClock.currentInstant.plus(90.seconds)

                val timeBeforeSend = testScheduler.currentTime
                channel.trySend(1)
                runCurrent()
                assertEquals(1, awaitItem())
                // We want to assert that the item is sent right away
                assertEquals(0, testScheduler.currentTime - timeBeforeSend)
                channel.close()
                awaitComplete()
            }
    }

    @Test
    fun `Given no event when consuming a flow with delayFirstThrottle then complete`() = runTest {
        flowOf<Int>()
            .delayFirstThrottle(1.seconds)
            .test {
                awaitComplete()
            }
    }
}
