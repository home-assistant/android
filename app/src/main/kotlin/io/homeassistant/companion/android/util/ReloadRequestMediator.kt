package io.homeassistant.companion.android.util

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Carries reload requests from the server commands to the frontend. Requests emitted while the
 * frontend is not open are dropped since there is nothing to reload.
 */
@Singleton
class ReloadRequestMediator @Inject constructor() {

    private val _eventFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val eventFlow = _eventFlow.asSharedFlow()

    fun emitReloadRequestEvent() {
        _eventFlow.tryEmit(Unit)
    }
}
