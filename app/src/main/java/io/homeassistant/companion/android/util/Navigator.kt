package io.homeassistant.companion.android.util

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class Navigator {
    private val _sharedFlow = MutableSharedFlow<NavigatorItem>(extraBufferCapacity = 1)
    val flow = _sharedFlow.asSharedFlow()

    fun navigateTo(navTarget: String) {
        _sharedFlow.tryEmit(NavigatorItem(navTarget))
    }

    fun navigateTo(navItem: NavigatorItem) {
        _sharedFlow.tryEmit(navItem)
    }

    data class NavigatorItem(
        val id: String,
        val popBackstackTo: String? = null,
        val popBackstackInclusive: Boolean = false
    )
}
