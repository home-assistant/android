package io.homeassistant.companion.android.util

import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.InputDeviceCompat
import androidx.core.view.MotionEventCompat
import androidx.core.view.ViewConfigurationCompat
import kotlinx.coroutines.launch

val LocalRotaryEventDispatcher = staticCompositionLocalOf<RotaryEventDispatcher> {
    noLocalProvidedFor("LocalRotaryEventDispatcher")
}

class RotaryEventDispatcher(
    var scrollState: ScrollableState? = null
) {
    suspend fun onRotate(delta: Float): Float? =
        scrollState?.scrollBy(delta)
}

@Composable
fun RotaryEventHandlerSetup(rotaryEventDispatcher: RotaryEventDispatcher) {
    val view = LocalView.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    view.requestFocus()
    view.setOnGenericMotionListener { _, event ->
        if (event?.action != MotionEvent.ACTION_SCROLL || !event.isFromSource(InputDeviceCompat.SOURCE_ROTARY_ENCODER)) {
            return@setOnGenericMotionListener false
        }

        val delta = -event.getAxisValue(MotionEventCompat.AXIS_SCROLL) * ViewConfigurationCompat.getScaledVerticalScrollFactor(
            ViewConfiguration.get(context), context
        )

        scope.launch {
            rotaryEventDispatcher.onRotate(delta)
        }
        true
    }
}

@Composable
fun RotaryEventState(scrollState: ScrollableState?) {
    val dispater = LocalRotaryEventDispatcher.current
    SideEffect {
        dispater.scrollState = scrollState
    }
}

private fun noLocalProvidedFor(noOp: String): Nothing {
    error("composition local $noOp not present")
}
