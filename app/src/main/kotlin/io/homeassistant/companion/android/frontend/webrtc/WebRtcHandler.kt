package io.homeassistant.companion.android.frontend.webrtc

import android.content.Context
import android.media.AudioManager
import androidx.annotation.VisibleForTesting
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ViewModelScoped
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.servers.webSocketRepositoryOrNull
import io.homeassistant.companion.android.frontend.externalbus.FrontendExternalBusRepository
import io.homeassistant.companion.android.frontend.externalbus.incoming.WebRtcStreamType
import io.homeassistant.companion.android.frontend.externalbus.outgoing.WebRtcStreamErrorCode
import io.homeassistant.companion.android.frontend.externalbus.outgoing.WebRtcStreamResultMessage
import io.homeassistant.companion.android.frontend.externalbus.outgoing.WebRtcStreamStoppedMessage
import io.homeassistant.companion.android.frontend.permissions.PermissionManager
import io.homeassistant.companion.android.webrtc.core.ConnectionState
import io.homeassistant.companion.android.webrtc.core.MediaOptions
import io.homeassistant.companion.android.webrtc.core.SessionFailure
import io.homeassistant.companion.android.webrtc.core.WebRtcSession
import io.homeassistant.companion.android.webrtc.core.WebRtcSessionFactory
import io.homeassistant.companion.android.webrtc.signaling.HaSignalingClient
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/** How long to wait for a session to connect before giving up and replying with a timeout error. */
private val CONNECTION_TIMEOUT = 25.seconds

// A stopped notification always carries a non-null message, so fall back to these when the session
// gives none.
private const val MESSAGE_INTERRUPTED = "The stream was interrupted"
private const val MESSAGE_CONNECTION_ENDED = "The stream ended"

/**
 * Handles the frontend's native WebRTC external-bus messages on behalf of the WebView.
 *
 * Currently this is the microphone talk-back backchannel (`webrtc/stream/start`), used when the
 * frontend has no secure context and cannot capture the microphone itself: each session is a
 * mic-only [WebRtcSession] to a camera entity. The reply to the frontend is deferred until the
 * session connects (carrying an app-generated `session_id`), or an error is replied on failure.
 *
 * A `webrtc/stream/stopped` notification is only sent when a stream ends *without* the frontend
 * stopping it: a session that dies after connecting (with the failure's code), or a stream the app
 * tears down itself while still connected ([ERROR_INTERRUPTED], e.g. the app is backgrounded). A
 * frontend-requested [stop] is silent. Scoped to the frontend: sessions do not survive it (no
 * background).
 */
