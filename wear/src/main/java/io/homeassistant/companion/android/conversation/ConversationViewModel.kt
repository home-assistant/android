package io.homeassistant.companion.android.conversation

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.data.prefs.WearPrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineError
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineEventType
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineIntentEnd
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineResponse
import io.homeassistant.companion.android.conversation.views.AssistMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConversationViewModel @Inject constructor(
    application: Application,
    private val serverManager: ServerManager,
    private val wearPrefsRepository: WearPrefsRepository
) : AndroidViewModel(application) {

    private val app = application

    private var conversationId: String? = null

    var useAssistPipeline by mutableStateOf(false)
        private set

    var allowInput by mutableStateOf(false)
        private set

    var isHapticEnabled by mutableStateOf(false)
        private set

    var currentPipeline by mutableStateOf<AssistPipelineResponse?>(null)
        private set

    private val _pipelines = mutableStateListOf<AssistPipelineResponse>()
    val pipelines: List<AssistPipelineResponse> = _pipelines

    private val startMessage = AssistMessage(application.getString(R.string.assist_how_can_i_assist), isInput = false)
    private val _conversation = mutableStateListOf(startMessage)
    val conversation: List<AssistMessage> = _conversation

    /** @return `true` if the voice input intent should be fired */
    suspend fun onCreate(): Boolean {
        val supported = checkAssistSupport()
        if (!serverManager.isRegistered()) {
            _conversation.clear()
            _conversation.add(
                AssistMessage(app.getString(R.string.not_registered), isInput = false)
            )
        } else if (supported == null) { // Couldn't get config
            _conversation.clear()
            _conversation.add(
                AssistMessage(app.getString(R.string.assist_connnect), isInput = false)
            )
        } else if (!supported) { // Core too old or missing component
            val usingPipelines = serverManager.getServer()?.version?.isAtLeast(2023, 5) == true
            _conversation.clear()
            _conversation.add(
                AssistMessage(
                    if (usingPipelines) {
                        app.getString(R.string.no_assist_support, "2023.5", app.getString(R.string.no_assist_support_assist_pipeline))
                    } else {
                        app.getString(R.string.no_assist_support, "2023.1", app.getString(R.string.no_assist_support_conversation))
                    },
                    isInput = false
                )
            )
        } else {
            if (serverManager.getServer()?.version?.isAtLeast(2023, 5) == true) {
                viewModelScope.launch {
                    loadPipelines()
                }
            }

            return setPipeline(null)
        }

        return false
    }

    private suspend fun checkAssistSupport(): Boolean? {
        isHapticEnabled = wearPrefsRepository.getWearHapticFeedback()
        if (!serverManager.isRegistered()) return false

        val config = serverManager.webSocketRepository().getConfig()
        val onConversationVersion = serverManager.integrationRepository().isHomeAssistantVersionAtLeast(2023, 1, 0)
        val onPipelineVersion = serverManager.integrationRepository().isHomeAssistantVersionAtLeast(2023, 5, 0)

        useAssistPipeline = onPipelineVersion
        return if ((onConversationVersion && !onPipelineVersion && config == null) || (onPipelineVersion && config == null)) {
            null // Version OK but couldn't get config (offline)
        } else {
            (onConversationVersion && !onPipelineVersion && config?.components?.contains("conversation") == true) ||
                (onPipelineVersion && config?.components?.contains("assist_pipeline") == true)
        }
    }

    private suspend fun loadPipelines() {
        val pipelines = serverManager.webSocketRepository().getAssistPipelines()
        pipelines?.let { _pipelines.addAll(it.pipelines) }
    }

    fun changePipeline(id: String) = viewModelScope.launch {
        if (id == currentPipeline?.id) return@launch
        setPipeline(id)
    }

    private suspend fun setPipeline(id: String?): Boolean {
        val pipeline = if (useAssistPipeline) {
            _pipelines.firstOrNull { it.id == id } ?: serverManager.webSocketRepository().getAssistPipeline(id)
        } else {
            null
        }

        if (pipeline != null || !useAssistPipeline) {
            currentPipeline = pipeline

            _conversation.clear()
            _conversation.add(startMessage)
            conversationId = null

            allowInput = true
        } else {
            allowInput = false
            _conversation.clear()
            _conversation.add(
                AssistMessage(app.getString(R.string.assist_error), isInput = false)
            )
        }

        return allowInput // Currently, always launch voice input when setting the pipeline
    }

    fun updateSpeechResult(result: String) = runAssistPipeline(result)

    private fun runAssistPipeline(text: String?) {
        if (text.isNullOrBlank()) return // Voice support is not ready yet

        val userMessage = AssistMessage(text ?: "…", isInput = true)
        _conversation.add(userMessage)
        val haMessage = AssistMessage("…", isInput = false)
        _conversation.add(haMessage)

        var job: Job? = null
        job = viewModelScope.launch {
            val flow = serverManager.integrationRepository().getAssistResponse(
                text = text,
                pipelineId = currentPipeline?.id,
                conversationId = conversationId
            )

            flow?.collect {
                when (it.type) {
                    AssistPipelineEventType.INTENT_END -> {
                        val data = (it.data as? AssistPipelineIntentEnd)?.intentOutput ?: return@collect
                        conversationId = data.conversationId
                        data.response.speech.plain["speech"]?.let { response ->
                            _conversation.indexOf(haMessage).takeIf { pos -> pos >= 0 }?.let { index ->
                                _conversation[index] = haMessage.copy(message = response)
                            }
                        }
                    }
                    AssistPipelineEventType.RUN_END -> {
                        job?.cancel()
                    }
                    AssistPipelineEventType.ERROR -> {
                        val errorMessage = (it.data as? AssistPipelineError)?.message ?: return@collect
                        _conversation.indexOf(haMessage).takeIf { pos -> pos >= 0 }?.let { index ->
                            _conversation[index] = haMessage.copy(message = errorMessage, isError = true)
                        }
                        job?.cancel()
                    }
                    else -> { /* Do nothing */ }
                }
            } ?: run {
                _conversation.indexOf(haMessage).takeIf { pos -> pos >= 0 }?.let { index ->
                    _conversation[index] = haMessage.copy(message = app.getString(R.string.assist_error), isError = true)
                }
            }
        }
    }
}
