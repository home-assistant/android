package io.homeassistant.companion.android.util

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import io.homeassistant.companion.android.common.dagger.AppComponent
import io.homeassistant.companion.android.common.dagger.DomainComponent
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor

val Activity.appComponent: AppComponent
    get() = (application as GraphComponentAccessor).appComponent

val Activity.domainComponent: DomainComponent
    get() = (application as GraphComponentAccessor).domainComponent

/**
 * Check if the state of the activity is at least started,
 * meaning onStart has been called but the state has not reached onSavedInstanceState (yet).
 */
val ComponentActivity.isStarted: Boolean
    get() = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
