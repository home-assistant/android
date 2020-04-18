package io.homeassistant.companion.android.wear.util.extensions

import android.app.Activity
import android.view.LayoutInflater
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.viewbinding.ViewBinding
import io.homeassistant.companion.android.common.dagger.AppComponent
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor

val Activity.appComponent: AppComponent
    get() = (application as GraphComponentAccessor).appComponent

/**
 * Check if the state of the activity is at least started,
 * meaning onStart has been called but the state has not reached onSavedInstanceState (yet).
 */
val ComponentActivity.isStarted: Boolean
    get() = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

/**
 * Helper extension to create a view binding.
 * This delegate can be used to easily obtain instances of created views inside the view binding.
 * Only views that have a id set explicitly can be accessed through this view binding.
 */
inline fun <T : ViewBinding> Activity.viewBinding(crossinline bindingInflater: (LayoutInflater) -> T) =
    lazy(LazyThreadSafetyMode.NONE) {
        bindingInflater(layoutInflater)
    }