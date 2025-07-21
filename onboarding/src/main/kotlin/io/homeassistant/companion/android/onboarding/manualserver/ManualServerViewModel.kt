package io.homeassistant.companion.android.onboarding.manualserver

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

@HiltViewModel
class ManualServerViewModel @Inject constructor() : ViewModel() {
    private val serverUrlMutableFlow = MutableStateFlow("")
    val serverUrlFlow: StateFlow<String> = serverUrlMutableFlow

    private val isServerUrlValidMutableFlow = MutableStateFlow(false)
    val isServerUrlValidFlow: StateFlow<Boolean> = isServerUrlValidMutableFlow

    fun onServerUrlChange(url: String) {
        serverUrlMutableFlow.update { url }
        validateServerUrl(url)
    }

    fun onConnectClick() {
    }

    private fun validateServerUrl(url: String) {
        isServerUrlValidMutableFlow.update {
            // TODO validate URL
            url.isNotBlank()
        }
    }
}
