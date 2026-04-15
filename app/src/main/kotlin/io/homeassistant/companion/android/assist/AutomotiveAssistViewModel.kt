package io.homeassistant.companion.android.assist

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import io.homeassistant.companion.android.assist.ui.AssistMessage
import io.homeassistant.companion.android.common.assist.AssistAudioStrategy
import io.homeassistant.companion.android.common.assist.AssistEvent
import io.homeassistant.companion.android.common.assist.AssistViewModelBase
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.AudioUrlPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/** A ViewModel for the automotive Assist UI. It provides a simplified state
 * compared to [AssistViewModel], focusing on voice-only interaction.
 */
class AutomotiveAssistViewModel @AssistedInject constructor(
    @Assisted override val serverManager: ServerManager,
    @Assisted override val audioStrategy: AssistAudioStrategy,
    @Assisted private val audioUrlPlayer: AudioUrlPlayer,
    @Assisted private val application: Application,
) : AssistViewModelBase(serverManager, audioStrategy, audioUrlPlayer, application) {

    val isAudioPlaying: Boolean get() = isPlayingAudio

    var isProcessing by mutableStateOf(false)
        private set

    @AssistedFactory
    interface Factory {
        fun create(
            serverManager: ServerManager,
            audioStrategy: AssistAudioStrategy,
            audioUrlPlayer: AudioUrlPlayer,
            application: Application,
        ): AutomotiveAssistViewModel
    }

    private val _conversation = MutableStateFlow<List<AssistMessage>>(emptyList())
    val conversation: StateFlow<List<AssistMessage>> = _conversation.asStateFlow()

    private val startMessage =
        AssistMessage(
            app.getString(io.homeassistant.companion.android.common.R.string.assist_how_can_i_assist),
            isInput = false,
        )

    var inputMode by mutableStateOf<AssistInputMode?>(null)
        private set

    var shouldFinish by mutableStateOf(false)
        private set

    var recorderAutoStart by mutableStateOf(false)
        private set

    override fun getInput(): AssistInputMode? = inputMode

    override fun setInput(inputMode: AssistInputMode) {
        this.inputMode = inputMode
    }

    init {
        viewModelScope.launch {
            audioStrategy.wakeWordDetected.collect { detectedPhrase ->
                if (inputMode != AssistInputMode.VOICE_ACTIVE) {
                    onMicrophoneInput(proactive = false)
                }
            }
        }
    }

    fun onCreate(hasPermission: Boolean, serverId: Int?, pipelineId: String?, startListening: Boolean?) {
        viewModelScope.launch {
            this@AutomotiveAssistViewModel.hasPermission = hasPermission
            serverId?.let {
                selectedServerId = it
            }
            startListening?.let { recorderAutoStart = it }

            if (!serverManager.isRegistered()) {
                inputMode = AssistInputMode.BLOCKED
                _conversation.value = listOf(
                    AssistMessage(
                        app.getString(io.homeassistant.companion.android.common.R.string.not_registered),
                        isInput = false,
                    ),
                )
                return@launch
            }

            if (pipelineId != null) {
                setPipeline(pipelineId)
            } else if (serverManager.integrationRepository(selectedServerId).getLastUsedPipelineId() != null) {
                setPipeline(serverManager.integrationRepository(selectedServerId).getLastUsedPipelineId())
            } else {
                inputMode = AssistInputMode.BLOCKED
                _conversation.value = listOf(
                    AssistMessage(
                        app.getString(io.homeassistant.companion.android.common.R.string.assist_error),
                        isInput = false,
                    ),
                )
            }

            if (hasPermission && recorderAutoStart) {
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
            _conversation.value = listOf(startMessage)
            inputMode = if (pipeline.sttEngine != null) AssistInputMode.VOICE_INACTIVE else AssistInputMode.TEXT_ONLY
            if (pipeline.sttEngine != null) {
                onMicrophoneInput(proactive = true)
            }
        } else {
            inputMode = AssistInputMode.BLOCKED
        }
    }

    fun onMicrophoneInput(proactive: Boolean = false) {
        Timber.d("onMicrophoneInput called (proactive=$proactive, hasPermission=$hasPermission)")
        if (!hasPermission) {
            Timber.w("onMicrophoneInput aborted: no permission")
            return
        }

        stopPlayback()
        setupRecorder(onError = {
            stopRecording()
            _conversation.value = _conversation.value + AssistMessage(
                app.getString(io.homeassistant.companion.android.common.R.string.assist_error),
                isInput = false,
                isError = true,
            )
        })
        inputMode = AssistInputMode.VOICE_ACTIVE
        if (proactive) {
            _conversation.value = _conversation.value + AssistMessage.placeholder(isInput = true)
            runAssistPipeline(null, isProactive = true)
        } else {
            runAssistPipeline(null)
        }
    }

    private fun runAssistPipeline(text: String?, isProactive: Boolean = false) {
        val isVoice = text == null
        stopPlayback()

        val userMessage = text?.let { AssistMessage(it, isInput = true) } ?: AssistMessage.placeholder(isInput = true)
        val haMessage = AssistMessage.placeholder(isInput = false)

        viewModelScope.launch {
            val currentList = _conversation.value.toMutableList()
            if (!isProactive || text != null) {
                currentList.add(userMessage)
                if (!isVoice) currentList.add(haMessage)
            }
            _conversation.value = currentList

            var message = if (isVoice) userMessage else haMessage

            val pipeline = try {
                val lastPipelineId = serverManager.integrationRepository(selectedServerId).getLastUsedPipelineId()
                lastPipelineId?.let {
                    serverManager.webSocketRepository(selectedServerId).getAssistPipeline(it)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to get assist pipeline")
                null
            }

            isProcessing = true
            runAssistPipelineInternal(
                text = text,
                pipeline = pipeline,
                wakeWordPhrase = null,
            ) { event ->
                when (event) {
                    is AssistEvent.Message -> {
                        val currentList = _conversation.value.toMutableList()
                        val index = currentList.indexOf(message)
                        if (index != -1) {
                            val isInput = event is AssistEvent.Message.Input
                            val isError = event is AssistEvent.Message.Error
                            currentList[index] = message.copy(
                                message = event.message.trim(),
                                isInput = isInput,
                                isError = isError,
                            )
                            if (isInput) {
                                currentList.add(haMessage)
                                message = haMessage
                            }
                            _conversation.value = currentList
                        }
                    }

                    is AssistEvent.MessageChunk -> {
                        val currentList = _conversation.value.toMutableList()
                        val lastIndex = currentList.lastIndex
                        if (lastIndex >= 0 && currentList[lastIndex] == haMessage) {
                            currentList[lastIndex] = haMessage.copy(message = haMessage.message + event.chunk)
                            _conversation.value = currentList
                        } else if (lastIndex >= 0) {
                            val lastMsg = currentList[lastIndex]
                            currentList[lastIndex] = lastMsg.copy(message = lastMsg.message + event.chunk)
                            _conversation.value = currentList
                        }
                    }

                    is AssistEvent.Dismiss -> {
                        isProcessing = false
                        shouldFinish = true
                    }

                    is AssistEvent.ContinueConversation -> onMicrophoneInput(proactive = true)

                    is AssistEvent.PipelineEnded -> {
                        isProcessing = false
                        val currentList = _conversation.value.toMutableList()
                        if (currentList.isNotEmpty() &&
                            currentList.last().isPlaceholder &&
                            !currentList.last().isInput
                        ) {
                            currentList.removeAt(currentList.lastIndex)
                            _conversation.value = currentList
                        }
                    }

                    else -> {}
                }
            }
        }
    }
}
