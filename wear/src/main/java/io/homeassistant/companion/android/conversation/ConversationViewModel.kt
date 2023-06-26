package io.homeassistant.companion.android.conversation

import android.app.Application
import android.content.pm.PackageManager
import android.util.Log
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
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineRunStart
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineSttEnd
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineTtsEnd
import io.homeassistant.companion.android.common.util.AudioRecorder
import io.homeassistant.companion.android.common.util.AudioUrlPlayer
import io.homeassistant.companion.android.conversation.views.AssistMessage
import io.homeassistant.companion.android.util.UrlUtil
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConversationViewModel @Inject constructor(
    application: Application,
    private val serverManager: ServerManager,
    private val audioRecorder: AudioRecorder,
    private val audioUrlPlayer: AudioUrlPlayer,
    private val wearPrefsRepository: WearPrefsRepository
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ConvViewModel"
    }

    enum class AssistInputMode {
        VOICE_ACTIVE,
        VOICE_INACTIVE,
        TEXT,
        NONE
    }

    private val app = application

    private var useAssistPipeline = false

    private var binaryHandlerId: Int? = null
    private var conversationId: String? = null

    var pipelineHandlesInput by mutableStateOf(false)
        private set

    var inputMode by mutableStateOf(AssistInputMode.NONE)
        private set

    var isHapticEnabled by mutableStateOf(false)
        private set

    var currentPipeline by mutableStateOf<AssistPipelineResponse?>(null)
        private set

    private var recorderJob: Job? = null
    private var recorderQueue: MutableList<ByteArray>? = null
    private val hasMicrophone = app.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
    private var hasPermission = false
    private var requestPermission: (() -> Unit)? = null
    private var requestSilently = true

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

        stopRecording()
        stopPlayback()

        setPipeline(id)
    }

    private suspend fun setPipeline(id: String?): Boolean {
        val pipeline = if (useAssistPipeline) {
            _pipelines.firstOrNull { it.id == id } ?: serverManager.webSocketRepository().getAssistPipeline(id)
        } else {
            null
        }

        pipelineHandlesInput = false
        if (pipeline != null || !useAssistPipeline) {
            currentPipeline = pipeline

            _conversation.clear()
            _conversation.add(startMessage)
            binaryHandlerId = null
            conversationId = null

            if (pipeline != null && hasMicrophone && pipeline.sttEngine != null) {
                if (hasPermission || requestSilently) {
                    inputMode = AssistInputMode.VOICE_INACTIVE
                    pipelineHandlesInput = true
                    onMicrophoneInput()
                } else {
                    inputMode = AssistInputMode.TEXT
                }
            } else {
                inputMode = AssistInputMode.TEXT
            }
        } else {
            inputMode = AssistInputMode.NONE
            _conversation.clear()
            _conversation.add(
                AssistMessage(app.getString(R.string.assist_error), isInput = false)
            )
        }

        return inputMode == AssistInputMode.TEXT
    }

    fun updateSpeechResult(result: String) = runAssistPipeline(result)

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
            recorderQueue = mutableListOf()
            recorderJob = viewModelScope.launch {
                audioRecorder.audioBytes.collect {
                    recorderQueue?.add(it) ?: sendVoiceData(it)
                }
            }

            inputMode = AssistInputMode.VOICE_ACTIVE
            runAssistPipeline(null)
        } else {
            _conversation.add(AssistMessage(app.getString(R.string.assist_error), isInput = false, isError = true))
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
                serverManager.webSocketRepository().runAssistPipelineForVoice(
                    sampleRate = AudioRecorder.SAMPLE_RATE,
                    outputTts = currentPipeline?.ttsEngine?.isNotBlank() == true,
                    pipelineId = currentPipeline?.id,
                    conversationId = conversationId
                )
            } else {
                serverManager.integrationRepository().getAssistResponse(
                    text = text!!,
                    pipelineId = currentPipeline?.id,
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
                            _conversation.indexOf(message).takeIf { pos -> pos >= 0 }?.let { index ->
                                _conversation[index] = message.copy(message = response["text"] as String)
                            }
                        }
                        _conversation.add(haMessage)
                        message = haMessage
                    }
                    AssistPipelineEventType.INTENT_END -> {
                        val data = (it.data as? AssistPipelineIntentEnd)?.intentOutput ?: return@collect
                        conversationId = data.conversationId
                        data.response.speech.plain["speech"]?.let { response ->
                            _conversation.indexOf(message).takeIf { pos -> pos >= 0 }?.let { index ->
                                _conversation[index] = message.copy(message = response)
                            }
                        }
                    }
                    AssistPipelineEventType.TTS_END -> {
                        if (!isVoice) return@collect
                        val audioPath = (it.data as? AssistPipelineTtsEnd)?.ttsOutput?.url
                        if (!audioPath.isNullOrBlank()) {
                            playAudio(audioPath)
                        }
                    }
                    AssistPipelineEventType.RUN_END -> {
                        stopRecording()
                        job?.cancel()
                    }
                    AssistPipelineEventType.ERROR -> {
                        val errorMessage = (it.data as? AssistPipelineError)?.message ?: return@collect
                        _conversation.indexOf(message).takeIf { pos -> pos >= 0 }?.let { index ->
                            _conversation[index] = message.copy(message = errorMessage, isError = true)
                        }
                        stopRecording()
                        job?.cancel()
                    }
                    else -> { /* Do nothing */ }
                }
            } ?: run {
                _conversation.indexOf(message).takeIf { pos -> pos >= 0 }?.let { index ->
                    _conversation[index] = message.copy(message = app.getString(R.string.assist_error), isError = true)
                }
            }
        }
    }

    private fun sendVoiceData(data: ByteArray) {
        binaryHandlerId?.let {
            viewModelScope.launch {
                // Launch to prevent blocking the output flow if the network is slow
                serverManager.webSocketRepository().sendVoiceData(it, data)
            }
        }
    }

    private fun playAudio(path: String) {
        UrlUtil.handle(serverManager.getServer()?.connection?.getUrl(), path)?.let {
            viewModelScope.launch {
                audioUrlPlayer.playAudio(it.toString())
            }
        }
    }

    fun setPermissionInfo(hasPermission: Boolean, callback: () -> Unit) {
        this.hasPermission = hasPermission
        requestPermission = callback
    }

    fun onPermissionResult(granted: Boolean, voiceInputIntent: (() -> Unit)) {
        hasPermission = granted
        pipelineHandlesInput = currentPipeline?.sttEngine != null && granted
        if (granted) {
            inputMode = AssistInputMode.VOICE_INACTIVE
            onMicrophoneInput()
        } else if (requestSilently) { // Don't notify the user if they haven't explicitly requested
            inputMode = AssistInputMode.TEXT
            voiceInputIntent()
        }
        requestSilently = false
    }

    fun onConversationScreenHidden() {
        stopRecording()
        stopPlayback()
    }

    fun onPause() {
        requestPermission = null
        stopRecording()
        stopPlayback()
    }

    private fun stopRecording() {
        audioRecorder.stopRecording()
        recorderJob?.cancel()
        recorderJob = null
        if (binaryHandlerId != null) {
            viewModelScope.launch {
                recorderQueue?.forEach {
                    sendVoiceData(it)
                }
                recorderQueue = null
                sendVoiceData(byteArrayOf()) // Empty message to indicate end of recording
                binaryHandlerId = null
            }
        } else {
            recorderQueue = null
        }
        if (inputMode == AssistInputMode.VOICE_ACTIVE) {
            inputMode = AssistInputMode.VOICE_INACTIVE
        }
    }

    private fun stopPlayback() = audioUrlPlayer.stop()
}
