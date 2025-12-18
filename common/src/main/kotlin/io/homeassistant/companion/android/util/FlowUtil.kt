package io.homeassistant.companion.android.util

import androidx.annotation.VisibleForTesting
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Emit the first value from a Flow, and then after the period has passed the last item emitted
 * by the Flow during that period (if different). This ensures throttling/debouncing but also
 * always receiving the first and last item of the Flow.
 *
 * From https://github.com/Kotlin/kotlinx.coroutines/issues/1446#issuecomment-1198103541
 */
fun <T> Flow<T>.throttleLatest(delayMillis: Long): Flow<T> = this
    .conflate()
    .transform {
        emit(it)
        delay(delayMillis)
    }

@OptIn(ExperimentalTime::class)
@VisibleForTesting
internal fun <T> Flow<T>.delayFirstThrottle(delayDuration: Duration, clock: Clock = Clock.System): Flow<T> =
    channelFlow {
        val startTime = clock.now()
        val endDelay = startTime + delayDuration
        val mutex = Mutex()
        var canSend = false
        var latestValue: T? = null

        val job = launch {
            this@delayFirstThrottle.collect { value ->
                mutex.withLock {
                    if (!canSend) {
                        // During delay period, keep only the latest value
                        latestValue = value
                    } else {
                        // After delay period, emit immediately
                        send(value)
                    }
                }
            }
        }

        // Wait for the delay period to pass
        val currentTime = clock.now()
        if (currentTime < endDelay) {
            delay(endDelay - currentTime)
        }

        // Atomically emit the latest value and set the flag
        mutex.withLock {
            latestValue?.let { send(it) }
            canSend = true
        }

        // Wait for upstream to complete
        job.join()
    }

/**
 * Delays emissions from the upstream flow by the specified [delayDuration], keeping only the
 * latest value if multiple items arrive during the delay period. Once the delay has passed,
 * all subsequent items are emitted immediately without delay.
 *
 * This is useful when you want an initial "quiet period" before processing, but only care about
 * the most recent value during that period.
 *
 * For example, if we have the following emissions from the upstream flow with a `delayDuration` of 500ms:
 * - `item0` at t0 = 0ms
 * - `item1` at t1 = 100ms
 * - `item2` at t2 = 200ms
 * - `item3` at t3 = 600ms
 * Then the downstream flow would receive:
 * - `item2` at t = 500ms (the latest value received during the delay period)
 * - `item3` at t = 600ms (emitted immediately as the delay period has passed)
 *
 * @param delayDuration The duration to delay before emitting the first (latest) item.
 */
@OptIn(ExperimentalTime::class)
fun <T> Flow<T>.delayFirstThrottle(delayDuration: Duration): Flow<T> {
    return delayFirstThrottle(delayDuration, Clock.System)
}