@ViewModelScoped
class WebRtcHandler @VisibleForTesting internal constructor(
    private val webRtcSessionFactory: WebRtcSessionFactory,
    private val serverManager: ServerManager,
    private val permissionManager: PermissionManager,
    private val externalBusRepository: FrontendExternalBusRepository,
    private val audioManager: AudioManager?,
) {

    @Inject
    internal constructor(
        webRtcSessionFactory: WebRtcSessionFactory,
        serverManager: ServerManager,
        permissionManager: PermissionManager,
        externalBusRepository: FrontendExternalBusRepository,
        @ApplicationContext context: Context,
    ) : this(
        webRtcSessionFactory,
        serverManager,
        permissionManager,
        externalBusRepository,
        context.getSystemService<AudioManager>(),
    )

    private class ActiveSession(val player: WebRtcSession, val collector: Job)

    /** The reason a stream ended, for the `webrtc/stream/stopped` notification (code + message). */
    private class StopReason(val code: WebRtcStreamErrorCode, val message: String)

    private val sessions = mutableMapOf<String, ActiveSession>()
    private var previousAudioMode: Int? = null

    /**
     * Starts a microphone stream to [entityId] on [serverId].
     * Replies to the request [messageId] with the session id once connected, or an error when it
     * cannot be started. The session-lifetime collector is launched on [scope], which the caller
     * (the ViewModel) owns and cancels on teardown. Returns once the session is launched; the reply
     * is sent asynchronously.
     */
    suspend fun start(
        scope: CoroutineScope,
        serverId: Int,
        messageId: Int?,
        streamType: WebRtcStreamType,
        entityId: String,
    ) {
        val rejection = when {
            streamType != WebRtcStreamType.MICROPHONE ->
                WebRtcStreamResultMessage.error(
                    messageId,
                    WebRtcStreamErrorCode.UnsupportedStreamType,
                    "Unsupported stream type",
                )
            !permissionManager.checkMicrophonePermission() ->
                WebRtcStreamResultMessage.error(messageId, WebRtcStreamErrorCode.MicrophoneDenied, null)
            else -> null
        }
        if (rejection != null) {
            externalBusRepository.send(rejection)
            return
        }
        val webSocketRepository = serverManager.webSocketRepositoryOrNull(serverId)
        if (webSocketRepository == null) {
            externalBusRepository.send(
                WebRtcStreamResultMessage.error(
                    messageId,
                    WebRtcStreamErrorCode.ServerUnavailable,
                    "Server is not available",
                ),
            )
            return
        }

        if (sessions.isEmpty()) enterCommunicationAudio()

        val sessionId = UUID.randomUUID().toString()
        val player = webRtcSessionFactory.create(
            signalingClient = HaSignalingClient(webSocketRepository, entityId),
            // go2rtc rejects an offer that carries no receive media ("codecs not matched"), so the
            // microphone leg is negotiated as send+receive audio. The received camera audio is muted
            // right after, since the frontend's own player already handles playback.
            mediaOptions = MediaOptions(videoIn = false, audioIn = true, audioOut = true),
        )
        player.audioIn?.setPlaybackEnabled(false)

        val collector = scope.launch { awaitConnectionAndReply(player, sessionId, messageId) }
        // If [scope] was cancelled while this was suspended (e.g. a teardown-time stopAll already
        // ran), do not register a session nothing would release.
        scope.ensureActive()
        sessions[sessionId] = ActiveSession(player, collector)
        player.start()
    }

    /**
     * Waits for [player]'s first terminal state and replies to [messageId]: the session id once
     * connected, or an error on failure or timeout. On success it keeps observing so a session that
     * later dies on its own is torn down and reported.
     */
    private suspend fun awaitConnectionAndReply(player: WebRtcSession, sessionId: String, messageId: Int?) {
        // Bound the wait for the first connection so the frontend always gets a reply, even
        // if the session never reaches a terminal state (stuck negotiating or connecting).
        val terminal = withTimeoutOrNull(CONNECTION_TIMEOUT) {
            player.state.first { it is ConnectionState.Connected || it is ConnectionState.Failed }
        }
        when (terminal) {
            is ConnectionState.Connected -> {
                externalBusRepository.send(WebRtcStreamResultMessage(messageId, sessionId))
                notifyWhenEnded(player, sessionId)
            }

            is ConnectionState.Failed -> {
                // The start request is still open, so the failure is reported in its reply; no
                // separate stopped notification is sent for a stream that never connected.
                externalBusRepository.send(
                    WebRtcStreamResultMessage.error(
                        messageId,
                        terminal.failure.code(),
                        terminal.failure.errorMessage(),
                    ),
                )
                release(sessionId)
            }

            else -> {
                externalBusRepository.send(
                    WebRtcStreamResultMessage.error(messageId, WebRtcStreamErrorCode.Timeout, "Connection timed out"),
                )
                release(sessionId)
            }
        }
    }

    /** Reports a `webrtc/stream/stopped` and releases the session if [player] fails after connecting. */
    private suspend fun notifyWhenEnded(player: WebRtcSession, sessionId: String) {
        player.state.collect { state ->
            if (state is ConnectionState.Failed) {
                release(
                    sessionId,
                    StopReason(state.failure.code(), state.failure.errorMessage() ?: MESSAGE_CONNECTION_ENDED),
                )
            }
        }
    }

    /**
     * Stops the stream with [sessionId] because the frontend asked to (`webrtc/stream/stop`). No
     * `webrtc/stream/stopped` is sent: that notification is only for streams that end without the
     * frontend stopping them. No-op for an unknown or already-stopped session.
     */
    suspend fun stop(sessionId: String) = release(sessionId)

    /**
     * Stops every active stream. Called on a frontend disconnect or teardown so the native peer
     * connections are released (cancelling the owning scope only stops the collectors, not the
     * sessions). Set [notifyFrontend] when the frontend is still connected and did not ask for the
     * stop itself (e.g. the app is backgrounded), so its UI reflects that the microphone is gone;
     * each stream is then reported as [ERROR_INTERRUPTED].
     */
    suspend fun stopAll(notifyFrontend: Boolean = false) {
        val reason = if (notifyFrontend) StopReason(WebRtcStreamErrorCode.Interrupted, MESSAGE_INTERRUPTED) else null
        sessions.keys.toList().forEach { release(it, reason) }
    }

    // release() is also called from the session's own collector, which this cancels, so the teardown
    // is non-cancellable: the suspending player.release() still runs instead of aborting at its first
    // suspension point and leaking the session.
    private suspend fun release(sessionId: String, reason: StopReason? = null) {
        val session = sessions.remove(sessionId) ?: return
        withContext(NonCancellable) {
            session.collector.cancel()
            session.player.release()
            if (reason != null) {
                externalBusRepository.send(WebRtcStreamStoppedMessage(sessionId, reason.code, reason.message))
            }
            if (sessions.isEmpty()) exitCommunicationAudio()
        }
    }

    private fun enterCommunicationAudio() {
        val manager = audioManager ?: return
        if (previousAudioMode == null) {
            previousAudioMode = manager.mode
        }
        manager.mode = AudioManager.MODE_IN_COMMUNICATION
        Timber.d("enterCommunicationAudio: was $previousAudioMode, now ${manager.mode}")
    }

    private fun exitCommunicationAudio() {
        val manager = audioManager ?: return
        previousAudioMode?.let { manager.mode = it }
        Timber.d("exitCommunicationAudio: restored mode ${manager.mode}")
        previousAudioMode = null
    }
}

/** The frontend error code for a session failure. */
private fun SessionFailure.code(): WebRtcStreamErrorCode = when (this) {
    is SessionFailure.Signaling -> WebRtcStreamErrorCode.SignalingFailed
    SessionFailure.ConnectionLost -> WebRtcStreamErrorCode.ConnectionFailed
    is SessionFailure.Internal -> WebRtcStreamErrorCode.Unknown()
}

/** A human-readable message for the failure, when the session provides one. */
private fun SessionFailure.errorMessage(): String? = when (this) {
    is SessionFailure.Signaling -> message
    SessionFailure.ConnectionLost -> null
    is SessionFailure.Internal -> message
}
