package io.homeassistant.companion.android.conversation

import android.app.Application
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.assist.AssistEvent
import io.homeassistant.companion.android.common.assist.AssistViewModelBase
import io.homeassistant.companion.android.common.data.prefs.WearPrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineResponse
import io.homeassistant.companion.android.common.util.AudioRecorder
import io.homeassistant.companion.android.common.util.AudioUrlPlayer
import io.homeassistant.companion.android.conversation.views.AssistMessage
import javax.inject.Inject
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class ConversationViewModel @Inject constructor(
    private val serverManager: ServerManager,
    private val audioRecorder: AudioRecorder,
    audioUrlPlayer: AudioUrlPlayer,
    private val wearPrefsRepository: WearPrefsRepository,
    application: Application,
) : AssistViewModelBase(serverManager, audioRecorder, audioUrlPlayer, application) {

    private var useAssistPipeline = false
    private var useAssistPipelineStt = false

    var inputMode by mutableStateOf(AssistInputMode.BLOCKED)
        private set

    var isHapticEnabled by mutableStateOf(false)
        private set

    var currentPipeline by mutableStateOf<AssistPipelineResponse?>(null)
        private set

    private var requestPermission: (() -> Unit)? = null
    private var requestSilently = true

    private val _pipelines = mutableStateListOf<AssistPipelineResponse>()
    val pipelines: List<AssistPipelineResponse> = _pipelines

    private val startMessage =
        AssistMessage(application.getString(commonR.string.assist_how_can_i_assist), isInput = false)
    private val _conversation = mutableStateListOf(startMessage)
    val conversation: List<AssistMessage> = _conversation

    /** @return `true` if the voice input intent should be fired */
    suspend fun onCreate(hasPermission: Boolean): Boolean {
        this.hasPermission = hasPermission

        if (!serverManager.isRegistered()) {
            _conversation.clear()
            _conversation.add(
                AssistMessage(app.getString(commonR.string.not_registered), isInput = false),
            )
            return false
        }

        if (hasPermission && hasMicrophone && serverManager.integrationRepository().getLastUsedPipelineSttSupport()) {
            // Start microphone recording to prevent missing voice input while doing network checks
            onMicrophoneInput(proactive = true)
        }

        val supported = checkAssistSupport()
        if (supported != true) stopRecording()
        if (supported == null) { // Couldn't get config
            _conversation.clear()
            _conversation.add(
                AssistMessage(app.getString(commonR.string.assist_connnect), isInput = false),
            )
        } else if (!supported) { // Core too old or missing component
            val usingPipelines = serverManager.getServer()?.version?.isAtLeast(2023, 5) == true
            _conversation.clear()
            _conversation.add(
                AssistMessage(
                    if (usingPipelines) {
                        app.getString(
                            commonR.string.no_assist_support,
                            "2023.5",
                            app.getString(commonR.string.no_assist_support_assist_pipeline),
                        )
                    } else {
                        app.getString(
                            commonR.string.no_assist_support,
                            "2023.1",
                            app.getString(commonR.string.no_assist_support_conversation),
                        )
                    },
                    isInput = false,
                ),
            )
        } else {
            if (serverManager.getServer()?.version?.isAtLeast(2023, 5) == true) {
                viewModelScope.launch {
                    loadPipelines()
                }
            }

            return setPipeline(
                if (useAssistPipeline) {
                    serverManager.integrationRepository().getLastUsedPipelineId()
                } else {
                    null
                },
            )
        }

        return false
    }

    /** @return `true` if the voice input intent should be fired */
    fun onNewIntent(intent: Intent): Boolean {
        if (
            (
                (intent.flags and Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT != 0) ||
                    intent.action in listOf(Intent.ACTION_ASSIST, "android.intent.action.VOICE_ASSIST")
                ) &&
            inputMode != AssistInputMode.BLOCKED
        ) {
            if (inputMode == AssistInputMode.TEXT) {
                return true
            } else {
                onMicrophoneInput()
            }
        }
        return false
    }

    override fun getInput(): AssistInputMode = inputMode

    override fun setInput(inputMode: AssistInputMode) {
        this.inputMode = inputMode
    }

    private suspend fun checkAssistSupport(): Boolean? {
        isHapticEnabled = wearPrefsRepository.getWearHapticFeedback()
        if (!serverManager.isRegistered()) return false

        val config = serverManager.webSocketRepository().getConfig()
        val onConversationVersion = serverManager.integrationRepository().isHomeAssistantVersionAtLeast(2023, 1, 0)
        val onPipelineVersion = serverManager.integrationRepository().isHomeAssistantVersionAtLeast(2023, 5, 0)

        useAssistPipeline = onPipelineVersion
        return if ((onConversationVersion && !onPipelineVersion && config == null) ||
            (onPipelineVersion && config == null)
        ) {
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

    fun usePipelineStt(): Boolean = useAssistPipelineStt

    fun changePipeline(id: String) = viewModelScope.launch {
        if (id == currentPipeline?.id) return@launch

        stopRecording(sendRecorded = false)
        stopPlayback()

        setPipeline(id)
    }

    private suspend fun setPipeline(id: String?): Boolean {
        val pipeline = if (useAssistPipeline) {
            _pipelines.firstOrNull { it.id == id } ?: serverManager.webSocketRepository().getAssistPipeline(id)
        } else {
            null
        }

        useAssistPipelineStt = false
        if (pipeline != null || !useAssistPipeline) {
            currentPipeline = pipeline
            currentPipeline?.let {
                serverManager.integrationRepository().setLastUsedPipeline(it.id, pipeline?.sttEngine != null)
            }

            _conversation.clear()
            _conversation.add(startMessage)
            clearPipelineData()
            if (pipeline != null && hasMicrophone && pipeline.sttEngine != null) {
                if (hasPermission || requestSilently) {
                    inputMode = AssistInputMode.VOICE_INACTIVE
                    useAssistPipelineStt = true
                    onMicrophoneInput(proactive = null)
                } else {
                    inputMode = AssistInputMode.TEXT
                }
            } else {
                inputMode = AssistInputMode.TEXT
            }
        } else {
            inputMode = AssistInputMode.BLOCKED
            _conversation.clear()
            _conversation.add(
                AssistMessage(app.getString(commonR.string.assist_error), isInput = false),
            )
        }

        return inputMode == AssistInputMode.TEXT
    }

    fun updateSpeechResult(commonResult: String) = runAssistPipeline(commonResult)

    /**
     * Start/stop microphone input for Assist, depending on the current state.
     * @param proactive true if proactive, null if not important, false if not
     */
    fun onMicrophoneInput(proactive: Boolean? = false) {
        if (!hasPermission) {
            requestPermission?.let { it() }
            return
        }

        if (inputMode == AssistInputMode.VOICE_ACTIVE && proactive == false) {
            stopRecording()
            return
        }

        stopPlayback()

        val recording = try {
            recorderProactive || audioRecorder.startRecording()
        } catch (e: Exception) {
            Timber.e(e, "Exception while starting recording")
            false
        }

        if (recording) {
            if (!recorderProactive) setupRecorderQueue()
            inputMode = AssistInputMode.VOICE_ACTIVE
            if (proactive == true) _conversation.add(AssistMessage("…", isInput = true))
            if (proactive != true) runAssistPipeline(null)
        } else {
            _conversation.add(
                AssistMessage(app.getString(commonR.string.assist_error), isInput = false, isError = true),
            )
        }
        recorderProactive = recording && proactive == true
    }

    private fun runAssistPipeline(text: String?) {
        val isVoice = text == null
        stopPlayback()

        val userMessage = AssistMessage(text ?: "…", isInput = true)
        _conversation.add(userMessage)
        val haMessage = AssistMessage("…", isInput = false)
        if (!isVoice) _conversation.add(haMessage)
        var message = if (isVoice) userMessage else haMessage

        runAssistPipelineInternal(
            text,
            currentPipeline,
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
                        if (isError && inputMode == AssistInputMode.VOICE_ACTIVE) {
                            stopRecording()
                        }
                    }
                }
                is AssistEvent.MessageChunk -> {
                    val lastMessage = _conversation.last()
                    if (lastMessage == haMessage) {
                        // Remove '...' message and add the chunk received
                        _conversation.removeAt(_conversation.lastIndex)
                        _conversation.add(lastMessage.copy(message = event.chunk))
                    } else {
                        // Replace last message with the updated message with the new chunk append
                        _conversation[_conversation.lastIndex] =
                            lastMessage.copy(message = lastMessage.message + event.chunk)
                    }
                }
                is AssistEvent.ContinueConversation -> onMicrophoneInput()
            }
        }
    }

    fun setPermissionInfo(hasPermission: Boolean, callback: () -> Unit) {
        this.hasPermission = hasPermission
        requestPermission = callback
    }

    fun onPermissionResult(granted: Boolean, voiceInputIntent: (() -> Unit)) {
        hasPermission = granted
        useAssistPipelineStt = currentPipeline?.sttEngine != null && granted
        val proactive = currentPipeline == null
        if (granted) {
            inputMode = AssistInputMode.VOICE_INACTIVE
            onMicrophoneInput(proactive = proactive)
        } else if (requestSilently && !proactive) { // Don't notify the user if they haven't explicitly requested
            inputMode = AssistInputMode.TEXT
            voiceInputIntent()
        }
        if (!proactive) requestSilently = false
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
}
