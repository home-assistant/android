package io.homeassistant.companion.android.onboarding.nameyourdevice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface NameYourDeviceNavigationEvent {
    object DeviceNameSaved : NameYourDeviceNavigationEvent
}

@HiltViewModel
class NameYourDeviceViewModel @Inject constructor() : ViewModel() {
    private val _navigationEvents = MutableSharedFlow<NameYourDeviceNavigationEvent>()
    val navigationEvents: Flow<NameYourDeviceNavigationEvent> = _navigationEvents

    private val deviceNameMutableFlow = MutableStateFlow("")
    val deviceNameFlow: StateFlow<String> = deviceNameMutableFlow

    private val isValidNameMutableFlow = MutableStateFlow(false)
    val isValidNameFlow: StateFlow<Boolean> = isValidNameMutableFlow

    fun onDeviceNameChange(name: String) {
        deviceNameMutableFlow.update { name }
        validateName(name)
    }

    fun onSaveClick() {
        viewModelScope.launch {
            _navigationEvents.emit(NameYourDeviceNavigationEvent.DeviceNameSaved)
        }
    }

    private fun validateName(name: String) {
        // TODO
        isValidNameMutableFlow.update { name.isNotEmpty() }
    }
}
