package io.homeassistant.companion.android.onboarding.nameyourdevice

import android.os.Build
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

internal sealed interface NameYourDeviceNavigationEvent {
    data object DeviceNameSaved : NameYourDeviceNavigationEvent
}

@HiltViewModel
internal class NameYourDeviceViewModel @Inject constructor() : ViewModel() {
    private val navigationEventsMutableFlow = MutableSharedFlow<NameYourDeviceNavigationEvent>()
    val navigationEventsFlow: Flow<NameYourDeviceNavigationEvent> = navigationEventsMutableFlow

    private val deviceNameMutableFlow = MutableStateFlow(Build.MODEL)
    val deviceNameFlow: StateFlow<String> = deviceNameMutableFlow

    private val isValidNameMutableFlow = MutableStateFlow(isValidName(deviceNameFlow.value))
    val isValidNameFlow: StateFlow<Boolean> = isValidNameMutableFlow

    fun onDeviceNameChange(name: String) {
        deviceNameMutableFlow.update { name }
        validateName(name)
    }

    fun onSaveClick() {
        viewModelScope.launch {
            navigationEventsMutableFlow.emit(NameYourDeviceNavigationEvent.DeviceNameSaved)
        }
    }

    private fun validateName(name: String) {
        isValidNameMutableFlow.update { isValidName(name) }
    }

    private fun isValidName(name: String): Boolean {
        // TODO
        return name.isNotEmpty()
    }
}
