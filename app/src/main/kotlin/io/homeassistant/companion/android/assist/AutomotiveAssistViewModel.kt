package io.homeassistant.companion.android.assist

import android.app.Application
import android.content.Intent
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.assist.ui.AssistMessage
import io.homeassistant.companion.android.assist.ui.AssistUiPipeline
import io.homeassistant.companion.android.common.assist.AssistAudioStrategy
import io.homeassistant.companion.android.common.assist.AssistEvent
import io.homeassistant.companion.android.common.assist.AssistViewModelBase
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineResponse
import io.homeassistant.companion.android.common.util.AudioUrlPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.time.Duration.Companion.seconds

/** A ViewModel for the automotive Assist UI. It provides a simplified state
 * compared to [AssistViewModel], focusing on voice-only interaction.
 */
@HiltViewModel(assistedFactory = AutomotiveAssistViewModel.Factory::class)
class AutomotiveAssistViewModel @AssistedInject constructor(
    serverManager: ServerManager,
    @Assisted initialAudioStrategy: AssistAudioStrategy,
    audioUrlPlayer: AudioUrlPlayer,
    application: Application,
) : AssistViewModelBase(serverManager, initialAudioStrategy, audioUrlPlayer, application) {

    @AssistedFactory
    interface Factory {
        fun create(audioStrategy: AssistAudioStrategy): AutomotiveAssistViewModel
    }

    private val startMessage =
        AssistMessage(application.getString(io.homeassistant.companion.android.common.R.string.assist_how_can_i_assist), isInput = false)
    private val _conversation = mutableStateListOf(startMessage)
    val conversation: List<AssistMessage> = _conversation

    var inputMode by mutableStateOf<AssistInputMode?>(null)
        private set

    var shouldFinish by mutableStateOf(false)
        private set

    private var inactivityTimerJob: Job? = null

    init {
        viewModelScope.launch {
            audioStrategy.wakeWordDetected.collect { detectedPhrase ->
                if (inputMode != AssistInputMode.VOICE_ACTIVE) {
                    onMicrophoneInput()
                }
            }
        }
    }

    fun onCreate(
        hasPermission: Boolean,
        serverId: Int?,
        pipelineId: String?,
        startListening: Boolean?,
    ) {
        viewModelScope.launch {
            this@AutomotiveAssistViewModel.hasPermission = hasPermission
            serverId?.let {
                selectedServerId = it
            }
            startListening?.let { recorderAutoStart = it }

            if (!serverManager.isRegistered()) {
                inputMode = AssistInputMode.BLOCKED
                _conversation.clear()
                _conversation.add(
                    AssistMessage(app.getString(io.homeassistant.companion.android.common.R.string.not_registered), isInput = false),
                )
                return@launch
            }

            if (pipelineId != null) {
                setPipeline(pipelineId)
            } else if (serverManager.integrationRepository(selectedServerId).getLastUsedPipelineId() != null) {
                setPipeline(serverManager.integrationRepository(selectedServerId).getLastUsedPipelineId())
            } else {
                inputMode = AssistInputMode.BLOCKED
                _conversation.clear()
                _conversation.add(
                    AssistMessage(app.getString(io.homeassistant.companion.android.common.R.string.assist_error), isInput = false),
                )
            }

            if (hasPermission && recorderAutoStart != false) {
                onMicrophoneInput(proactive = true)
            }
        }
    }

    private suspend fun setPipeline(id: String?) {
        val pipeline = try {
            serverManager.webSocketRepository(selectedServerId).getAssistPipeline(id)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get assist pipeline")
            null
        }

        if (pipeline != null) {
            _conversation.clear()
            _conversation.add(startMessage)
            inputMode = if (pipeline.sttEngine != null) AssistInputMode.VOICE_INACTIVE else AssistInputMode.TEXT_ONLY
            if (pipeline.sttEngine != null) {
                onMicrophoneInput(proactive = true)
            }
        } else {
            inputMode = AssistInputMode.BLOCKED
        }
    }

    fun onMicrophoneInput(proactive: Boolean? = false) {
        if (!hasPermission) return

        stopPlayback()
        setupRecorder(onError = {
            stopRecording()
            _conversation.add(
                AssistMessage(app.getString(io.homeassistant.companion.android.common.R.string.assist_error), isInput = false, isError = true),
            )
        })
        inputMode = AssistInputMode.VOICE_ACTIVE
        if (proactive == true) _conversation.add(AssistMessage.placeholder(isInput = true))
        if (proactive != true) runAssistPipeline(null)
    }

    private fun runAssistPipeline(text: String?) {
        val isVoice = text == null
        stopPlayback()

        val userMessage = text?.let { AssistMessage(it, isInput = true) } ?: AssistMessage.placeholder(isInput = true)
        _conversation.add(userMessage)
        val haMessage = AssistMessage.placeholder(isInput = false)
        if (!isVoice) _conversation.add(haMessage)
        var message = if (isVoice) userMessage else haMessage

        runAssistPipelineInternal(
            text = text,
            pipeline = serverManager.integrationRepository(selectedServerId).getLastUsedPipelineId()?.let {
                serverManager.webSocketRepository(selectedServerId).getAssistPipeline(it)
            },
            wakeWordPhrase = null,
        ) { event ->
            when (event) {
                is AssistEvent.Message -> {
                    _conversation.indexOf(message).takeIf { pos -> pos >= 0 }?.let { index ->
                        val isInput = event is AssistEvent.Message.Input
                        val isError = event is AssistEvent.Message.Error
                        _conversation[index] = message.copy(
                            message = event.message.trim(),
                            isInput = isInput,
                            isError = isError,
                        )
                        if (isInput) {
                            _conversation.add(haMessage)
                            message = haMessage
                        }
                    }
                }

                is AssistEvent.MessageChunk -> {
                    val lastMessage = _conversation.last()
                    if (lastMessage == haMessage) {
                        _conversation.removeAt(_conversation.lastIndex)
                        _conversation.add(lastMessage.copy(message = event.chunk))
                    } else {
                        _conversation[_conversation.lastIndex] =
                            lastMessage.copy(message = lastMessage.message + event.chunk)
                    }
                }

                is AssistEvent.Dismiss -> shouldFinish = true
                else -> {}
            }
        }
    }

    override fun onPause() {
        super.onPause()
        stopRecording()
        stopPlayback()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRecording()
        stopPlayback()
    }
}
