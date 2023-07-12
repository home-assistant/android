package io.homeassistant.companion.android.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
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
