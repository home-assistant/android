package io.homeassistant.companion.android.frontend.haptic

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.core.content.getSystemService
import io.homeassistant.companion.android.frontend.externalbus.incoming.HapticType
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import timber.log.Timber

private val SUCCESS_FALLBACK_DURATION = 500.milliseconds
private val FAILURE_FALLBACK_DURATION = 1.seconds
private val WARNING_FALLBACK_DURATION = 1.5.seconds
private val SELECTION_FALLBACK_DURATION = 50.milliseconds

/**
 * Performs haptic feedback on a [View] based on a [HapticType].
 *
 * Uses `View.performHapticFeedback()` with semantic constants where available,
 * falling back to `Vibrator` patterns for API levels that lack the specific constant.
 */
object HapticFeedbackPerformer {

    /**
     * Performs the haptic feedback corresponding to [hapticType] on the given [view].
     *
     * Uses `View.performHapticFeedback` for types that have a matching `HapticFeedbackConstants`
     * value on the current API level, and falls back to `Vibrator` for types that require it
     * (e.g., `warning` always uses Vibrator, `success`/`failure`/`selection` fall back to
     * Vibrator on pre-API 30).
     */
    fun perform(view: View, hapticType: HapticType) {
        Timber.d("Performing haptic feedback: $hapticType")
        when (hapticType) {
            is HapticType.Success -> performSuccess(view)
            is HapticType.Warning -> performWarning(view)
            is HapticType.Failure -> performFailure(view)
            is HapticType.Light -> view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            is HapticType.Medium -> view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            is HapticType.Heavy -> view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            is HapticType.Selection -> performSelection(view)
            is HapticType.Unknown -> Timber.w("Ignoring unknown haptic type")
        }
    }

    private fun performSuccess(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            vibrate(view, duration = SUCCESS_FALLBACK_DURATION)
        }
    }

    private fun performWarning(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            view.context.getSystemService<Vibrator>()?.vibrate(
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK),
            )
        } else {
            vibrate(view, duration = WARNING_FALLBACK_DURATION)
        }
    }

    private fun performFailure(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
        } else {
            vibrate(view, duration = FAILURE_FALLBACK_DURATION)
        }
    }

    private fun performSelection(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.GESTURE_START)
        } else {
            vibrate(view, duration = SELECTION_FALLBACK_DURATION)
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrate(view: View, duration: Duration) {
        view.context.getSystemService<Vibrator>()?.vibrate(duration.inWholeMilliseconds)
    }
}
