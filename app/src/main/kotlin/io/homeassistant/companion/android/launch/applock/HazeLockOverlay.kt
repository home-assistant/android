package io.homeassistant.companion.android.launch.applock

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

/**
 * Full-screen overlay that blurs the content marked with `hazeSource` and blocks all touch events.
 *
 * Only renders when [hazeState]`.blurEnabled` is `true`. The blur is provided by [hazeEffect]
 * which renders the blurred snapshot of content captured by the paired `hazeSource` modifier.
 * All pointer events are intercepted and consumed so that taps, swipes, and gestures cannot
 * reach the composables underneath while the app is locked.
 *
 * @param hazeState shared state that connects this overlay to the content marked with `hazeSource`.
 *   When `blurEnabled` is `false`, the overlay is not composed at all.
 * @param modifier optional modifier for the overlay
 * @param style the blur style to apply, defaults to [HazeMaterials.thin]
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
internal fun HazeLockOverlay(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    style: HazeStyle = HazeMaterials.thin(),
) {
    if (!hazeState.blurEnabled) return

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
            .hazeEffect(hazeState, style = style),
    )
}
