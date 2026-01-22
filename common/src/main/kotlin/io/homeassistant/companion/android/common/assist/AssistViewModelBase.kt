package io.homeassistant.companion.android.common.assist

import android.app.Application
import android.content.pm.PackageManager
import androidx.annotation.VisibleForTesting
import androidx.annotation.VisibleForTesting.Companion.PROTECTED
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.servers.UrlState
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
import io.homeassistant.companion.android.common.util.FailFast
import io.homeassistant.companion.android.common.util.PlaybackState
import io.homeassistant.companion.android.util.UrlUtil
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import timber.log.Timber

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

    /**
     * Parent job managing the audio recording pipeline. Contains both the producer
     * (collecting from [AudioRecorder.audioBytes]) and consumer (forwarding to server).
     * Joining this job ensures all buffered audio has been sent before cleanup.
     */
    private var recorderJob: Job? = null

    /**
     * Child job that collects audio bytes from [AudioRecorder.audioBytes] SharedFlow
     * and buffers them in a channel. Cancelled separately from [recorderJob] to stop
     * collecting while still allowing the consumer to drain buffered data.
     */
    private var producerJob: Job? = null

    /**
     * Signals when the server is ready to receive audio data. Completed with the
     * binary handler ID when [handleSttStart] is called. The consumer coroutine
     * awaits this before forwarding buffered audio to the server.
     */
    private var sttReady: CompletableDeferred<Int>? = null
    protected val hasMicrophone = app.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
    protected var hasPermission = false

    @VisibleForTesting
    var binaryHandlerId: Int? = null
    private var conversationId: String? = null
    private var continueConversation = AtomicBoolean(false)

    suspend fun isRegistered(): Boolean = serverManager.isRegistered()

    abstract fun getInput(): AssistInputMode?
    abstract fun setInput(inputMode: AssistInputMode)

    protected fun clearPipelineData() {
        binaryHandlerId = null
        conversationId = null
    }

    private var currentPlayAudioJob: Job? = null

    private var currentPathBeingPlayed: String? = null

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
            val flow = try {
                if (isVoice) {
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to start assist pipeline")
                null
            }

            flow?.collect { event ->
                when (event.type) {
                    AssistPipelineEventType.RUN_START -> handleRunStart(
                        event.data as? AssistPipelineRunStart,
                        isVoice,
                        onEvent,
                    )

                    AssistPipelineEventType.STT_START -> handleSttStart()
                    AssistPipelineEventType.STT_END -> handleSttEnd(event.data as? AssistPipelineSttEnd, onEvent)
                    AssistPipelineEventType.INTENT_PROGRESS -> handleIntentProgress(
                        event.data as? AssistPipelineIntentProgress,
                        onEvent,
                    )

                    AssistPipelineEventType.INTENT_END -> handleIntentEnd(
                        event.data as? AssistPipelineIntentEnd,
                        onEvent,
                    )

                    AssistPipelineEventType.TTS_END -> handleTtsEnd(
                        event.data as? AssistPipelineTtsEnd,
                        isVoice,
                        onEvent,
                    )

                    AssistPipelineEventType.RUN_END -> {
                        stopRecording()
                        job?.cancel()
                    }

                    AssistPipelineEventType.ERROR -> if (handleError(event.data as? AssistPipelineError, onEvent)) {
                        job?.cancel()
                    }

                    else -> {
                        /*No op*/
                    }
                }
            } ?: run {
                onEvent(AssistEvent.Message.Output(app.getString(R.string.assist_error)))
            }
        }
    }

    private fun handleRunStart(data: AssistPipelineRunStart?, isVoice: Boolean, onEvent: (AssistEvent) -> Unit) {
        if (!isVoice) return

        data?.ttsOutput?.let { ttsOutput ->
            val audioPath = ttsOutput.url
            if (audioPath.isNotBlank() && currentPathBeingPlayed != audioPath) {
                currentPathBeingPlayed = audioPath
                stopPlayback()
                currentPlayAudioJob = viewModelScope.launch {
                    playAudio(audioPath).collect { state ->
                        if (state == PlaybackState.STOP_PLAYING) {
                            notifyContinueConversationIfNeeded(onEvent)
                        }
                    }
                }
            }
        }

        binaryHandlerId = data?.runnerData?.get("stt_binary_handler_id") as? Int
    }

    private fun handleSttStart() {
        binaryHandlerId?.let { id ->
            sttReady?.complete(id)
        }
    }

    private fun handleSttEnd(data: AssistPipelineSttEnd?, onEvent: (AssistEvent) -> Unit) {
        stopRecording()
        data?.sttOutput?.get("text")?.let { text ->
            onEvent(AssistEvent.Message.Input(text as String))
        }
    }

    private fun handleIntentProgress(data: AssistPipelineIntentProgress?, onEvent: (AssistEvent) -> Unit) {
        data?.chatLogDelta?.content?.let { delta ->
            onEvent(AssistEvent.MessageChunk(delta))
        }
    }

    private fun handleIntentEnd(data: AssistPipelineIntentEnd?, onEvent: (AssistEvent) -> Unit) {
        val intentOutput = data?.intentOutput ?: return
        conversationId = intentOutput.conversationId
        continueConversation.set(intentOutput.continueConversation)
        intentOutput.response.speech?.plain?.get("speech")?.let { speech ->
            onEvent(AssistEvent.Message.Output(speech))
        }
    }

    /*
     * Handles TTS_END events for backward compatibility with servers that don't support
     * streaming TTS in RUN_START. If [currentPathBeingPlayed] is set, audio is already
     * playing from RUN_START and this handler is skipped.
     */
    private fun handleTtsEnd(data: AssistPipelineTtsEnd?, isVoice: Boolean, onEvent: (AssistEvent) -> Unit) {
        if (!isVoice || currentPathBeingPlayed != null) return

        currentPlayAudioJob = viewModelScope.launch {
            val audioPath = data?.ttsOutput?.url
            if (!audioPath.isNullOrBlank()) {
                playAudio(audioPath).first { state -> state == PlaybackState.STOP_PLAYING }
            }
            notifyContinueConversationIfNeeded(onEvent)
        }
    }

    /**
     * Return true if we need to cancel the job
     */
    private fun handleError(data: AssistPipelineError?, onEvent: (AssistEvent) -> Unit): Boolean {
        val errorMessage = data?.message ?: return false
        onEvent(AssistEvent.Message.Error(errorMessage))
        stopRecording()
        return true
    }

    /**
     * Sets up audio recording and buffering for voice input.
     *
     * Must be called before [runAssistPipelineInternal] for voice pipelines.
     * Audio data is buffered until the server is ready to receive, then all
     * buffered and subsequent audio is forwarded.
     *
     * Note: [AudioRecorder.audioBytes] is a SharedFlow which never completes, so we use
     * explicit channel management to allow graceful shutdown with buffer draining.
     */
    @VisibleForTesting(otherwise = PROTECTED)
    fun setupRecorder() {
        Timber.d("Setting up recorder")
        sttReady = CompletableDeferred()

        recorderJob = viewModelScope.launch {
            val audioChannel = Channel<ByteArray>(Channel.UNLIMITED)

            // Producer: collect from SharedFlow and buffer in channel
            producerJob = launch {
                audioRecorder.audioBytes.collect { data ->
                    audioChannel.send(data)
                }
            }.apply {
                invokeOnCompletion {
                    audioChannel.close()
                }
            }

            // Consumer: wait for STT to be ready, then forward all buffered and new data
            val handlerId = sttReady?.await() ?: run {
                FailFast.fail { "sttReady not set" }
                producerJob?.cancel()
                audioChannel.close()
                return@launch
            }

            for (data in audioChannel) {
                serverManager.webSocketRepository(selectedServerId).sendVoiceData(handlerId, data)
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun playAudio(path: String): Flow<PlaybackState> {
        return serverManager.connectionStateProvider(selectedServerId).urlFlow().flatMapLatest { urlState ->
            val baseUrl = if (urlState is UrlState.HasUrl) {
                urlState.url
            } else {
                null
            }
            UrlUtil.handle(baseUrl, path)?.let {
                audioUrlPlayer.playAudio(it.toString())
            } ?: emptyFlow()
        }
    }

    protected fun stopRecording(sendRecorded: Boolean = true) {
        stopAudioCapture()

        binaryHandlerId?.let { handlerId ->
            finalizeRecording(handlerId, sendRecorded)
        } ?: clearRecorderState()

        updateInputModeAfterRecording()
    }

    private fun stopAudioCapture() {
        audioRecorder.stopRecording()
        producerJob?.cancel()
        producerJob = null
    }

    private fun finalizeRecording(handlerId: Int, sendRecorded: Boolean) {
        viewModelScope.launch {
            if (sendRecorded) {
                recorderJob?.join()
                try {
                    serverManager.webSocketRepository(selectedServerId).sendVoiceData(
                        handlerId,
                        byteArrayOf(),
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Failed to finalize recording")
                }
            }
            clearRecorderState()
        }
    }

    private fun clearRecorderState() {
        recorderJob?.cancel()
        recorderJob = null
        sttReady = null
        binaryHandlerId = null
    }

    private fun updateInputModeAfterRecording() {
        if (getInput() == AssistInputMode.VOICE_ACTIVE) {
            setInput(if (recorderProactive) AssistInputMode.BLOCKED else AssistInputMode.VOICE_INACTIVE)
        }
        recorderProactive = false
    }

    protected fun stopPlayback() = currentPlayAudioJob?.cancel()

    /**
     * Checks if the conversation should continue and notifies the UI if so.
     * This is called after audio playback finishes to let the player complete before
     * recording a new entry from the user.
     */
    private fun notifyContinueConversationIfNeeded(onEvent: (AssistEvent) -> Unit) {
        if (continueConversation.getAndSet(false)) {
            onEvent(AssistEvent.ContinueConversation)
        }
    }
}
