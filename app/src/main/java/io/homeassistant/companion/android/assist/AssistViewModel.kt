package io.homeassistant.companion.android.assist

import android.app.Application
import android.util.Log
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
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineRunStart
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineSttEnd
import io.homeassistant.companion.android.common.util.AudioRecorder
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject
import io.homeassistant.companion.android.common.R as commonR

@HiltViewModel
class AssistViewModel @Inject constructor(
    val serverManager: ServerManager,
    val audioRecorder: AudioRecorder,
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

    private var recorderJob: Job? = null
    private var recorderQueue: MutableList<ByteArray>? = null
    private var hasPermission = false
    private var requestPermission: (() -> Unit)? = null
    private var requestSilently = true

    private var binaryHandlerId: Int? = null
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
                    AssistMessage(
                        app.getString(commonR.string.no_assist_support, "2023.5", app.getString(commonR.string.no_assist_support_assist_pipeline)),
                        isInput = false
                    )
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
            binaryHandlerId = null
            conversationId = null
            if (it.sttEngine != null) {
                if (hasPermission || requestSilently) {
                    inputMode = AssistInputMode.VOICE_INACTIVE
                    onMicrophoneInput()
                } else { // already requested permission once and was denied
                    inputMode = AssistInputMode.TEXT
                }
            } else {
                inputMode = AssistInputMode.TEXT_ONLY
            }
        } // TODO else Assist isn't ready
    }

    fun onChangeInput() {
        when (inputMode) {
            null, AssistInputMode.TEXT_ONLY -> { /* Do nothing */ }
            AssistInputMode.TEXT -> {
                inputMode = AssistInputMode.VOICE_INACTIVE
                if (hasPermission || requestSilently) {
                    onMicrophoneInput()
                }
            }
            AssistInputMode.VOICE_INACTIVE -> {
                inputMode = AssistInputMode.TEXT
            }
            AssistInputMode.VOICE_ACTIVE -> {
                stopRecording()
                inputMode = AssistInputMode.TEXT
            }
        }
    }

    fun onTextInput(input: String) = runAssistPipeline(input)

    fun onMicrophoneInput() {
        if (!hasPermission) {
            requestPermission?.let { it() }
            return
        }

        if (inputMode == AssistInputMode.VOICE_ACTIVE) {
            stopRecording()
            return
        }

        val recording = try {
            audioRecorder.startRecording()
        } catch (e: Exception) {
            Log.e(TAG, "Exception while starting recording", e)
            false
        }

        if (recording) {
            inputMode = AssistInputMode.VOICE_ACTIVE
            recorderQueue = mutableListOf()

            recorderJob = viewModelScope.launch {
                audioRecorder.audioBytes.collect {
                    recorderQueue?.add(it) ?: sendVoiceData(it)
                }
            }

            runAssistPipeline(null)
        } else {
            _conversation.add(AssistMessage(app.getString(commonR.string.assist_error), isInput = false, isError = true))
        }
    }

    private fun runAssistPipeline(text: String?) {
        val isVoice = text == null

        val userMessage = AssistMessage(text ?: "…", isInput = true)
        _conversation.add(userMessage)
        val haMessage = AssistMessage("…", isInput = false)
        if (!isVoice) _conversation.add(haMessage)
        var message = if (isVoice) userMessage else haMessage

        var job: Job? = null
        job = viewModelScope.launch {
            val flow = if (isVoice) {
                serverManager.webSocketRepository(selectedServerId).runAssistPipelineForVoice(
                    sampleRate = AudioRecorder.SAMPLE_RATE,
                    outputTts = selectedPipeline?.ttsEngine?.isNotBlank() == true,
                    pipelineId = selectedPipeline?.id,
                    conversationId = conversationId
                )
            } else {
                serverManager.webSocketRepository(selectedServerId).runAssistPipelineForText(
                    text = text!!,
                    pipelineId = selectedPipeline?.id,
                    conversationId = conversationId
                )
            }

            flow?.collect {
                when (it.type) {
                    AssistPipelineEventType.RUN_START -> {
                        if (!isVoice) return@collect
                        val data = (it.data as? AssistPipelineRunStart)?.runnerData
                        binaryHandlerId = data?.get("stt_binary_handler_id") as? Int
                    }
                    AssistPipelineEventType.STT_START -> {
                        viewModelScope.launch {
                            recorderQueue?.forEach { item ->
                                sendVoiceData(item)
                            }
                            recorderQueue = null
                        }
                    }
                    AssistPipelineEventType.STT_END -> {
                        stopRecording()
                        (it.data as? AssistPipelineSttEnd)?.sttOutput?.let { response ->
                            val index = _conversation.indexOf(message)
                            _conversation[index] = message.copy(message = response["text"] as String)
                        }
                        _conversation.add(haMessage)
                        message = haMessage
                    }
                    // TODO TTS
                    AssistPipelineEventType.INTENT_END -> {
                        val data = (it.data as? AssistPipelineIntentEnd)?.intentOutput ?: return@collect
                        conversationId = data.conversationId
                        data.response.speech.plain["speech"]?.let { response ->
                            val index = _conversation.indexOf(message)
                            _conversation[index] = message.copy(message = response)
                        }
                    }
                    AssistPipelineEventType.RUN_END -> {
                        stopRecording()
                        job?.cancel()
                    }
                    AssistPipelineEventType.ERROR -> {
                        val errorMessage = (it.data as? AssistPipelineError)?.message ?: return@collect
                        val index = _conversation.indexOf(message)
                        _conversation[index] = message.copy(message = errorMessage, isError = true)
                        stopRecording()
                        job?.cancel()
                    }
                    else -> { /* Do nothing */ }
                }
            } ?: run {
                val messageIndex = _conversation.indexOf(message)
                _conversation[messageIndex] = message.copy(message = app.getString(commonR.string.assist_error), isError = true)
                stopRecording()
            }
        }
    }

    private suspend fun sendVoiceData(data: ByteArray) {
        binaryHandlerId?.let {
            serverManager.webSocketRepository(selectedServerId).sendVoiceData(it, data)
        }
    }

    fun setPermissionInfo(hasPermission: Boolean, callback: () -> Unit) {
        this.hasPermission = hasPermission
        requestPermission = callback
    }

    fun onPermissionResult(granted: Boolean) {
        hasPermission = granted
        if (granted) {
            inputMode = AssistInputMode.VOICE_INACTIVE
            onMicrophoneInput()
        } else if (requestSilently) { // Don't notify the user if they haven't explicitly requested
            inputMode = AssistInputMode.TEXT
        } else {
            _conversation.add(AssistMessage(app.getString(commonR.string.assist_permission), isInput = false))
        }
        requestSilently = false
    }

    fun onStop() {
        requestPermission = null
        stopRecording()
    }

    private fun stopRecording() {
        recorderJob?.cancel()
        recorderJob = null
        if (binaryHandlerId != null) {
            viewModelScope.launch {
                recorderQueue?.forEach {
                    sendVoiceData(it)
                }
                recorderQueue = null
                sendVoiceData(ByteArray(2)) // Empty message to indicate end of recording

                binaryHandlerId = null
            }
        } else {
            recorderQueue = null
        }
        if (inputMode == AssistInputMode.VOICE_ACTIVE) {
            inputMode = AssistInputMode.VOICE_INACTIVE
        }
        audioRecorder.stopRecording()
    }
}
