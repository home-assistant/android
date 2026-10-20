package io.homeassistant.companion.android.developer.webrtc

import android.content.Context
import android.media.AudioManager
import androidx.annotation.VisibleForTesting
import androidx.core.content.getSystemService
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.CAMERA_DOMAIN
import io.homeassistant.companion.android.common.data.integration.display.EntitiesForDisplayManager
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplay
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayState
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.impl.entities.CameraStreamType
import io.homeassistant.companion.android.webrtc.core.ConnectionState
import io.homeassistant.companion.android.webrtc.core.MediaOptions
import io.homeassistant.companion.android.webrtc.core.MicState
import io.homeassistant.companion.android.webrtc.core.RtcStatsSnapshot
import io.homeassistant.companion.android.webrtc.core.WebRtcSession
import io.homeassistant.companion.android.webrtc.core.WebRtcSessionFactory
import io.homeassistant.companion.android.webrtc.signaling.HaSignalingClient
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

private val STATE_SHARING_TIMEOUT = 5.seconds

/**
 * Stream types the selected camera reported, used to fail fast before starting a session instead
 * of only discovering an unsupported camera once the offer is rejected.
 */
sealed interface StreamTypesState {
    /** No camera selected, nothing probed yet. */
    data object None : StreamTypesState

    /** The probe is running. */
    data object Loading : StreamTypesState

    /** The camera reported these frontend stream types (see [CameraStreamType]). */
    data class Loaded(val types: Set<CameraStreamType>) : StreamTypesState

    /** The probe failed, so the supported types are unknown. */
    data object Unavailable : StreamTypesState

    /** WebRTC is only refused when the camera reported its types and WebRTC was not among them. */
    val allowsWebRtc: Boolean get() = this !is Loaded || CameraStreamType.WebRtc in types
}

/**
 * UI state of the developer WebRTC debug screen: the servers to pick from, the camera entities of
 * the selected server, and the current selection.
 */
data class WebRtcDebugState(
    val serversDropdownItems: List<HADropdownItem<Int>> = emptyList(),
    val selectedServerId: Int = 0,
    val entityDisplayState: EntityDisplayState<EntityDisplay> = EntityDisplayState.Loading,
    val selectedEntityId: String? = null,
    val mediaOptions: MediaOptions = MediaOptions(),
    val streamTypes: StreamTypesState = StreamTypesState.None,
) {
    val showServerSelector: Boolean get() = serversDropdownItems.size > 1

    /**
     * A session can start once a camera is selected, a stream is requested, and the camera is not
     * known to lack WebRTC support.
     */
    val canStart: Boolean
        get() = selectedEntityId != null && mediaOptions.hasAnyStream && streamTypes.allowsWebRtc
}

