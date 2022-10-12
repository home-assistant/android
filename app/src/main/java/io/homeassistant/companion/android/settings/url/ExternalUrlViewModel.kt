package io.homeassistant.companion.android.settings.url

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.url.UrlRepository
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExternalUrlViewModel @Inject constructor(
    private val urlRepository: UrlRepository,
    application: Application
) : AndroidViewModel(application) {

    var canUseCloud by mutableStateOf(false)
        private set

    var useCloud by mutableStateOf(false)
        private set

    var externalUrl by mutableStateOf("")
        private set

    init {
        viewModelScope.launch {
            canUseCloud = urlRepository.canUseCloud()
            useCloud = urlRepository.shouldUseCloud()
            externalUrl = urlRepository.getUrl(isInternal = false, ignoreCloud = true).toString()
        }
    }

    fun toggleCloud(use: Boolean) {
        viewModelScope.launch {
            useCloud = if (use && canUseCloud) {
                urlRepository.setUseCloud(true)
                true
            } else {
                urlRepository.setUseCloud(false)
                false
            }
        }
    }

    fun updateExternalUrl(url: String) {
        viewModelScope.launch {
            urlRepository.saveUrl(url, false)
            externalUrl = urlRepository.getUrl(isInternal = false, ignoreCloud = true)?.toString() ?: ""
        }
    }
}
