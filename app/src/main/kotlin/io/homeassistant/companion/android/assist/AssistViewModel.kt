package io.homeassistant.companion.android.assist

import android.app.Application
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.assist.ui.AssistMessage
import io.homeassistant.companion.android.assist.ui.AssistUiPipeline
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.assist.AssistEvent
import io.homeassistant.companion.android.common.assist.AssistViewModelBase
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineResponse
import io.homeassistant.companion.android.common.util.AudioRecorder
import io.homeassistant.companion.android.common.util.AudioUrlPlayer
import javax.inject.Inject
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class AssistViewModel @Inject constructor(
    val serverManager: ServerManager,
    private val audioRecorder: AudioRecorder,
    audioUrlPlayer: AudioUrlPlayer,
    application: Application,
) : AssistViewModelBase(serverManager, audioRecorder, audioUrlPlayer, application) {

    private var filteredServerId: Int? = null
    private val allPipelines = mutableMapOf<Int, List<AssistPipelineResponse>>()
    private var selectedPipeline: AssistPipelineResponse? = null

    private var recorderAutoStart = true
    private var requestPermission: (() -> Unit)? = null
    private var requestSilently = true

    private val startMessage =
        AssistMessage(application.getString(commonR.string.assist_how_can_i_assist), isInput = false)
    private val _conversation = mutableStateListOf(startMessage)
    val conversation: List<AssistMessage> = _conversation

    private val _pipelines = mutableStateListOf<AssistUiPipeline>()
    val pipelines: List<AssistUiPipeline> = _pipelines

    var currentPipeline by mutableStateOf<AssistUiPipeline?>(null)
        private set

    var inputMode by mutableStateOf<AssistInputMode?>(null)
        private set

    var userCanManagePipelines by mutableStateOf(false)
        private set

    fun onCreate(hasPermission: Boolean, serverId: Int?, pipelineId: String?, startListening: Boolean?) {
        viewModelScope.launch {
            this@AssistViewModel.hasPermission = hasPermission
            serverId?.let {
                filteredServerId = serverId
                selectedServerId = serverId
            }
            startListening?.let { recorderAutoStart = it }

            if (!serverManager.isRegistered()) {
                inputMode = AssistInputMode.BLOCKED
                _conversation.clear()
                _conversation.add(
                    AssistMessage(app.getString(commonR.string.not_registered), isInput = false),
                )
                return@launch
            }

            if (
                pipelineId == PIPELINE_LAST_USED &&
                recorderAutoStart &&
                hasPermission &&
                hasMicrophone &&
                serverManager.getServer(selectedServerId) != null &&
                serverManager.integrationRepository(selectedServerId).getLastUsedPipelineSttSupport()
            ) {
                // Start microphone recording to prevent missing voice input while doing network checks
                onMicrophoneInput(proactive = true)
            }

            val supported = checkSupport()
            if (supported != true) stopRecording()
            if (supported == null) { // Couldn't get config
                inputMode = AssistInputMode.BLOCKED
                _conversation.clear()
                _conversation.add(
                    AssistMessage(app.getString(commonR.string.assist_connnect), isInput = false),
                )
            } else if (!supported) { // Core too old or doesn't include assist pipeline
                inputMode = AssistInputMode.BLOCKED
                _conversation.clear()
                _conversation.add(
                    AssistMessage(
                        app.getString(
                            commonR.string.no_assist_support,
                            "2023.5",
                            app.getString(commonR.string.no_assist_support_assist_pipeline),
                        ),
                        isInput = false,
                    ),
                )
            } else {
                setPipeline(
                    when {
                        pipelineId == PIPELINE_LAST_USED -> serverManager.integrationRepository(
                            selectedServerId,
                        ).getLastUsedPipelineId()
                        pipelineId == PIPELINE_PREFERRED -> null
                        pipelineId?.isNotBlank() == true -> pipelineId
                        else -> null
                    },
                )
            }

            if (serverManager.isRegistered()) {
                loadPipelines()
            }
            userCanManagePipelines = serverManager.getServer()?.user?.isAdmin == true
        }
    }

    /**
     * Update the state of the Assist dialog for a new 'assistant triggered' action
     * @param intent the updated intent
     * @param lockedMatches whether the locked state changed and contents should be cleared
     */
    fun onNewIntent(intent: Intent, lockedMatches: Boolean) {
        if (
            (intent.flags and Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT != 0) ||
            intent.action in
            listOf(Intent.ACTION_ASSIST, "android.intent.action.VOICE_ASSIST", Intent.ACTION_VOICE_COMMAND)
        ) {
            if (!lockedMatches && inputMode != AssistInputMode.BLOCKED) {
                _conversation.clear()
                _conversation.add(startMessage)
            }
            if (inputMode == AssistInputMode.VOICE_ACTIVE || inputMode == AssistInputMode.VOICE_INACTIVE) {
                onMicrophoneInput()
            }
        }
    }

    override fun getInput(): AssistInputMode? = inputMode

    override fun setInput(inputMode: AssistInputMode) {
        this.inputMode = inputMode
    }

    private suspend fun checkSupport(): Boolean? {
        if (!serverManager.isRegistered()) return false
        if (!serverManager.integrationRepository(
                selectedServerId,
            ).isHomeAssistantVersionAtLeast(2023, 5, 0)
        ) {
            return false
        }
        return serverManager.webSocketRepository(selectedServerId).getConfig()?.components?.contains("assist_pipeline")
    }

    private suspend fun loadPipelines() {
        val serverIds = filteredServerId?.let { listOf(it) } ?: serverManager.defaultServers.map { it.id }
        serverIds.forEach { serverId ->
            viewModelScope.launch {
                val server = serverManager.getServer(serverId)
                val serverPipelines = serverManager.webSocketRepository(serverId).getAssistPipelines()
                allPipelines[serverId] = serverPipelines?.pipelines ?: emptyList()
                _pipelines.addAll(
                    serverPipelines?.pipelines.orEmpty().map {
                        AssistUiPipeline(
                            serverId = serverId,
                            serverName = server?.friendlyName ?: "",
                            id = it.id,
                            name = it.name,
                        )
                    },
                )
            }
        }
    }

    fun changePipeline(serverId: Int, id: String) = viewModelScope.launch {
        if (serverId == selectedServerId && id == selectedPipeline?.id) return@launch

        stopRecording(sendRecorded = false)
        stopPlayback()

        selectedServerId = serverId
        setPipeline(id)
    }

    private suspend fun setPipeline(id: String?) {
        selectedPipeline =
            allPipelines[selectedServerId]?.firstOrNull { it.id == id }
                ?: serverManager.webSocketRepository(selectedServerId).getAssistPipeline(id)
        selectedPipeline?.let {
            currentPipeline = AssistUiPipeline(
                serverId = selectedServerId,
                serverName = serverManager.getServer(selectedServerId)?.friendlyName ?: "",
                id = it.id,
                name = it.name,
            )
            serverManager.integrationRepository(selectedServerId).setLastUsedPipeline(it.id, it.sttEngine != null)

            _conversation.clear()
            _conversation.add(startMessage)
            clearPipelineData()
            if (hasMicrophone && it.sttEngine != null) {
                if (recorderAutoStart && (hasPermission || requestSilently)) {
                    inputMode = AssistInputMode.VOICE_INACTIVE
                    onMicrophoneInput(proactive = null)
                } else { // already requested permission once and was denied
                    inputMode = AssistInputMode.TEXT
                }
            } else {
                inputMode = AssistInputMode.TEXT_ONLY
            }
        } ?: run {
            if (!id.isNullOrBlank()) {
                setPipeline(null) // Try falling back to default pipeline
            } else {
                Timber.w("Server $selectedServerId does not have any pipelines")
                inputMode = AssistInputMode.BLOCKED
                _conversation.clear()
                _conversation.add(
                    AssistMessage(app.getString(commonR.string.assist_error), isInput = false),
                )
            }
        }
    }

    fun onChangeInput() {
        when (inputMode) {
            null, AssistInputMode.BLOCKED, AssistInputMode.TEXT_ONLY -> { /* Do nothing */ }
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
                stopRecording(sendRecorded = false)
                inputMode = AssistInputMode.TEXT
            }
        }
    }

    fun onTextInput(input: String) = runAssistPipeline(input)

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
            selectedPipeline,
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

    fun onPermissionResult(granted: Boolean) {
        hasPermission = granted
        val proactive = currentPipeline == null
        if (granted) {
            inputMode = AssistInputMode.VOICE_INACTIVE
            onMicrophoneInput(proactive = proactive)
        } else if (requestSilently && !proactive) { // Don't notify the user if they haven't explicitly requested
            inputMode = AssistInputMode.TEXT
        } else if (!requestSilently) {
            _conversation.add(AssistMessage(app.getString(commonR.string.assist_permission), isInput = false))
        }
        if (!proactive) requestSilently = false
    }

    fun onPause() {
        requestPermission = null
        stopRecording()
    }

    fun onDestroy() {
        requestPermission = null
        stopRecording()
        stopPlayback()
    }
}
