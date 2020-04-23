package io.homeassistant.companion.android.util

import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import io.homeassistant.companion.android.common.dagger.AppComponent
import io.homeassistant.companion.android.common.dagger.DomainComponent
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor

val Fragment.appComponent: AppComponent
    get() = (requireActivity().application as GraphComponentAccessor).appComponent

val Fragment.domainComponent: DomainComponent
    get() = (requireActivity().application as GraphComponentAccessor).domainComponent

/**
 * Check if the state of the fragment is at least started,
 * meaning onStart has been called but the state has not reached onSavedInstanceState (yet).
 */
val Fragment.isStarted: Boolean
    get() = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
