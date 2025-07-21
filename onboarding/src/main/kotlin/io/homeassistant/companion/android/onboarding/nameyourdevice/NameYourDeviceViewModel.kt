package io.homeassistant.companion.android.onboarding.nameyourdevice

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

@HiltViewModel
class NameYourDeviceViewModel @Inject constructor() : ViewModel() {
    private val deviceNameMutableFlow = MutableStateFlow("")
    val deviceNameFlow: StateFlow<String> = deviceNameMutableFlow

    private val isValidNameMutableFlow = MutableStateFlow(false)
    val isValidNameFlow: StateFlow<Boolean> = isValidNameMutableFlow

    fun onDeviceNameChange(name: String) {
        deviceNameMutableFlow.update { name }
        validateName(name)
    }

    fun onSaveClick() {
        // TODO
    }

    private fun validateName(name: String) {
        // TODO
        isValidNameMutableFlow.update { name.isNotEmpty() }
    }
}
