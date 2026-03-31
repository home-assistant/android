package io.homeassistant.companion.android.util

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

@Singleton
class PermissionRequestMediator @Inject constructor() {

    private val _eventFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val eventFlow = _eventFlow.asSharedFlow()

    fun emitPermissionRequestEvent(permission: String) {
        _eventFlow.tryEmit(permission)
    }
}
