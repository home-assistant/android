package io.homeassistant.companion.android.onboarding.locationsharing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

internal sealed interface LocationSharingNavigationEvent {
    data object GoToNextScreen : LocationSharingNavigationEvent
}

@HiltViewModel
internal class LocationSharingViewModel @Inject constructor() : ViewModel() {
    private val _navigationEventsFlow = MutableSharedFlow<LocationSharingNavigationEvent>()
    val navigationEventsFlow = _navigationEventsFlow.asSharedFlow()

    fun onGoToNextScreen() {
        viewModelScope.launch {
            _navigationEventsFlow.emit(LocationSharingNavigationEvent.GoToNextScreen)
        }
    }
}
