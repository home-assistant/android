package io.homeassistant.companion.android.launch.applock

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import dev.chrisbanes.haze.hazeEffect

/**
 * Full-screen overlay that blurs the content marked with `hazeSource` and blocks all touch events.
 *
 * Only renders when [hazeState]`.blurEnabled` is `true`. The blur is provided by [hazeEffect]
 * which renders the blurred snapshot of content captured by the paired `hazeSource` modifier.
 * All pointer events are intercepted and consumed so that taps, swipes, and gestures cannot
 * reach the composables underneath while the app is locked.
 *
 * @param hazeState shared state that connects this overlay to the content marked with `hazeSource`.
 * @param modifier optional modifier for the overlay
 * @param style the blur style to apply, defaults to [HazeMaterials.thin]
 */
@Composable
internal fun HazeLockOverlay(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    style: HazeBlurStyle = HazeMaterials.thin(),
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
            }
            .hazeBlur(input = HazeInput.Sources(hazeState), style = style),
    )
}
