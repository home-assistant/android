package io.homeassistant.companion.android.util

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class Navigator {
    private val mutableFlow = MutableSharedFlow<NavigatorItem>(extraBufferCapacity = 1)
    val flow = mutableFlow.asSharedFlow()

    fun navigateTo(navTarget: String) {
        mutableFlow.tryEmit(NavigatorItem(navTarget))
    }

    fun navigateTo(navItem: NavigatorItem) {
        mutableFlow.tryEmit(navItem)
    }

    data class NavigatorItem(
        val id: String,
        val popBackstackTo: String? = null,
        val popBackstackInclusive: Boolean = false,
    )
}
