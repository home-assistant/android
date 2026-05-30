package io.homeassistant.companion.android.launch

import android.graphics.Rect
import android.util.Rational
import io.homeassistant.companion.android.frontend.exoplayer.ExoPlayerUiState

/**
 * Narrowest aspect ratio (width:height) accepted by
 * [android.app.PictureInPictureParams.Builder.setAspectRatio], per its documented range of
 * 1:2.39 to 2.39:1 inclusive. We clamp to it so the system does not reject the params later
 * when entering PiP.
 */
private val PIP_MIN_ASPECT = Rational(100, 239)

/**
 * Widest aspect ratio (width:height) accepted by
 * [android.app.PictureInPictureParams.Builder.setAspectRatio], per its documented range of
 * 1:2.39 to 2.39:1 inclusive. We clamp to it so the system does not reject the params later
 * when entering PiP.
 */
private val PIP_MAX_ASPECT = Rational(239, 100)

/**
 * Fallback PiP aspect ratio used when the source content does not advertise its own —
 * e.g. a WebView custom view, or a video that has not yet reported its dimensions. 16:9
 * matches the most common video framing and sits comfortably inside the PiP allowed range.
 */
private val PIP_DEFAULT_ASPECT = Rational(16, 9)

/**
 * Snapshot of "the screen has PiP-eligible content" along with the parameters needed to enter
 * Picture-in-Picture mode.
 *
 * `null` upstream of this type means the host activity must not enter PiP. Both fields are
 * value types — keeping `android.app.PictureInPictureParams` (API 26+) out of the ViewModel
 * avoids leaking an Android system type into shared Compose plumbing.
 *
 * @param aspectRatio Pre-clamped to Android's allowed PiP range `[1:2.39, 2.39:1]`.
 * @param sourceRect Optional layout hint used for the launch animation. `null` lets Android
 *   infer it from the activity's content.
 */
data class PipReadiness(val aspectRatio: Rational, val sourceRect: Rect? = null) {
    companion object {
        /**
         * Computes the [PipReadiness] snapshot for the current screen state.
         *
         * Returns `null` when no PiP-eligible content is showing. When both an ExoPlayer fullscreen
         * stream and a custom view are simultaneously present (theoretically possible — different
         * frontend paths choose between them), the player is the more specific signal and wins.
         *
         * The returned aspect ratio is clamped to Android's allowed PiP range `[1:2.39, 2.39:1]` so
         * `PictureInPictureParams.Builder.setAspectRatio` cannot throw.
         */
        fun from(customViewShown: Boolean, exoState: ExoPlayerUiState?): PipReadiness? {
            val playerFullScreen = exoState?.isFullScreen == true
            if (!playerFullScreen && !customViewShown) return null

            val aspect = if (playerFullScreen) {
                exoState.videoAspectRatio?.let(::aspectFromHeightOverWidth) ?: PIP_DEFAULT_ASPECT
            } else {
                PIP_DEFAULT_ASPECT
            }
            return PipReadiness(aspectRatio = aspect.coerceWithinPipRange())
        }

        private fun Rational.coerceWithinPipRange(): Rational = when {
            toFloat() < PIP_MIN_ASPECT.toFloat() -> PIP_MIN_ASPECT
            toFloat() > PIP_MAX_ASPECT.toFloat() -> PIP_MAX_ASPECT
            else -> this
        }

        private fun aspectFromHeightOverWidth(heightOverWidth: Double): Rational {
            // ExoPlayer stores ratio as height/width; PictureInPictureParams wants width:height.
            // Multiply by 1_000 before truncating to integers so we keep three significant digits
            // before `Rational`'s built-in reduction collapses common cases like 16:9 or 4:3.
            val widthScaled = 1_000.0
            val heightScaled = heightOverWidth * 1_000.0
            return Rational(
                widthScaled.toInt().coerceAtLeast(1),
                heightScaled.toInt().coerceAtLeast(1),
            )
        }
    }
}
