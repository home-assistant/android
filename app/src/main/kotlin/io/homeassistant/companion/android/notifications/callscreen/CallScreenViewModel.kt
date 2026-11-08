package io.homeassistant.companion.android.notifications.callscreen

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.webrtc.EglBase
import org.webrtc.VideoTrack

private const val STATE_SUBSCRIPTION_TIMEOUT_MS = 5_000L

/**
 * Display-ready state of the call UI.
 *
 * @property videoTrack live video to draw, otherwise [poster] is shown
 */
internal data class CallScreenUiState(
    val title: String?,
    val message: String?,
    val poster: Bitmap?,
    val videoTrack: VideoTrack?,
    val buttons: List<CallAction>,
    val menuItems: List<CallAction>,
    val phase: CallPhase,
    val micAvailable: Boolean,
    val micMuted: Boolean,
    val speakerOn: Boolean,
    val connectedAt: Instant?,
)

/** Exposes the call of [CallScreenManager] to the call UI and forwards the user intents to it. */
@HiltViewModel
internal class CallScreenViewModel @Inject constructor(
    private val manager: CallScreenManager,
    private val clock: Clock,
) : ViewModel() {

    /** State of the call, `null` once the call is over and the UI must close. */
    val uiState: StateFlow<CallScreenUiState?> = manager.activeCall
        .map { it?.toUiState() }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STATE_SUBSCRIPTION_TIMEOUT_MS),
            manager.activeCall.value?.toUiState(),
        )

    /** One-shot errors to show to the user. */
    val events: Flow<CallScreenEvent> = manager.events

    /** EGL context renderers must share with the video decoder. */
    val eglBaseContext: EglBase.Context get() = manager.eglBaseContext

    /** Whether the call with [callId] is the active one. */
    fun isActive(callId: Long): Boolean = manager.activeCall.value?.config?.callId == callId

    /** Current time, used to tick the call duration. */
    fun now(): Instant = clock.now()

    fun onAnswer() = manager.answer()

    fun onDecline() = manager.decline()

    fun onHangUp() = manager.hangUp()

    fun onAction(index: Int) = manager.runAction(index)

    fun onSilence() = manager.silence()

    fun onMicrophoneDenied() = manager.notifyMicrophoneDenied()

    /** Whether the call asks to talk back to the stream, which needs the microphone permission. */
    fun wantsMicrophone(): Boolean = manager.activeCall.value?.config?.media?.contains(CallMedia.MICROPHONE) == true

    fun onToggleMic(muted: Boolean) = manager.setMicMuted(muted)

    fun onToggleSpeaker(on: Boolean) = manager.setSpeaker(on)

    fun onStart() = manager.startPreview()

    fun onStop() = manager.stopPreview()

    /** Whether the call is still ringing. */
    fun isRinging(): Boolean = manager.activeCall.value?.phase == CallPhase.Ringing

    private fun ActiveCall.toUiState() = CallScreenUiState(
        title = config.title,
        message = config.message,
        poster = poster,
        videoTrack = videoTrack,
        buttons = config.buttons,
        menuItems = config.menuItems,
        phase = phase,
        micAvailable = micAvailable,
        micMuted = micMuted,
        speakerOn = speakerOn,
        connectedAt = connectedAt,
    )
}
