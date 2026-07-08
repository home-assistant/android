package io.homeassistant.companion.android.settings.developer.webrtc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.webrtc.core.MicState
import io.homeassistant.companion.android.webrtc.core.PlayerState
import io.homeassistant.companion.android.webrtc.core.audio.AudioController
import io.homeassistant.companion.android.webrtc.core.session.WebRtcSession
import io.homeassistant.companion.android.webrtc.core.session.libwebrtc.LibWebRtcPeerConnectionControllerFactory
import io.homeassistant.companion.android.webrtc.signaling.HaSignalingClient
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import livekit.org.webrtc.EglBase

private val STATE_SHARING_TIMEOUT = 5.seconds

/**
 * Owns the WebRTC session of the developer debug screen, so the session survives configuration
 * changes and is always released when the screen is destroyed.
 */
@HiltViewModel
class WebRtcDebugViewModel @Inject constructor(
    private val serverManager: ServerManager,
    private val controllerFactory: LibWebRtcPeerConnectionControllerFactory,
    private val audioController: AudioController,
) : ViewModel() {

    private val _session = MutableStateFlow<WebRtcSession?>(null)
    val session: StateFlow<WebRtcSession?> = _session.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val playerState: StateFlow<PlayerState> = _session
        .flatMapLatest { session -> session?.state ?: flowOf(PlayerState.Idle) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_SHARING_TIMEOUT.inWholeMilliseconds),
            initialValue = PlayerState.Idle,
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val micState: StateFlow<MicState> = _session
        .flatMapLatest { session -> session?.micState ?: flowOf(MicState.Off) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_SHARING_TIMEOUT.inWholeMilliseconds),
            initialValue = MicState.Off,
        )

    /** EGL context the renderer must share with the hardware decoder. */
    val eglContext: EglBase.Context
        get() = controllerFactory.eglBase.eglBaseContext

    fun startSession(entityId: String) {
        val trimmedEntityId = entityId.trim()
        if (trimmedEntityId.isEmpty()) return
        viewModelScope.launch {
            stopSession()
            val signalingClient = HaSignalingClient(serverManager.webSocketRepository())
            _session.value = WebRtcSession(
                entityId = trimmedEntityId,
                signalingClient = signalingClient,
                controllerFactory = controllerFactory,
                audioController = audioController,
            ).also { it.start() }
        }
    }

    /** The caller must hold the `RECORD_AUDIO` permission before enabling the microphone. */
    fun setMicEnabled(enabled: Boolean) {
        _session.value?.setMicEnabled(enabled)
    }

    fun stopSession() {
        _session.value?.release()
        _session.value = null
    }

    override fun onCleared() {
        stopSession()
    }
}
