package io.homeassistant.companion.android.conversation

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.prefs.WearPrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConversationViewModel @Inject constructor(
    application: Application,
    private val serverManager: ServerManager,
    private val wearPrefsRepository: WearPrefsRepository
) : AndroidViewModel(application) {

    var speechResult by mutableStateOf("")
        private set

    var conversationResult by mutableStateOf("")
        private set

    var supportsConversation by mutableStateOf(false)
        private set

    var isHapticEnabled = mutableStateOf(false)
        private set

    var isRegistered by mutableStateOf(false)
        private set

    var checkSupportProgress by mutableStateOf(true)
        private set

    fun getConversation() {
        viewModelScope.launch {
            conversationResult =
                if (serverManager.isRegistered()) serverManager.integrationRepository().getConversation(speechResult) ?: ""
                else ""
        }
    }

    suspend fun isSupportConversation() {
        checkSupportProgress = true
        isRegistered = serverManager.isRegistered()
        supportsConversation =
            serverManager.isRegistered() &&
            serverManager.integrationRepository().isHomeAssistantVersionAtLeast(2023, 1, 0) &&
            serverManager.webSocketRepository().getConfig()?.components?.contains("conversation") == true
        isHapticEnabled.value = wearPrefsRepository.getWearHapticFeedback()
        checkSupportProgress = false
    }

    fun updateSpeechResult(result: String) {
        speechResult = result
    }
}
