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
import kotlinx.coroutines.Job
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

    var isAudioPlaying by mutableStateOf(false)
        private set

  

    private var pipelineJob: Job? = null
    private var activeUserMessage: AssistMessage? = null
    private var activeHaMessage: AssistMessage? = null
    private var isContinuationTurn = false

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
                    onMicrophoneInput(clearConversation = false)
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
            } else if (
                serverManager.integrationRepository(selectedServerId).getLastUsedPipelineId() != null
            ) {
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
                onMicrophoneInput(proactive = true, clearConversation = true)
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
            _conversation.value = emptyList()
            activeUserMessage = null
            activeHaMessage = null
            inputMode = if (pipeline.sttEngine != null) AssistInputMode.VOICE_INACTIVE else AssistInputMode.TEXT_ONLY
        } else {
            inputMode = AssistInputMode.BLOCKED
        }
    }

    fun onMicrophoneInput(
        proactive: Boolean = false,
        isContinuation: Boolean = false,
        clearConversation: Boolean = false,
    ) {
        Timber.d(
            "onMicrophoneInput called " +
                "(proactive=$proactive, isContinuation=$isContinuation, clearConversation=$clearConversation)",
        )
        if (!hasPermission) {
            Timber.w("onMicrophoneInput aborted: no permission")
            return
        }

        if (clearConversation) {
            _conversation.value = emptyList()
            activeUserMessage = null
            activeHaMessage = null
            pipelineJob?.cancel()
        }

        stopPlayback()
        setupRecorder(onError = {
            stopRecording()
            _conversation.value = _conversation.value + AssistMessage(
                app.getString(io.homeassistant.companion.android.common.R.string.assist_error),
                isInput = false,
                isError = true,
            )
            Timber.e(it, "Recorder setup failed")
        })
        if (!isContinuation) {
            inputMode = AssistInputMode.VOICE_ACTIVE
        }

        if (proactive) {
              if (isContinuation) {
                    // Just add user placeholder, pipeline already running
                    activeUserMessage = AssistMessage.placeholder(isInput = true)
                    _conversation.value = _conversation.value + activeUserMessage!!
                    activeHaMessage = AssistMessage.placeholder(isInput = false)
                } else {
                // New pipeline, add placeholders and start pipeline
                activeUserMessage = AssistMessage.placeholder(isInput = true)
                activeHaMessage = AssistMessage.placeholder(isInput = false)
                _conversation.value = _conversation.value + activeUserMessage!! + activeHaMessage!!
                runAssistPipeline(null)
            }
        }
    }

    private fun runAssistPipeline(text: String?, skipStopPlayback: Boolean = false) {
        val isVoice = text == null
        if (!skipStopPlayback) {
            stopPlayback()
        }

        pipelineJob = viewModelScope.launch {
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
                        if (event is AssistEvent.Message.Error) {
                            if (activeHaMessage != null) {
                                val haIndex = currentList.indexOf(activeHaMessage)
                                if (haIndex != -1) {
                                    currentList[haIndex] = activeHaMessage!!.copy(
                                        message = event.message.trim(),
                                        isError = true,
                                    )
                                    _conversation.value = currentList
                                }
                            }
                        } else if (event is AssistEvent.Message.Input) {
                            if (activeUserMessage != null) {
                                val userIndex = currentList.indexOf(activeUserMessage)
                                if (userIndex != -1) {
                                    currentList[userIndex] = activeUserMessage!!.copy(
                                        message = event.message.trim(),
                                        isError = false,
                                    )
                                    // Add assistant placeholder for the response if not already in list
                                    if (currentList.indexOf(activeHaMessage) == -1) {
                                        activeHaMessage = AssistMessage.placeholder(isInput = false)
                                        currentList.add(activeHaMessage!!)
                                    }
                                    _conversation.value = currentList
                                }
                            }
                        } else if (event is AssistEvent.Message.Output) {
                            if (activeHaMessage != null) {
                                val haIndex = currentList.indexOf(activeHaMessage)
                                if (haIndex != -1) {
                                    currentList[haIndex] = activeHaMessage!!.copy(
                                        message = event.message.trim(),
                                        isError = false,
                                    )
                                    _conversation.value = currentList
                                }
                            }
                        }
                    }

                    is AssistEvent.MessageChunk -> {
                        val currentList = _conversation.value.toMutableList()
                        if (activeHaMessage != null) {
                            val haIndex = currentList.indexOf(activeHaMessage)
                            if (haIndex != -1) {
                                activeHaMessage = activeHaMessage!!.copy(
                                    message = activeHaMessage!!.message + event.chunk,
                                )
                                currentList[haIndex] = activeHaMessage!!
                                _conversation.value = currentList
                            }
                        }
                    }

                    is AssistEvent.Dismiss -> {
                        isProcessing = false
                        shouldFinish = true
                    }

                    is AssistEvent.ContinueConversation -> {
                        onMicrophoneInput(proactive = true, isContinuation = true)
                        isContinuationTurn = true
                        runAssistPipeline(null, skipStopPlayback = true)
                    }

                    is AssistEvent.PipelineEnded -> {
                        isProcessing = false
                        if (!isContinuationTurn) {
                            activeUserMessage = null
                            activeHaMessage = null
                        }
                        isContinuationTurn = false
                    }

                    else -> {}
                }
            }
        }
    }
}
