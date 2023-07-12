package io.homeassistant.companion.android.assist

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.assist.ui.AssistMessage
import io.homeassistant.companion.android.assist.ui.AssistUiPipeline
import io.homeassistant.companion.android.common.assist.AssistViewModelBase
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineResponse
import io.homeassistant.companion.android.common.util.AudioRecorder
import io.homeassistant.companion.android.common.util.AudioUrlPlayer
import kotlinx.coroutines.launch
import javax.inject.Inject
import io.homeassistant.companion.android.common.R as commonR

@HiltViewModel
class AssistViewModel @Inject constructor(
    val serverManager: ServerManager,
    private val audioRecorder: AudioRecorder,
    audioUrlPlayer: AudioUrlPlayer,
    application: Application
) : AssistViewModelBase(serverManager, audioRecorder, audioUrlPlayer, application) {

    companion object {
        const val TAG = "AssistViewModel"
    }

    private var filteredServerId: Int? = null
    private val allPipelines = mutableMapOf<Int, List<AssistPipelineResponse>>()
    private var selectedPipeline: AssistPipelineResponse? = null

    private var recorderAutoStart = true
    private var requestPermission: (() -> Unit)? = null
    private var requestSilently = true

    private val startMessage = AssistMessage(application.getString(commonR.string.assist_how_can_i_assist), isInput = false)
    private val _conversation = mutableStateListOf(startMessage)
    val conversation: List<AssistMessage> = _conversation

    private val _pipelines = mutableStateListOf<AssistUiPipeline>()
    val pipelines: List<AssistUiPipeline> = _pipelines

    var currentPipeline by mutableStateOf<AssistUiPipeline?>(null)
        private set

    var inputMode by mutableStateOf<AssistInputMode?>(null)
        private set

    fun onCreate(serverId: Int?, pipelineId: String?, startListening: Boolean?) {
        viewModelScope.launch {
            serverId?.let {
                filteredServerId = serverId
                selectedServerId = serverId
            }
            startListening?.let { recorderAutoStart = it }

            val supported = checkSupport()
            if (!serverManager.isRegistered()) {
                inputMode = AssistInputMode.BLOCKED
                _conversation.clear()
                _conversation.add(
                    AssistMessage(app.getString(commonR.string.not_registered), isInput = false)
                )
            } else if (supported == null) { // Couldn't get config
                inputMode = AssistInputMode.BLOCKED
                _conversation.clear()
                _conversation.add(
                    AssistMessage(app.getString(commonR.string.assist_connnect), isInput = false)
                )
            } else if (!supported) { // Core too old or doesn't include assist pipeline
                inputMode = AssistInputMode.BLOCKED
                _conversation.clear()
                _conversation.add(
                    AssistMessage(
                        app.getString(commonR.string.no_assist_support, "2023.5", app.getString(commonR.string.no_assist_support_assist_pipeline)),
                        isInput = false
                    )
                )
            } else {
                setPipeline(pipelineId?.ifBlank { null })
            }

            if (serverManager.isRegistered()) {
                viewModelScope.launch {
                    loadPipelines()
                }
            }
        }
    }

    override fun getInput(): AssistInputMode? = inputMode

    override fun setInput(inputMode: AssistInputMode) {
        this.inputMode = inputMode
    }

    private suspend fun checkSupport(): Boolean? {
        if (!serverManager.isRegistered()) return false
        if (!serverManager.integrationRepository(selectedServerId).isHomeAssistantVersionAtLeast(2023, 5, 0)) return false
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
                            name = it.name
                        )
                    }
                )
            }
        }
    }

    fun userCanManagePipelines(): Boolean = serverManager.getServer()?.user?.isAdmin == true

    fun changePipeline(serverId: Int, id: String) = viewModelScope.launch {
        if (serverId == selectedServerId && id == selectedPipeline?.id) return@launch

        stopRecording()
        stopPlayback()

        selectedServerId = serverId
        setPipeline(id)
    }

    private suspend fun setPipeline(id: String?) {
        selectedPipeline =
            allPipelines[selectedServerId]?.firstOrNull { it.id == id } ?: serverManager.webSocketRepository(selectedServerId).getAssistPipeline(id)
        selectedPipeline?.let {
            currentPipeline = AssistUiPipeline(
                serverId = selectedServerId,
                serverName = serverManager.getServer(selectedServerId)?.friendlyName ?: "",
                id = it.id,
                name = it.name
            )

            _conversation.clear()
            _conversation.add(startMessage)
            clearPipelineData()
            if (hasMicrophone && it.sttEngine != null) {
                if (recorderAutoStart && (hasPermission || requestSilently)) {
                    inputMode = AssistInputMode.VOICE_INACTIVE
                    onMicrophoneInput()
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
                Log.w(TAG, "Server $selectedServerId does not have any pipelines")
                inputMode = AssistInputMode.BLOCKED
                _conversation.clear()
                _conversation.add(
                    AssistMessage(app.getString(commonR.string.assist_error), isInput = false)
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
            setupRecorderQueue()
            inputMode = AssistInputMode.VOICE_ACTIVE
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

        runAssistPipelineInternal(
            text,
            selectedPipeline
        ) { newMessage, isInput, isError ->
            _conversation.indexOf(message).takeIf { pos -> pos >= 0 }?.let { index ->
                _conversation[index] = message.copy(
                    message = newMessage,
                    isInput = isInput ?: message.isInput,
                    isError = isError
                )
                if (isInput == true) {
                    _conversation.add(haMessage)
                    message = haMessage
                }
            }
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

    fun onPause() {
        requestPermission = null
        stopRecording()
        stopPlayback()
    }
}
