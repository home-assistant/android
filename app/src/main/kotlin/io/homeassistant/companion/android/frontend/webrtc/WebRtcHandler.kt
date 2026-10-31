package io.homeassistant.companion.android.frontend.webrtc

import android.content.Context
import android.media.AudioManager
import androidx.annotation.VisibleForTesting
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ViewModelScoped
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.servers.webSocketRepositoryOrNull
import io.homeassistant.companion.android.common.data.websocket.WebSocketRepository
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/** How long to wait for a session to connect before giving up and replying with a timeout error. */
private val CONNECTION_TIMEOUT = 25.seconds

// A stopped event always carries a non-null message, so fall back to these when the session
// gives none.
private const val MESSAGE_INTERRUPTED = "The stream was interrupted"
private const val MESSAGE_CONNECTION_ENDED = "The stream ended"

/**
 * Manages the WebRTC sessions, capturing the microphone on behalf of the WebView.
 * [start] opens a mic-only [WebRtcSession] to an entity, [stop] ends one, and [stopAll] ends them all.
 *
 * [start]'s reply is deferred until the session connects (carrying an app-generated session id), or
 * an error is replied on failure. A stopped event is sent only when a stream ends *without*
 * the frontend stopping it: a session that dies after connecting (with the failure's code), or one
 * the handler tears down itself while still connected ([WebRtcStreamErrorCode.Interrupted], e.g. the
 * app is backgrounded). A frontend-requested [stop] is silent. Sessions are scoped to the frontend
 * and do not survive it (no background).
 *
 * Not thread safe: the session and audio state are plain fields. Every call, and the per-session
 * coroutines launched on the scope passed to [start], must run on a single-threaded/confined
 * dispatcher (the ViewModel's main dispatcher) so they never execute in parallel. The same scope is
 * not sufficient on its own if that scope uses a multi-threaded dispatcher.
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

    /** A session the handler owns. The variant is the stage its start request has reached. */
    private sealed interface ActiveSession {
        /** The peer connection, or null for a [Starting] session that has not created it yet. */
        val player: WebRtcSession?

        /** The collector observing the session, or null for a [Starting] session that has none yet. */
        val collector: Job?

        /**
         * Being set up (permission, server, peer connection): registered so a [stop]/[stopAll] reaches
         * it, but its setup runs inline in the caller. The frontend still awaits the reply to [messageId].
         */
        class Starting(val messageId: Int?) : ActiveSession {
            override val player: WebRtcSession? = null
            override val collector: Job? = null
        }

        /** Created and connecting: the [collector] awaits the first terminal state. [messageId] is
         *  kept, as in [Starting], so a teardown before connecting fails that still-open start request. */
        class Connecting(override val player: WebRtcSession, override val collector: Job, val messageId: Int?) :
            ActiveSession

        /** Replied to with the session id, so the frontend now tracks the session itself by that id. */
        class Connected(override val player: WebRtcSession, override val collector: Job) : ActiveSession
    }

    /** Why a stream was torn down, deciding what (if anything) the frontend is told. */
    private sealed interface StopReason {
        /** The frontend asked for the stop, or its page is gone: nothing is sent. */
        data object Silent : StopReason

        /** An end to report. */
        data class Reported(val code: WebRtcStreamErrorCode, val message: String) : StopReason
    }

    private val sessions = mutableMapOf<String, ActiveSession>()
    private var previousAudioMode: Int? = null

    /**
     * Starts a microphone stream to [entityId] on [serverId]. Replies to the
     * request [messageId] with the session id once connected, or an error when it cannot be started.
     * The setup runs here, on the caller's coroutine; the connected session is then observed on
     * [scope], which the caller owns and cancels on teardown.
     */
    suspend fun start(
        scope: CoroutineScope,
        serverId: Int,
        messageId: Int?,
        streamType: WebRtcStreamType,
        entityId: String,
    ) {
        if (streamType != WebRtcStreamType.MICROPHONE) {
            externalBusRepository.send(
                WebRtcStreamResultMessage.error(
                    messageId,
                    WebRtcStreamErrorCode.UnsupportedStreamType,
                    "Unsupported stream type",
                ),
            )
            return
        }
        val sessionId = UUID.randomUUID().toString()
        sessions[sessionId] = ActiveSession.Starting(messageId)
        openSession(scope, sessionId, serverId, messageId, entityId)
    }

    /**
     * Stops the stream with [sessionId]. No stopped event is sent.
     * No-op for an unknown or already-stopped session.
     */
    suspend fun stop(sessionId: String) = release(sessionId)

    /**
     * Stops every active stream. Set [notifyFrontend] when the frontend is still connected and did not ask for the
     * stop itself (e.g. the app is backgrounded); each stream is then reported as [WebRtcStreamErrorCode.Interrupted].
     */
    suspend fun stopAll(notifyFrontend: Boolean = false) {
        val reason = if (notifyFrontend) {
            StopReason.Reported(WebRtcStreamErrorCode.Interrupted, MESSAGE_INTERRUPTED)
        } else {
            StopReason.Silent
        }
        val sessionSnapshot = sessions.keys.toList()
        sessionSnapshot.forEach { release(it, reason) }
    }

    /**
     * Sets up the session [sessionId], then hands it to a collector to observe. Runs on the caller's
     * coroutine, so cancelling that scope tears the setup down. The [finally] cleans up (releases the
     * session, restores the audio) unless a collector took it over ([handedOff]).
     */
    private suspend fun openSession(
        scope: CoroutineScope,
        sessionId: String,
        serverId: Int,
        messageId: Int?,
        entityId: String,
    ) {
        var player: WebRtcSession? = null
        var handedOff = false
        try {
            val webSocketRepository = resolveTarget(sessionId, messageId, serverId)
            if (webSocketRepository != null) {
                enterCommunicationAudio()
                val created = createSession(webSocketRepository, entityId, sessionId, messageId)
                player = created
                if (created != null && sessionId in sessions) {
                    val collector = scope.launch { awaitConnectionAndReply(created, sessionId, messageId) }
                    sessions[sessionId] = ActiveSession.Connecting(created, collector, messageId)
                    handedOff = true
                    created.start()
                }
            }
        } finally {
            if (!handedOff) {
                // Rejected, abandoned by a stop/stopAll, cancelled (scope cleared), or failed before a
                // collector took over.
                withContext(NonCancellable) {
                    sessions.remove(sessionId)
                    player?.release()
                    if (sessions.isEmpty()) exitCommunicationAudio()
                }
            }
        }
    }

    /**
     * Returns the [WebSocketRepository] to open the session on, or null: with an error reply when the microphone
     * permission is denied or the server is unavailable, silently when the session was abandoned.
     */
    /**
     * Creates the peer connection, or null when it could not be built: a factory failure (e.g. native
     * init) is logged and replied as an unknown error rather than crashing the ViewModel coroutine.
     */
    private suspend fun createSession(
        webSocketRepository: WebSocketRepository,
        entityId: String,
        sessionId: String,
        messageId: Int?,
    ): WebRtcSession? = try {
        webRtcSessionFactory.create(
            signalingClient = HaSignalingClient(webSocketRepository, entityId),
            mediaOptions = MediaOptions(videoIn = false, audioIn = false, audioOut = true),
        )
    } catch (e: CancellationException) {
        throw e
    } catch (failure: Exception) {
        Timber.e(failure, "Failed to create the WebRTC session")
        if (sessionId in sessions) {
            externalBusRepository.send(
                WebRtcStreamResultMessage.error(
                    messageId,
                    WebRtcStreamErrorCode.Unknown,
                    "Could not create the session",
                ),
            )
        }
        null
    }

    private suspend fun resolveTarget(sessionId: String, messageId: Int?, serverId: Int): WebSocketRepository? = when {
        !permissionManager.checkMicrophonePermission() -> {
            if (sessionId in sessions) {
                externalBusRepository.send(
                    WebRtcStreamResultMessage.error(messageId, WebRtcStreamErrorCode.MicrophoneDenied, null),
                )
            }
            null
        }
        sessionId !in sessions -> null
        else -> serverManager.webSocketRepositoryOrNull(serverId).also { repository ->
            if (repository == null) {
                externalBusRepository.send(
                    WebRtcStreamResultMessage.error(
                        messageId,
                        WebRtcStreamErrorCode.ServerUnavailable,
                        "Server is not available",
                    ),
                )
            }
        }
    }

    /**
     * Waits for [player]'s first terminal state and replies to [messageId]: the session id once
     * connected, or an error on failure or timeout. On success it keeps observing so a session that
     * later dies on its own is torn down and reported.
     */
    private suspend fun awaitConnectionAndReply(player: WebRtcSession, sessionId: String, messageId: Int?) {
        val terminal = withTimeoutOrNull(CONNECTION_TIMEOUT) {
            player.state.first { it is ConnectionState.Connected || it is ConnectionState.Failed }
        }
        when (terminal) {
            is ConnectionState.Connected -> {
                // Not Connecting any more means a stop/stopAll released it; its reply went out then.
                val connecting = sessions[sessionId] as? ActiveSession.Connecting ?: return
                withContext(NonCancellable) {
                    sessions[sessionId] = ActiveSession.Connected(player, connecting.collector)
                    externalBusRepository.send(WebRtcStreamResultMessage(messageId, sessionId))
                }
                observeUntilFailed(player, sessionId)
            }

            is ConnectionState.Failed -> release(
                sessionId,
                StopReason.Reported(
                    terminal.failure.code(),
                    terminal.failure.errorMessage() ?: MESSAGE_CONNECTION_ENDED,
                ),
            )

            else -> release(sessionId, StopReason.Reported(WebRtcStreamErrorCode.Timeout, "Connection timed out"))
        }
    }

    /** Suspends observing the connected [player] until it fails, then releases it and reports a stopped event. */
    private suspend fun observeUntilFailed(player: WebRtcSession, sessionId: String) {
        player.state.collect { state ->
            if (state is ConnectionState.Failed) {
                release(
                    sessionId,
                    StopReason.Reported(state.failure.code(), state.failure.errorMessage() ?: MESSAGE_CONNECTION_ENDED),
                )
            }
        }
    }

    /**
     * Non-cancellable so the suspending player.release() still runs. A Starting session's inline setup
     * owns its teardown, so only its reply is sent here; a Connecting/Connected one is torn down here.
     */
    private suspend fun release(sessionId: String, reason: StopReason = StopReason.Silent) {
        val session = sessions.remove(sessionId) ?: return
        withContext(NonCancellable) {
            session.collector?.cancel()
            session.player?.release()
            val endMessage = (reason as? StopReason.Reported)?.let { report ->
                when (session) {
                    // Acknowledged: the frontend tracks the session by its id, so report a stopped event.
                    is ActiveSession.Connected -> WebRtcStreamStoppedMessage(sessionId, report.code, report.message)
                    // Still unacknowledged: fail the open start request rather than send a stopped event
                    // the frontend could not correlate.
                    is ActiveSession.Starting ->
                        WebRtcStreamResultMessage.error(session.messageId, report.code, report.message)
                    is ActiveSession.Connecting ->
                        WebRtcStreamResultMessage.error(session.messageId, report.code, report.message)
                }
            }
            endMessage?.let { externalBusRepository.send(it) }
            if (sessions.isEmpty()) exitCommunicationAudio()
        }
    }

    private fun enterCommunicationAudio() {
        val manager = audioManager ?: return
        if (previousAudioMode != null) return
        previousAudioMode = manager.mode
        manager.mode = AudioManager.MODE_IN_COMMUNICATION
        Timber.d("enterCommunicationAudio: now ${manager.mode}")
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
    is SessionFailure.Internal -> WebRtcStreamErrorCode.Unknown
}

/** A human-readable message for the failure, when the session provides one. */
private fun SessionFailure.errorMessage(): String? = when (this) {
    is SessionFailure.Signaling -> message
    SessionFailure.ConnectionLost -> null
    is SessionFailure.Internal -> message
}
