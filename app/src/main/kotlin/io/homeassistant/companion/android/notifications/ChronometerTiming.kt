package io.homeassistant.companion.android.notifications

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Resolved timing of a chronometer notification.
 *
 * @property whenAt The instant the chronometer counts from, or counts down to.
 * @property countDown `true` to count down to [whenAt], `false` to count up from it.
 */
@OptIn(ExperimentalTime::class)
internal data class ChronometerTiming(val whenAt: Instant, val countDown: Boolean)

/**
 * Resolves the chronometer timing from the notification [data], or `null` when there is no usable
 * `when` value.
 *
 * `when` is a number of seconds, either since the Unix epoch or relative to [now] when
 * `when_relative` is `true`. A negative relative `when` counts up, anchored at `now - |when|`,
 * which moves every time the notification is re-sent. `when_start` is the moment the timer actually
 * started and is always a Unix timestamp, so it anchors that count-up instead and a re-send no
 * longer resets the elapsed time. It is ignored for every other combination, where `when` is
 * already an absolute anchor.
 */
@OptIn(ExperimentalTime::class)
internal fun resolveChronometerTiming(data: Map<String, String>, now: Instant): ChronometerTiming? {
    val whenOffset = data[MessagingManager.WHEN].toSecondsOrNull()
        ?.takeIf { it.inWholeMilliseconds != 0L }
        ?: return null
    val isRelative = data[MessagingManager.WHEN_RELATIVE]?.toBoolean() == true
    val start = data[MessagingManager.WHEN_START].toSecondsOrNull()
        ?.let { Instant.fromEpochMilliseconds(it.inWholeMilliseconds) }

    return when {
        !isRelative -> {
            val whenAt = Instant.fromEpochMilliseconds(whenOffset.inWholeMilliseconds)
            ChronometerTiming(whenAt = whenAt, countDown = whenAt > now)
        }
        whenOffset.isNegative() && start != null -> ChronometerTiming(whenAt = start, countDown = false)
        else -> ChronometerTiming(whenAt = now + whenOffset, countDown = whenOffset.isPositive())
    }
}

/** Parses a decimal number of seconds; non-numeric and non-finite values yield `null`. */
private fun String?.toSecondsOrNull(): Duration? = this?.toDoubleOrNull()?.takeIf { it.isFinite() }?.seconds
