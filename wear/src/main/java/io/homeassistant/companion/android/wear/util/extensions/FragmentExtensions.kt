package io.homeassistant.companion.android.wear.util.extensions

import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.viewbinding.ViewBinding
import io.homeassistant.companion.android.common.dagger.AppComponent
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.wear.util.delegates.FragmentViewBindingDelegate

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

/**
 * Helper extension to create a fragment view binding delegate.
 * This delegate can be used to easily obtain instances of created views inside the view binding.
 * Only views that have a id set explicitly can be accessed through this view binding.
 */
fun <T : ViewBinding> Fragment.viewBinding(viewBindingFactory: (View) -> T) =
    FragmentViewBindingDelegate(this, viewBindingFactory)