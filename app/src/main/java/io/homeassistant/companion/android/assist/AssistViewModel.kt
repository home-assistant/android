package io.homeassistant.companion.android.assist

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.assist.ui.AssistMessage
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineError
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineEventType
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineIntentEnd
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineResponse
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject
import io.homeassistant.companion.android.common.R as commonR

@HiltViewModel
class AssistViewModel @Inject constructor(
    val serverManager: ServerManager,
    application: Application
) : AndroidViewModel(application) {

    companion object {
        const val TAG = "AssistViewModel"
    }

    enum class AssistInputMode {
        TEXT,
        TEXT_ONLY,
        VOICE_INACTIVE,
        VOICE_ACTIVE
    }

    private val app = application

    private var selectedServerId = ServerManager.SERVER_ID_ACTIVE
    private var selectedPipeline: AssistPipelineResponse? = null

    private var conversationId: String? = null

    private val startMessage = AssistMessage(application.getString(commonR.string.assist_how_can_i_assist), isInput = false)
    private val _conversation = mutableStateListOf(startMessage)
    val conversation: List<AssistMessage> = _conversation

    var inputMode by mutableStateOf<AssistInputMode?>(null)
        private set

    fun onCreate() {
        // TODO pass arguments
        viewModelScope.launch {
            val supported = checkSupport()
            if (supported) {
                setPipeline(null)
            } else {
                _conversation.clear()
                _conversation.add(
                    AssistMessage(app.getString(commonR.string.no_assist_support_assist_pipeline), isInput = false)
                )
            }
        }
    }

    private suspend fun checkSupport(): Boolean {
        if (!serverManager.isRegistered()) return false
        return serverManager.integrationRepository().isHomeAssistantVersionAtLeast(2023, 5, 0) &&
            serverManager.webSocketRepository().getConfig()?.components?.contains("assist_pipeline") == true
    }

    fun changePipeline(serverId: Int, id: String) = viewModelScope.launch {
        selectedServerId = serverId
        setPipeline(id)
    }

    private suspend fun setPipeline(id: String?) {
        selectedPipeline = serverManager.webSocketRepository(selectedServerId).getAssistPipeline(id)
        selectedPipeline?.let {
            _conversation.clear()
            _conversation.add(startMessage)
            if (it.sttEngine != null) {
                inputMode = AssistInputMode.VOICE_INACTIVE
                // TODO or active + start input based on arguments
            } else {
                inputMode = AssistInputMode.TEXT_ONLY
            }
        } // TODO else Assist isn't ready
    }

    fun onChangeInput() {
        when (inputMode) {
            null, AssistInputMode.TEXT_ONLY -> { /* Do nothing */ }
            AssistInputMode.TEXT -> {
                inputMode = AssistInputMode.VOICE_INACTIVE // TODO active if permission?
            }
            AssistInputMode.VOICE_INACTIVE -> {
                inputMode = AssistInputMode.TEXT
            }
            AssistInputMode.VOICE_ACTIVE -> {
                // TODO stop current input
                inputMode = AssistInputMode.TEXT
            }
        }
    }

    fun onTextInput(input: String) {
        _conversation.add(AssistMessage(input, isInput = true))
        val message = AssistMessage("…", isInput = false)
        _conversation.add(message)

        var job: Job? = null
        job = viewModelScope.launch {
            serverManager.webSocketRepository(selectedServerId).runAssistPipelineForText(
                text = input,
                pipelineId = selectedPipeline?.id,
                conversationId = conversationId
            )?.collect {
                when (it.type) {
                    AssistPipelineEventType.INTENT_END -> {
                        val data = (it.data as? AssistPipelineIntentEnd)?.intentOutput ?: return@collect
                        conversationId = data.conversationId
                        data.response.speech.plain["speech"]?.let { response ->
                            val index = _conversation.indexOf(message)
                            _conversation[index] = message.copy(message = response)
                        }
                        job?.cancel()
                    }
                    AssistPipelineEventType.ERROR -> {
                        val errorMessage = (it.data as? AssistPipelineError)?.message ?: return@collect
                        val index = _conversation.indexOf(message)
                        _conversation[index] = message.copy(message = errorMessage, isError = true)
                        job?.cancel()
                    }
                    else -> { /* Do nothing */ }
                }
            } ?: run {
                val messageIndex = _conversation.indexOf(message)
                _conversation[messageIndex] = message.copy(message = app.getString(commonR.string.assist_error), isError = true)
            }
        }
    }

    fun onMicrophoneInput() {
        _conversation.add(AssistMessage("…", isInput = true))
    }
}
