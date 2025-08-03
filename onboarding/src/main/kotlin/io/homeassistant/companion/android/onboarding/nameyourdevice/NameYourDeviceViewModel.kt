package io.homeassistant.companion.android.onboarding.nameyourdevice

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal sealed interface NameYourDeviceNavigationEvent {
    data object DeviceNameSaved : NameYourDeviceNavigationEvent
}

@HiltViewModel
internal class NameYourDeviceViewModel @Inject constructor() : ViewModel() {
    private val _navigationEventsFlow = MutableSharedFlow<NameYourDeviceNavigationEvent>()
    val navigationEventsFlow = _navigationEventsFlow.asSharedFlow()

    private val _deviceNameFlow = MutableStateFlow(Build.MODEL)
    val deviceNameFlow = _deviceNameFlow.asStateFlow()

    private val _isValidNameFlow = MutableStateFlow(isValidName(deviceNameFlow.value))
    val isValidNameFlow = _isValidNameFlow.asStateFlow()

    fun onDeviceNameChange(name: String) {
        _deviceNameFlow.update { name }
        validateName(name)
    }

    fun onSaveClick() {
        viewModelScope.launch {
            _navigationEventsFlow.emit(NameYourDeviceNavigationEvent.DeviceNameSaved)
        }
    }

    private fun validateName(name: String) {
        _isValidNameFlow.update { isValidName(name) }
    }

    private fun isValidName(name: String): Boolean {
        // TODO
        return name.isNotEmpty()
    }
}
