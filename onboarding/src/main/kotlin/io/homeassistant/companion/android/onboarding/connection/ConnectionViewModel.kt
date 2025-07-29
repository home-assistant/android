package io.homeassistant.companion.android.onboarding.connection

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.onboarding.connection.navigation.ConnectionRoute
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

internal sealed interface ConnectionNavigationEvent {
    object Authenticated : ConnectionNavigationEvent
}

@HiltViewModel
internal class ConnectionViewModel @Inject constructor(savedStateHandle: SavedStateHandle) : ViewModel() {
    private val route: ConnectionRoute = savedStateHandle.toRoute()
    val url = route.url

    private val navigationEventsMutableFlow = MutableSharedFlow<ConnectionNavigationEvent>()
    val navigationEvents: Flow<ConnectionNavigationEvent> = navigationEventsMutableFlow

    init {
        viewModelScope.launch {
            delay(1.seconds)
            // navigationEventsMutableFlow.emit(ConnectionNavigationEvent.Authenticated)
        }
    }
}
