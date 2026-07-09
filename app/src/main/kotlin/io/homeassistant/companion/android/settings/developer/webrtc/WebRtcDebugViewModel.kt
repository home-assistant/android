package io.homeassistant.companion.android.settings.developer.webrtc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.webrtc.core.MediaOptions
import io.homeassistant.companion.android.webrtc.core.MicState
import io.homeassistant.companion.android.webrtc.core.PlayerState
import io.homeassistant.companion.android.webrtc.core.audio.AudioController
import io.homeassistant.companion.android.webrtc.core.session.RtcDebugStats
import io.homeassistant.companion.android.webrtc.core.session.WebRtcSession
import io.homeassistant.companion.android.webrtc.core.session.libwebrtc.LibWebRtcPeerConnectionControllerFactory
import io.homeassistant.companion.android.webrtc.signaling.HaSignalingClient
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import livekit.org.webrtc.EglBase

private val STATE_SHARING_TIMEOUT = 5.seconds
private val STATS_POLL_INTERVAL = 1.seconds

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

    /**
     * Multi-line technical summary of the connection statistics, refreshed every
     * [STATS_POLL_INTERVAL] while a session exists. Rates are derived from consecutive snapshots.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val debugStats: StateFlow<String?> = _session
        .flatMapLatest { session -> session?.let(::statsText) ?: flowOf(null) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_SHARING_TIMEOUT.inWholeMilliseconds),
            initialValue = null,
        )

    /** EGL context the renderer must share with the hardware decoder. */
    val eglContext: EglBase.Context
        get() = controllerFactory.eglBase.eglBaseContext

    fun startSession(entityId: String, audioOnly: Boolean) {
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
                mediaOptions = MediaOptions(video = !audioOnly),
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

private fun statsText(session: WebRtcSession): Flow<String?> = flow {
    var previous: RtcDebugStats? = null
    while (true) {
        val stats = session.debugStats()
        emit(stats?.format(previous, STATS_POLL_INTERVAL.inWholeMilliseconds))
        previous = stats
        delay(STATS_POLL_INTERVAL)
    }
}

// Raw technical output, on purpose not localized on this developer-only screen
private fun RtcDebugStats.format(previous: RtcDebugStats?, intervalMs: Long): String = buildString {
    appendLine("codec: ${videoCodec ?: "-"}  ${frameWidth ?: "-"}x${frameHeight ?: "-"}")
    val fps = framesPerSecond?.let { "%.1f".format(it) } ?: "-"
    appendLine("fps: $fps  decoded: ${framesDecoded ?: "-"}  lost: ${videoPacketsLost ?: "-"}")
    appendLine(
        "video: ${bitrateKbps(videoBytesReceived, previous?.videoBytesReceived, intervalMs)}  " +
            "audio in: ${bitrateKbps(audioBytesReceived, previous?.audioBytesReceived, intervalMs)}  " +
            "mic out: ${bitrateKbps(audioBytesSent, previous?.audioBytesSent, intervalMs)}",
    )
    val rtt = roundTripTimeMs?.let { "%.0f ms".format(it) } ?: "-"
    append(
        "rtt: $rtt  ice: ${localCandidateType ?: "-"} -> ${remoteCandidateType ?: "-"} (${transportProtocol ?: "-"})",
    )
}

private fun bitrateKbps(bytes: Long?, previousBytes: Long?, intervalMs: Long): String {
    if (bytes == null || previousBytes == null || bytes < previousBytes) return "- kbps"
    return "${(bytes - previousBytes) * 8 * 1000 / intervalMs / 1000} kbps"
}
