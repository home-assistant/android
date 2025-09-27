package io.homeassistant.companion.android.common.assist

import android.app.Application
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineError
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineEventType
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineIntentEnd
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineIntentProgress
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineRunStart
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineSttEnd
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineTtsEnd
import io.homeassistant.companion.android.common.util.AudioRecorder
import io.homeassistant.companion.android.common.util.AudioUrlPlayer
import io.homeassistant.companion.android.util.UrlUtil
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

sealed interface AssistEvent {
    sealed class Message(val message: String) : AssistEvent {
        class Input(message: String) : Message(message)
        class Output(message: String) : Message(message)
        class Error(message: String) : Message(message)
    }
    class MessageChunk(val chunk: String) : AssistEvent
    data object ContinueConversation : AssistEvent
}

abstract class AssistViewModelBase(
    private val serverManager: ServerManager,
    private val audioRecorder: AudioRecorder,
    private val audioUrlPlayer: AudioUrlPlayer,
    application: Application,
) : AndroidViewModel(application) {

    companion object {
        const val PIPELINE_PREFERRED = "preferred"
        const val PIPELINE_LAST_USED = "last_used"
    }

    enum class AssistInputMode {
        TEXT,
        TEXT_ONLY,
        VOICE_INACTIVE,
        VOICE_ACTIVE,
        BLOCKED,
    }

    protected val app = application

    protected var selectedServerId = ServerManager.SERVER_ID_ACTIVE

    protected var recorderProactive = false
    private var recorderJob: Job? = null
    private var recorderQueue: MutableList<ByteArray>? = null
    protected val hasMicrophone = app.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
    protected var hasPermission = false

    private var binaryHandlerId: Int? = null
    private var conversationId: String? = null
    private var continueConversation = AtomicBoolean(false)

    suspend fun isRegistered(): Boolean = serverManager.isRegistered()

    abstract fun getInput(): AssistInputMode?
    abstract fun setInput(inputMode: AssistInputMode)

    protected fun clearPipelineData() {
        binaryHandlerId = null
        conversationId = null
    }

    /**
     * @param text input to run an intent pipeline with, or `null` to run a STT pipeline (check if
     * STT is supported _before_ calling this function)
     * @param pipeline information about the pipeline, or `null` to use the server's default
     * @param onEvent callback for events that should be use to update the UI
     */
    protected fun runAssistPipelineInternal(
        text: String?,
        pipeline: AssistPipelineResponse?,
        onEvent: (AssistEvent) -> Unit,
    ) {
        val isVoice = text == null
        var job: Job? = null
        job = viewModelScope.launch {
            val flow = if (isVoice) {
                serverManager.webSocketRepository(selectedServerId).runAssistPipelineForVoice(
                    sampleRate = AudioRecorder.SAMPLE_RATE,
                    outputTts = pipeline?.ttsEngine?.isNotBlank() == true,
                    pipelineId = pipeline?.id,
                    conversationId = conversationId,
                )
            } else {
                serverManager.integrationRepository(selectedServerId).getAssistResponse(
                    text = text,
                    pipelineId = pipeline?.id,
                    conversationId = conversationId,
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
                            binaryHandlerId?.let { id ->
                                // Manually loop here to avoid the queue being reset too soon
                                recorderQueue?.forEach { data ->
                                    serverManager.webSocketRepository(selectedServerId).sendVoiceData(id, data)
                                }
                            }
                            recorderQueue = null
                        }
                    }
                    AssistPipelineEventType.STT_END -> {
                        stopRecording()
                        (it.data as? AssistPipelineSttEnd)?.sttOutput?.let { response ->
                            onEvent(AssistEvent.Message.Input(response["text"] as String))
                        }
                    }
                    AssistPipelineEventType.INTENT_PROGRESS -> {
                        (it.data as? AssistPipelineIntentProgress)?.chatLogDelta?.content?.let { delta ->
                            onEvent(AssistEvent.MessageChunk(delta))
                        }
                    }
                    AssistPipelineEventType.INTENT_END -> {
                        val data = (it.data as? AssistPipelineIntentEnd)?.intentOutput ?: return@collect
                        conversationId = data.conversationId
                        continueConversation.set(data.continueConversation)
                        data.response.speech?.plain?.get("speech")?.let { speech ->
                            onEvent(AssistEvent.Message.Output(speech))
                        }
                    }
                    AssistPipelineEventType.TTS_END -> {
                        if (!isVoice) return@collect
                        viewModelScope.launch {
                            val audioPath = (it.data as? AssistPipelineTtsEnd)?.ttsOutput?.url
                            if (!audioPath.isNullOrBlank()) {
                                playAudio(audioPath)
                            }
                            // We send the continueConversation flag here after getting it from AssistPipelineEventType.INTENT_END so that
                            // we let the mediaplayer finishing playing the audio before recording a new entry from the user.
                            if (continueConversation.getAndSet(false)) {
                                onEvent(AssistEvent.ContinueConversation)
                            }
                        }
                    }
                    AssistPipelineEventType.RUN_END -> {
                        stopRecording()
                        job?.cancel()
                    }
                    AssistPipelineEventType.ERROR -> {
                        val errorMessage = (it.data as? AssistPipelineError)?.message ?: return@collect
                        onEvent(AssistEvent.Message.Error(errorMessage))
                        stopRecording()
                        job?.cancel()
                    }
                    else -> { /* Do nothing */ }
                }
            } ?: run {
                onEvent(AssistEvent.Message.Output(app.getString(R.string.assist_error)))
            }
        }
    }

    protected fun setupRecorderQueue() {
        recorderQueue = mutableListOf()
        recorderJob = viewModelScope.launch {
            audioRecorder.audioBytes.collect {
                recorderQueue?.add(it) ?: sendVoiceData(it)
            }
        }
    }

    private fun sendVoiceData(data: ByteArray) {
        binaryHandlerId?.let {
            viewModelScope.launch {
                // Launch to prevent blocking the output flow if the network is slow
                serverManager.webSocketRepository(selectedServerId).sendVoiceData(it, data)
            }
        }
    }

    private suspend fun playAudio(path: String): Boolean {
        return UrlUtil.handle(serverManager.getServer(selectedServerId)?.connection?.getUrl(), path)?.let {
            audioUrlPlayer.playAudio(it.toString())
        } ?: false
    }

    protected fun stopRecording(sendRecorded: Boolean = true) {
        audioRecorder.stopRecording()
        recorderJob?.cancel()
        recorderJob = null
        if (binaryHandlerId != null) {
            viewModelScope.launch {
                if (sendRecorded) {
                    recorderQueue?.forEach {
                        sendVoiceData(it)
                    }
                    sendVoiceData(byteArrayOf()) // Empty message to indicate end of recording
                }
                recorderQueue = null
                binaryHandlerId = null
            }
        } else {
            recorderQueue = null
        }
        if (getInput() == AssistInputMode.VOICE_ACTIVE) {
            setInput(if (recorderProactive) AssistInputMode.BLOCKED else AssistInputMode.VOICE_INACTIVE)
        }
        recorderProactive = false
    }

    protected fun stopPlayback() = audioUrlPlayer.stop()
}