/** Owns the WebRTC session of the developer debug screen. */
@HiltViewModel
class WebRtcDebugViewModel @VisibleForTesting internal constructor(
    private val serverManager: ServerManager,
    private val entitiesForDisplayManager: EntitiesForDisplayManager,
    private val webRtcSessionFactory: WebRtcSessionFactory,
    private val audioManager: AudioManager?,
) : ViewModel() {

    @Inject
    constructor(
        serverManager: ServerManager,
        entitiesForDisplayManager: EntitiesForDisplayManager,
        webRtcSessionFactory: WebRtcSessionFactory,
        @ApplicationContext context: Context,
    ) : this(
        serverManager,
        entitiesForDisplayManager,
        webRtcSessionFactory,
        context.getSystemService<AudioManager>(),
    )

    /** The audio mode to restore when a talk-back session ends, captured before switching it. */
    private var previousAudioMode: Int? = null

    private val _state = MutableStateFlow(WebRtcDebugState())
    val state: StateFlow<WebRtcDebugState> = _state.asStateFlow()

    private val _session = MutableStateFlow<WebRtcSession?>(null)
    val session: StateFlow<WebRtcSession?> = _session.asStateFlow()

    private var loadEntitiesJob: Job? = null
    private var loadStreamTypesJob: Job? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    val connectionState: StateFlow<ConnectionState> = _session
        .flatMapLatest { session -> session?.state ?: flowOf(ConnectionState.Idle) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_SHARING_TIMEOUT.inWholeMilliseconds),
            initialValue = ConnectionState.Idle,
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val micState: StateFlow<MicState> = _session
        .flatMapLatest { session -> session?.audioOut?.state ?: flowOf(MicState.Off) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_SHARING_TIMEOUT.inWholeMilliseconds),
            initialValue = MicState.Off,
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val stats: StateFlow<RtcStatsSnapshot> = _session
        .flatMapLatest { session -> session?.observeStats() ?: flowOf(RtcStatsSnapshot.EMPTY) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_SHARING_TIMEOUT.inWholeMilliseconds),
            initialValue = RtcStatsSnapshot.EMPTY,
        )

    init {
        viewModelScope.launch {
            val servers = serverManager.servers()
            val activeServerId = serverManager.getServer()?.id ?: servers.firstOrNull()?.id ?: 0
            _state.update {
                it.copy(
                    serversDropdownItems = servers.map { server ->
                        HADropdownItem(key = server.id, label = server.friendlyName)
                    },
                    selectedServerId = activeServerId,
                )
            }
            loadEntities(activeServerId)
        }
    }

    fun selectServerId(serverId: Int) {
        if (serverId == _state.value.selectedServerId) return
        loadStreamTypesJob?.cancel()
        _state.update {
            it.copy(
                selectedServerId = serverId,
                selectedEntityId = null,
                entityDisplayState = EntityDisplayState.Loading,
                streamTypes = StreamTypesState.None,
            )
        }
        loadEntities(serverId)
    }

    fun selectEntityId(entityId: String?) {
        if (entityId == _state.value.selectedEntityId) return
        _state.update { it.copy(selectedEntityId = entityId) }
        loadStreamTypes(entityId)
    }

    fun updateMediaOptions(mediaOptions: MediaOptions) {
        _state.update { it.copy(mediaOptions = mediaOptions) }
    }

    fun startSession() {
        val current = _state.value
        val entityId = current.selectedEntityId?.trim()?.takeIf { it.isNotEmpty() } ?: return
        if (!current.mediaOptions.hasAnyStream) return
        viewModelScope.launch {
            releaseSession()
            // Two-way audio needs the audio stack in communication mode (before the peer
            // connection's recorder is created) so the microphone is captured with the hardware
            // echo canceller engaged, like a voice call.
            if (current.mediaOptions.audioOut) enterCommunicationAudio()
            val signalingClient =
                HaSignalingClient(serverManager.webSocketRepository(current.selectedServerId), entityId)
            _session.value = webRtcSessionFactory.create(
                signalingClient = signalingClient,
                mediaOptions = current.mediaOptions,
            ).also { it.start() }
        }
    }

    fun stopSession() {
        viewModelScope.launch { releaseSession() }
    }

    private suspend fun releaseSession() {
        _session.value?.release()
        _session.value = null
        exitCommunicationAudio()
    }

    private fun enterCommunicationAudio() {
        val manager = audioManager ?: return
        if (previousAudioMode == null) {
            previousAudioMode = manager.mode
        }
        manager.mode = AudioManager.MODE_IN_COMMUNICATION
    }

    private fun exitCommunicationAudio() {
        val manager = audioManager ?: return
        previousAudioMode?.let { manager.mode = it }
        previousAudioMode = null
    }

    override fun onCleared() {
        // onCleared is not a coroutine and viewModelScope is already cancelling, so release the
        // session on a short-lived scope that outlives it, then tear that scope down.
        val session = _session.value
        _session.value = null
        exitCommunicationAudio()
        if (session != null) {
            val cleanupScope = CoroutineScope(Dispatchers.Main.immediate)
            cleanupScope.launch { session.release() }.invokeOnCompletion { cleanupScope.cancel() }
        }
    }

    private fun loadEntities(serverId: Int) {
        loadEntitiesJob?.cancel()
        loadEntitiesJob = viewModelScope.launch {
            entitiesForDisplayManager.snapshotInContext(serverId) { it.domain == CAMERA_DOMAIN }.collect { state ->
                _state.update { it.copy(entityDisplayState = state) }
            }
        }
    }

    private fun loadStreamTypes(entityId: String?) {
        loadStreamTypesJob?.cancel()
        if (entityId == null) {
            _state.update { it.copy(streamTypes = StreamTypesState.None) }
            return
        }
        _state.update { it.copy(streamTypes = StreamTypesState.Loading) }
        loadStreamTypesJob = viewModelScope.launch {
            val response = serverManager.webSocketRepository(_state.value.selectedServerId)
                .getCameraCapabilities(entityId)
            val streamTypes = if (response == null) {
                Timber.w("Failed to load the camera capabilities of $entityId")
                StreamTypesState.Unavailable
            } else {
                StreamTypesState.Loaded(response.frontendStreamTypes.toSet())
            }
            _state.update { it.copy(streamTypes = streamTypes) }
        }
    }
}
