package io.homeassistant.companion.android.onboarding.manualserver

import android.webkit.URLUtil
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@HiltViewModel
internal class ManualServerViewModel @Inject constructor() : ViewModel() {
    private val serverUrlMutableFlow = MutableStateFlow("")
    val serverUrlFlow = serverUrlMutableFlow.asStateFlow()

    private val isServerUrlValidMutableFlow = MutableStateFlow(false)
    val isServerUrlValidFlow = isServerUrlValidMutableFlow.asStateFlow()

    fun onServerUrlChange(url: String) {
        serverUrlMutableFlow.update { url }
        validateServerUrl(url)
    }

    private fun validateServerUrl(url: String) {
        isServerUrlValidMutableFlow.update {
            URLUtil.isValidUrl(url)
        }
    }
}
