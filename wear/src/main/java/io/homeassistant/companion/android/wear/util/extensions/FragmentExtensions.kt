package io.homeassistant.companion.android.wear.util.extensions

import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import io.homeassistant.companion.android.common.dagger.AppComponent
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor

/**
 * Helper extension to obtain the instance of the appComponent.
 * Requires the fragment to have a valid activity and application instance.
 */
val Fragment.appComponent: AppComponent
    get() = (requireActivity().application as GraphComponentAccessor).appComponent

/**
 * Check if the state of the fragment is at least started,
 * meaning onStart has been called but the state has not reached onSavedInstanceState (yet).
 */
val Fragment.isStarted: Boolean
    get() = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

fun Fragment.requireDrawable(@DrawableRes resourceId: Int): Drawable {
    return requireContext().requireDrawable(resourceId)
}
