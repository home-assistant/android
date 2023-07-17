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
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineRunStart
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineSttEnd
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineTtsEnd
import io.homeassistant.companion.android.common.util.AudioRecorder
import io.homeassistant.companion.android.common.util.AudioUrlPlayer
import io.homeassistant.companion.android.util.UrlUtil
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

abstract class AssistViewModelBase(
    private val serverManager: ServerManager,
    private val audioRecorder: AudioRecorder,
    private val audioUrlPlayer: AudioUrlPlayer,
    application: Application
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
        BLOCKED
    }

    protected val app = application

    protected var selectedServerId = ServerManager.SERVER_ID_ACTIVE

    private var recorderJob: Job? = null
    private var recorderQueue: MutableList<ByteArray>? = null
    protected val hasMicrophone = app.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
    protected var hasPermission = false

    private var binaryHandlerId: Int? = null
    private var conversationId: String? = null

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
     * @param onMessage callback for messages that should be posted for this pipeline run, with 3
     * arguments: the message, whether the message is input/output/undetermined, whether the message
     * is an error message
     */
    protected fun runAssistPipelineInternal(
        text: String?,
        pipeline: AssistPipelineResponse?,
        onMessage: (String, Boolean?, Boolean) -> Unit
    ) {
        val isVoice = text == null
        var job: Job? = null
        job = viewModelScope.launch {
            val flow = if (isVoice) {
                serverManager.webSocketRepository(selectedServerId).runAssistPipelineForVoice(
                    sampleRate = AudioRecorder.SAMPLE_RATE,
                    outputTts = pipeline?.ttsEngine?.isNotBlank() == true,
                    pipelineId = pipeline?.id,
                    conversationId = conversationId
                )
            } else {
                serverManager.integrationRepository(selectedServerId).getAssistResponse(
                    text = text!!,
                    pipelineId = pipeline?.id,
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
                            onMessage(response["text"] as String, true, false)
                        }
                    }
                    AssistPipelineEventType.INTENT_END -> {
                        val data = (it.data as? AssistPipelineIntentEnd)?.intentOutput ?: return@collect
                        conversationId = data.conversationId
                        data.response.speech.plain["speech"]?.let { response ->
                            onMessage(response, false, false)
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
                        onMessage(errorMessage, null, true)
                        stopRecording()
                        job?.cancel()
                    }
                    else -> { /* Do nothing */ }
                }
            } ?: run {
                onMessage(app.getString(R.string.assist_error), null, true)
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
                serverManager.webSocketRepository().sendVoiceData(it, data)
            }
        }
    }

    private fun playAudio(path: String) {
        UrlUtil.handle(serverManager.getServer(selectedServerId)?.connection?.getUrl(), path)?.let {
            viewModelScope.launch {
                audioUrlPlayer.playAudio(it.toString())
            }
        }
    }

    protected fun stopRecording() {
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
        if (getInput() == AssistInputMode.VOICE_ACTIVE) {
            setInput(AssistInputMode.VOICE_INACTIVE)
        }
    }

    protected fun stopPlayback() = audioUrlPlayer.stop()
}
