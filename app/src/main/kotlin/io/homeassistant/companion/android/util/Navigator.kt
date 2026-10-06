package io.homeassistant.companion.android.util

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

class Navigator {
    private val mutableFlow = Channel<NavigatorItem>(Channel.BUFFERED)
    val flow = mutableFlow.receiveAsFlow()

    fun navigateTo(navTarget: String) {
        mutableFlow.trySend(NavigatorItem(navTarget))
    }

    fun navigateTo(navItem: NavigatorItem) {
        mutableFlow.trySend(navItem)
    }

    data class NavigatorItem(
        val id: String,
        val popBackstackTo: String? = null,
        val popBackstackInclusive: Boolean = false,
    )
}
