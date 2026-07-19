package io.homeassistant.companion.android.launch.link

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.util.FailFast
import io.homeassistant.companion.android.settings.server.ServerChooserItemsUseCase
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Resolves the deep link that launched [LinkActivity] and drives its UI.
 */
@HiltViewModel
class LinkViewModel @Inject constructor(
    private val linkHandler: LinkHandler,
    private val serverChooserItemsUseCase: ServerChooserItemsUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<LinkUiState>(LinkUiState.Loading)
    val uiState: StateFlow<LinkUiState> = _uiState.asStateFlow()

    // A Channel (rather than a SharedFlow) is required because some links resolve synchronously
    // (e.g. an invitation link is parsed without any I/O). The resulting event is then emitted from
    // onCreate before the activity starts collecting; a Channel buffers it and delivers it exactly
    // once, whereas a SharedFlow with no subscriber would drop it and the activity would hang.
    private val _navigationEvents = Channel<LinkNavigationEvent>(Channel.BUFFERED)
    val navigationEvents: Flow<LinkNavigationEvent> = _navigationEvents.receiveAsFlow()

    /**
     * Entry point called by the activity with the deep link [uri] from the launching intent.
     *
     * Resolves the destination and either updates [uiState] (when the user must pick a server) or
     * emits a [LinkNavigationEvent]. A `null` [uri] is an invalid caller intent: it fails fast and
     * finishes. Expected to be called once per launch; the activity guards against re-handling the
     * same intent on recreation.
     */
    fun onLinkReceived(uri: Uri?) {
        if (uri == null) {
            FailFast.fail { "Missing data in caller Intent" }
            _navigationEvents.trySend(LinkNavigationEvent.Finish)
            return
        }
        viewModelScope.launch {
            when (val destination = linkHandler.handleLink(uri)) {
                LinkDestination.NoDestination -> _navigationEvents.trySend(LinkNavigationEvent.Finish)
                is LinkDestination.Onboarding ->
                    _navigationEvents.trySend(LinkNavigationEvent.OpenInvitation(destination.serverUrl))
                is LinkDestination.Webview ->
                    _navigationEvents.trySend(
                        LinkNavigationEvent.NavigateToWebView(destination.target, destination.serverId),
                    )
                is LinkDestination.ServerPicker ->
                    serverChooserItemsUseCase(destination.servers).collect { items ->
                        _uiState.value = LinkUiState.ChoosingServer(
                            items = items,
                            target = destination.target,
                        )
                    }
            }
        }
    }

    fun onServerSelected(serverId: Int) {
        val state = _uiState.value as? LinkUiState.ChoosingServer ?: return
        _navigationEvents.trySend(LinkNavigationEvent.NavigateToWebView(target = state.target, serverId = serverId))
    }

    fun onServerChooserDismissed() {
        _navigationEvents.trySend(LinkNavigationEvent.Finish)
    }
}
