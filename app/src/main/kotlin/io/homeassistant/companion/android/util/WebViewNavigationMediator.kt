package io.homeassistant.companion.android.util

import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.frontend.navigation.FrontendTarget
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Carries webview navigation requests from the server commands to the visible frontend.
 */
@Singleton
class WebViewNavigationMediator @Inject constructor() {

    private val _visibleServerId = MutableStateFlow<Int?>(null)

    /** The server shown by the visible frontend, or `null` when no frontend is visible. */
    val visibleServerId: StateFlow<Int?> = _visibleServerId.asStateFlow()

    private val _navigationRequests = MutableSharedFlow<FrontendTarget>(extraBufferCapacity = 1)
    val navigationRequests = _navigationRequests.asSharedFlow()

    /** [ServerManager.SERVER_ID_ACTIVE] is normalized to `null` since it does not identify a server. */
    fun setVisibleServer(serverId: Int?) {
        _visibleServerId.value = serverId?.takeUnless { it == ServerManager.SERVER_ID_ACTIVE }
    }

    fun requestNavigation(target: FrontendTarget) {
        _navigationRequests.tryEmit(target)
    }
}
