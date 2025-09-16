package io.homeassistant.companion.android.util

import androidx.annotation.VisibleForTesting
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transform

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
internal fun <T> Flow<T>.delayFirst(delayDuration: Duration, clock: Clock = Clock.System): Flow<T> {
    lateinit var endDelay: Instant

    return buffer().onStart {
        endDelay = clock.now() + delayDuration
    }.onEach {
        val currentTime = clock.now()
        if (currentTime < endDelay) {
            delay(endDelay - currentTime)
        }
    }
}

/**
 * Delays the first emission of items from the upstream flow by the specified [delayDuration].
 * Subsequent items are emitted without delay. This is useful when you want to delay the
 * initial processing of a flow without delaying the start of the upstream flow itself.
 * It also adds a buffer to the flow to ensure that the upstream flow can continue emitting items
 * while the first item is being delayed.
 *
 * Unlike using `onStart { delay(duration) }`, which postpones the start of the entire flow,
 * this operator allows the upstream flow to start immediately, and only delays the downstream flow.
 *
 * For example, if we have the following emissions from the upstream flow with a `delayDuration` of 500ms:
 * - `item0` at t0 = 0ms
 * - `item1` at t1 = 100ms
 * - `item2` at t2 = 550ms
 * Then the downstream flow would receive:
 * - `item0` and `item1` at t = 500ms (buffered and emitted immediately after the initial delay)
 * - `item2` at t = 550ms (emitted immediately as it arrives after the initial delay period)
 *
 * @param delayDuration The duration to delay the first item.
 */
@OptIn(ExperimentalTime::class)
fun <T> Flow<T>.delayFirst(delayDuration: Duration): Flow<T> {
    return delayFirst(delayDuration, Clock.System)
}
