package io.homeassistant.companion.android.util.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * [androidx.lifecycle.compose.dropUnlessResumed] for a callback taking an argument: [block] only
 * runs while the current [LocalLifecycleOwner] is resumed, so a tap repeated while the screen is
 * already leaving is ignored.
 */
@Composable
@ReadOnlyComposable
fun <T> dropUnlessResumed(block: (T) -> Unit): (T) -> Unit {
    val lifecycleOwner = LocalLifecycleOwner.current
    return { argument ->
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            block(argument)
        }
    }
}
