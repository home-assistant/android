package io.homeassistant.companion.android.onboarding.locationsharing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

internal sealed interface LocationSharingNavigationEvent {
    data object GoToNextScreen : LocationSharingNavigationEvent
}

@HiltViewModel
internal class LocationSharingViewModel @Inject constructor() : ViewModel() {
    private val navigationEventsMutableFlow = MutableSharedFlow<LocationSharingNavigationEvent>()
    val navigationEventFlow: Flow<LocationSharingNavigationEvent> = navigationEventsMutableFlow

    fun onGoToNextScreen() {
        viewModelScope.launch {
            navigationEventsMutableFlow.emit(LocationSharingNavigationEvent.GoToNextScreen)
        }
    }
}
