package io.homeassistant.companion.android.search

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.websocket.WebSocketRepository
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    application: Application,
    private val integrationUseCase: IntegrationRepository,
    private val webSocketRepository: WebSocketRepository
) : AndroidViewModel(application) {

    var searchResult = mutableStateOf("")
        private set

    var speechResult = mutableStateOf("")
        private set

    var supportsConversation = mutableStateOf(false)
        private set

    fun getConversation() {
        viewModelScope.launch {
            speechResult.value = integrationUseCase.getConversation(searchResult.value) ?: ""
        }
    }

    suspend fun isSupportConversation() {
        supportsConversation.value =
            integrationUseCase.isHomeAssistantVersionAtLeast(2023, 1, 0) &&
            webSocketRepository.getConfig()?.components?.contains("conversation") == true
    }
}
