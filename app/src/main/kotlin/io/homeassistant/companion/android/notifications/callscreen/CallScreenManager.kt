package io.homeassistant.companion.android.notifications.callscreen

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.annotation.MainThread
import androidx.annotation.StringRes
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.servers.UrlState
import io.homeassistant.companion.android.common.util.di.SuspendProvider
import io.homeassistant.companion.android.common.util.isAutomotive
import io.homeassistant.companion.android.notifications.MessagingManager
import io.homeassistant.companion.android.notifications.NotificationImageDownloader
import io.homeassistant.companion.android.notifications.callscreen.webrtc.Go2RtcSession
import io.homeassistant.companion.android.notifications.callscreen.webrtc.Go2RtcSessionState
import io.homeassistant.companion.android.notifications.callscreen.webrtc.WebRtcFactoryProvider
import io.homeassistant.companion.android.notifications.callscreen.webrtc.buildGo2RtcWsUrl
import java.util.Objects
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import org.webrtc.EglBase
import org.webrtc.VideoTrack
import timber.log.Timber

private const val EVENT_NOTIFICATION_ACTION = "mobile_app_notification_action"
private const val EVENT_ACTION_KEY = "action"
private const val SIGNED_PATH = "/api/webrtc/ws"
private const val BACKCHANNEL_ERROR_MARKER = "backchannel"
private const val REQUEST_ANSWER = -1
private const val REQUEST_FULL_SCREEN = -2
private const val REQUEST_DECLINE = -3
private const val REQUEST_HANG_UP = -4
private val SIGNED_PATH_VALIDITY = 30.seconds
private val POSTER_DOWNLOAD_TIMEOUT = 3.seconds

/** Why the call is shown as not working while still open. */
internal sealed interface CallFailure {
    /** The notification has no stream, or the server URL or signature is unavailable. */
    data object StreamUnavailable : CallFailure

    /** The WebRTC session failed with [reason]. */
    data class Session(val reason: String) : CallFailure
}

/** Phase of the active call. */
internal sealed interface CallPhase {
    data object Ringing : CallPhase
    data object Connecting : CallPhase
    data object Connected : CallPhase
    data class Failed(val failure: CallFailure) : CallPhase
}

/** One-shot notification for the call UI. */
internal sealed interface CallScreenEvent {
    data class Error(@StringRes val message: Int) : CallScreenEvent
}

/**
 * State of the call currently ringing or in progress.
 *
 * @property micAvailable `true` if the microphone is sent to the stream
 * @property silenced `true` once ringtone and vibration were stopped
 */
internal data class ActiveCall(
    val config: CallScreenConfig,
    val phase: CallPhase = CallPhase.Ringing,
    val poster: Bitmap? = null,
    val silenced: Boolean = false,
    val micAvailable: Boolean = false,
    val micMuted: Boolean = false,
    val speakerOn: Boolean = true,
    val connectedAt: Instant? = null,
    val videoTrack: VideoTrack? = null,
)

private enum class EndReason { Declined, HungUp, ActionEnded, Cleared, Missed, Replaced }

/** Everything owned by the active call that doesn't belong in the observable state. */
private class CallRuntime(val person: Person, val intents: RingingIntents, val uriIntents: Map<Int, PendingIntent>) {
    var ringTimeoutJob: Job? = null
    var screenOffReceiver: BroadcastReceiver? = null
    var session: Go2RtcSession? = null
    var preview: Go2RtcSession? = null
    var sessionJob: Job? = null
    var previewJob: Job? = null
    var audioAcquired = false
    var micRetried = false
}

/**
 * Owns the single call screen call of the app: ringing, the go2rtc session once answered, and the
 * events sent to Home Assistant. Everything runs on the main thread.
 */
@Singleton
internal class CallScreenManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val serverManager: ServerManager,
    private val okHttpClientProvider: SuspendProvider<OkHttpClient>,
    private val imageDownloader: NotificationImageDownloader,
    private val notifications: CallScreenNotifications,
    private val webRtc: WebRtcFactoryProvider,
    private val audioRouter: CallAudioRouter,
    private val clock: Clock,
) {
    companion object {
        const val EXTRA_CALL_ID = "call_screen_call_id"
        const val EXTRA_TAG = "call_screen_tag"
        const val EXTRA_AUTO_ANSWER = "call_screen_auto_answer"
        const val EXTRA_ACTION_INDEX = "call_screen_action_index"
        const val ACTION_DECLINE = "io.homeassistant.companion.android.callscreen.DECLINE"
        const val ACTION_RUN = "io.homeassistant.companion.android.callscreen.RUN"
        const val ACTION_HANG_UP = "io.homeassistant.companion.android.callscreen.HANG_UP"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ringMutex = Mutex()
    private val _activeCall = MutableStateFlow<ActiveCall?>(null)
    private val _events = Channel<CallScreenEvent>(Channel.BUFFERED)
    private var runtime: CallRuntime? = null

    /** The call currently ringing or in progress, `null` when there is none. */
    val activeCall: StateFlow<ActiveCall?> = _activeCall.asStateFlow()

    /** One-shot events for the call UI. */
    val events: Flow<CallScreenEvent> = _events.receiveAsFlow()

    /** EGL context renderers must share with the video decoder. */
    val eglBaseContext: EglBase.Context get() = webRtc.eglBase.eglBaseContext

    /**
     * Parse [data] as a call screen notification.
     *
     * @return the config, or `null` if it is a regular notification or the device can't show a call screen
     */
    fun parseConfig(data: Map<String, String>, callId: Long): CallScreenConfig? {
        if (context.isAutomotive()) return null
        return parseCallScreenConfig(
            data,
            callId,
            CallScreenDefaults(
                answerTitle = context.getString(commonR.string.call_screen_answer),
                declineTitle = context.getString(commonR.string.call_screen_decline),
                moreTitle = context.getString(commonR.string.call_screen_more),
            ),
        )
    }

    /**
     * Start ringing for [config].
     *
     * @param uriIntents pending intents of the `URI` actions by [CallAction.index]
     * @return `false` without side effects if the call can't be shown, in which case a regular
     * notification must be posted instead
     */
    @MainThread
    suspend fun tryRing(config: CallScreenConfig, uriIntents: Map<Int, PendingIntent>): Boolean = ringMutex.withLock {
        if (_activeCall.value?.phase.let { it != null && it != CallPhase.Ringing }) {
            Timber.d("A call is in progress, posting the call screen notification as a regular one")
            return@withLock false
        }
        if (_activeCall.value != null) end(EndReason.Replaced)

        val poster = loadPoster(config)
        val person = notifications.buildPerson(config, poster)
        val intents = buildRingingIntents(config)
        val call = ActiveCall(config = config, poster = poster)
        val newRuntime = CallRuntime(person, intents, uriIntents)
        runtime = newRuntime
        _activeCall.value = call

        if (!postRinging(config, person, intents, silenced = false)) {
            runtime = null
            _activeCall.value = null
            return@withLock false
        }
        newRuntime.ringTimeoutJob = scope.launch {
            delay(config.ringTimeout)
            end(EndReason.Missed)
        }
        registerScreenOffReceiver(newRuntime)
        true
    }

    /** Stop ringtone and vibration but keep the call, no-op if the call is not ringing or already silenced. */
    @MainThread
    fun silence() {
        val call = _activeCall.value ?: return
        val current = runtime ?: return
        if (call.phase != CallPhase.Ringing || call.silenced) return
        _activeCall.value = call.copy(silenced = true)
        postRinging(call.config, current.person, current.intents, silenced = true)
    }

    /** Answer the ringing call: send the answer event, start the foreground service and open the stream. */
    @MainThread
    fun answer() {
        val call = _activeCall.value ?: return
        val current = runtime ?: return
        if (call.phase != CallPhase.Ringing) return

        current.ringTimeoutJob?.cancel()
        unregisterScreenOffReceiver(current)
        closePreview(current)
        NotificationManagerCompat.from(context).cancel(call.config.tag, call.config.tag.hashCode())

        val micGranted = hasMicrophonePermission()
        _activeCall.value = call.copy(
            phase = CallPhase.Connecting,
            micAvailable = CallMedia.MICROPHONE in call.config.media && micGranted,
            videoTrack = null,
        )
        fireEvent(call.config, roleKey(call.config, CallActionRole.Answer))
        ContextCompat.startForegroundService(context, Intent(context, CallScreenService::class.java))
        audioRouter.acquire(speakerOn = true)
        current.audioAcquired = true
        val media = if (micGranted) call.config.media else call.config.media - CallMedia.MICROPHONE
        startSession(call.config, current, media)
    }

    /** Tell the user that the microphone permission was denied, so the stream is answered without talking back. */
    @MainThread
    fun notifyMicrophoneDenied() {
        _events.trySend(CallScreenEvent.Error(commonR.string.call_screen_mic_denied))
    }

    /** Decline the ringing call and notify Home Assistant. */
    @MainThread
    fun decline() {
        val call = _activeCall.value ?: return
        fireEvent(call.config, roleKey(call.config, CallActionRole.Decline))
        end(EndReason.Declined)
    }

    /** End the connected call. Home Assistant is not notified. */
    @MainThread
    fun hangUp() = end(EndReason.HungUp)

    /**
     * Run the custom or menu action at [index] (see [CallAction.index]): open its URI or fire its event,
     * stop ringing and end the call if the action asks for it.
     */
    @MainThread
    fun runAction(index: Int) {
        val call = _activeCall.value ?: return
        val action = (call.config.buttons + call.config.menuItems).firstOrNull { it.index == index && index != 0 }
            ?: return
        val uriIntent = runtime?.uriIntents?.get(index)
        if (action.key == MessagingManager.URI && uriIntent != null) {
            try {
                uriIntent.send()
            } catch (e: PendingIntent.CanceledException) {
                Timber.e(e, "Failed to open the URI of call screen action ${action.key}")
            }
        } else {
            fireEvent(call.config, action.key)
        }
        silence()
        if (action.endCall) end(EndReason.ActionEnded)
    }

    /** End the call if it is the one with [tag], used when Home Assistant clears the notification. */
    @MainThread
    fun endByTag(tag: String) {
        if (_activeCall.value?.config?.tag == tag) end(EndReason.Cleared)
    }

    /** Show the live stream behind the ringing UI. Called when the call UI becomes visible. */
    @MainThread
    fun startPreview() {
        val call = _activeCall.value ?: return
        val current = runtime ?: return
        val config = call.config
        if (call.phase != CallPhase.Ringing || config.background != CallBackground.Stream) return
        if (current.previewJob != null || config.stream == null) return

        current.previewJob = scope.launch {
            val session = Go2RtcSession(webRtc.factory(), okHttpClientProvider(), scope)
            current.preview = session
            launch { session.videoTrack.collect { track -> updateCall(config.callId) { it.copy(videoTrack = track) } } }
            if (!connect(session, config, setOf(CallMedia.VIDEO))) closePreview(current)
        }
    }

    /** Release the preview stream. Called when the call UI is hidden. */
    @MainThread
    fun stopPreview() {
        runtime?.let { closePreview(it) }
    }

    /** Mute or unmute the microphone sent to the stream. */
    @MainThread
    fun setMicMuted(muted: Boolean) {
        val call = _activeCall.value ?: return
        runtime?.session?.setMicrophoneEnabled(!muted)
        _activeCall.value = call.copy(micMuted = muted)
    }

    /** Route the call audio to the speaker or the earpiece. */
    @MainThread
    fun setSpeaker(on: Boolean) {
        val call = _activeCall.value ?: return
        audioRouter.setSpeaker(on)
        _activeCall.value = call.copy(speakerOn = on)
    }

    /** Build the foreground notification of the connected call, `null` if there is no call. */
    @MainThread
    fun buildOngoingNotification(): Notification? {
        val call = _activeCall.value ?: return null
        val current = runtime ?: return null
        return notifications.buildOngoing(
            call.config,
            current.person,
            receiverIntent(call.config, ACTION_HANG_UP, REQUEST_HANG_UP),
            current.intents.fullScreen,
        )
    }

    private suspend fun loadPoster(config: CallScreenConfig): Bitmap? {
        val path = (config.background as? CallBackground.Image)?.path ?: config.poster ?: return null
        return withTimeoutOrNull(POSTER_DOWNLOAD_TIMEOUT) { imageDownloader.downloadServerImage(config.serverId, path) }
    }

    @Suppress("ReturnCount")
    private fun postRinging(
        config: CallScreenConfig,
        person: Person,
        intents: RingingIntents,
        silenced: Boolean,
    ): Boolean {
        val tag = config.tag
        return try {
            post(tag, notifications.buildRinging(config, person, intents, silenced))
            true
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "CallStyle rejected by the system, retrying with plain actions")
            try {
                post(tag, notifications.buildRinging(config, person, intents, silenced, useCallStyle = false))
                true
            } catch (e: IllegalArgumentException) {
                Timber.e(e, "Failed to post the call screen notification for tag=$tag")
                false
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun post(tag: String, notification: Notification) {
        NotificationManagerCompat.from(context).notify(tag, tag.hashCode(), notification)
    }

    private fun registerScreenOffReceiver(current: CallRuntime) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = silence()
        }
        current.screenOffReceiver = receiver
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    private fun unregisterScreenOffReceiver(current: CallRuntime) {
        current.screenOffReceiver?.let { context.unregisterReceiver(it) }
        current.screenOffReceiver = null
    }

    private fun closePreview(current: CallRuntime) {
        current.previewJob?.cancel()
        current.previewJob = null
        current.preview?.close()
        current.preview = null
        _activeCall.update { call -> call?.takeIf { it.phase == CallPhase.Ringing }?.copy(videoTrack = null) ?: call }
    }

    /** (Re)start the answered session, mapping its state and video track to the call state. */
    private fun startSession(config: CallScreenConfig, current: CallRuntime, media: Set<CallMedia>) {
        current.sessionJob?.cancel()
        current.session?.close()
        current.session = null
        current.sessionJob = scope.launch {
            val session = Go2RtcSession(webRtc.factory(), okHttpClientProvider(), scope)
            current.session = session
            launch { session.videoTrack.collect { track -> updateCall(config.callId) { it.copy(videoTrack = track) } } }
            launch {
                session.state.collect { state -> onSessionState(config, current, media, state) }
            }
            if (!connect(session, config, media)) {
                updateCall(config.callId) { it.copy(phase = CallPhase.Failed(CallFailure.StreamUnavailable)) }
            }
        }
    }

    private fun onSessionState(
        config: CallScreenConfig,
        current: CallRuntime,
        media: Set<CallMedia>,
        state: Go2RtcSessionState,
    ) {
        when (state) {
            Go2RtcSessionState.Connected -> updateCall(config.callId) {
                it.copy(phase = CallPhase.Connected, connectedAt = it.connectedAt ?: clock.now())
            }

            is Go2RtcSessionState.Failed -> {
                val retryWithoutMic = CallMedia.MICROPHONE in media &&
                    !current.micRetried &&
                    state.reason.contains(BACKCHANNEL_ERROR_MARKER, ignoreCase = true)
                if (retryWithoutMic) {
                    current.micRetried = true
                    _events.trySend(CallScreenEvent.Error(commonR.string.call_screen_no_backchannel))
                    updateCall(config.callId) { it.copy(micAvailable = false, micMuted = false) }
                    startSession(config, current, media - CallMedia.MICROPHONE)
                } else {
                    updateCall(config.callId) { it.copy(phase = CallPhase.Failed(CallFailure.Session(state.reason))) }
                }
            }

            Go2RtcSessionState.Connecting, Go2RtcSessionState.Closed -> Unit
        }
    }

    /** Sign the endpoint and start negotiating [session], `false` if the stream can't be reached. */
    private suspend fun connect(session: Go2RtcSession, config: CallScreenConfig, media: Set<CallMedia>): Boolean {
        val source = config.stream ?: return false
        val signedPath = serverManager.webSocketRepository(config.serverId)
            .signPath(SIGNED_PATH, SIGNED_PATH_VALIDITY)
        val baseUrl = (serverManager.connectionStateProvider(config.serverId).urlFlow().first() as? UrlState.HasUrl)
            ?.url
        if (signedPath == null || baseUrl == null) {
            Timber.w("Unable to start the call stream: signedPath=${signedPath != null} url=${baseUrl != null}")
            return false
        }
        session.connect(buildGo2RtcWsUrl(baseUrl, signedPath, source, config.server), media)
        return true
    }

    private fun end(reason: EndReason) {
        val call = _activeCall.value ?: return
        val current = runtime
        val config = call.config
        current?.let {
            it.ringTimeoutJob?.cancel()
            it.sessionJob?.cancel()
            it.session?.close()
            it.session = null
            unregisterScreenOffReceiver(it)
            closePreview(it)
            if (it.audioAcquired) audioRouter.release()
        }
        if (reason == EndReason.Missed) {
            post(config.tag, notifications.buildMissed(config))
        } else {
            NotificationManagerCompat.from(context).cancel(config.tag, config.tag.hashCode())
        }
        runtime = null
        _activeCall.value = null
        context.stopService(Intent(context, CallScreenService::class.java))
    }

    private fun fireEvent(config: CallScreenConfig, actionKey: String) {
        scope.launch {
            try {
                serverManager.integrationRepository(config.serverId)
                    .fireEvent(EVENT_NOTIFICATION_ACTION, config.eventData + (EVENT_ACTION_KEY to actionKey))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Unable to fire call screen event for action=$actionKey")
                _events.trySend(CallScreenEvent.Error(commonR.string.event_error))
            }
        }
    }

    private fun updateCall(callId: Long, transform: (ActiveCall) -> ActiveCall) {
        _activeCall.update { if (it?.config?.callId == callId) transform(it) else it }
    }

    private fun roleKey(config: CallScreenConfig, role: CallActionRole) = config.buttons.first { it.role == role }.key

    private fun hasMicrophonePermission() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun buildRingingIntents(config: CallScreenConfig) = RingingIntents(
        answer = activityIntent(config, autoAnswer = true, requestCode = REQUEST_ANSWER),
        decline = receiverIntent(config, ACTION_DECLINE, REQUEST_DECLINE),
        fullScreen = activityIntent(config, autoAnswer = false, requestCode = REQUEST_FULL_SCREEN),
        custom = config.buttons
            .filter { it.role == CallActionRole.Custom }
            .associate { it.index to receiverIntent(config, ACTION_RUN, it.index, it.index) },
    )

    private fun activityIntent(config: CallScreenConfig, autoAnswer: Boolean, requestCode: Int): PendingIntent {
        val intent = Intent(context, CallScreenActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_CALL_ID, config.callId)
            .putExtra(EXTRA_TAG, config.tag)
            .putExtra(EXTRA_AUTO_ANSWER, autoAnswer)
        return PendingIntent.getActivity(
            context,
            requestCodeOf(config, requestCode),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun receiverIntent(
        config: CallScreenConfig,
        action: String,
        requestCode: Int,
        actionIndex: Int = 0,
    ): PendingIntent {
        val intent = Intent(context, CallScreenReceiver::class.java)
            .setAction(action)
            .putExtra(EXTRA_CALL_ID, config.callId)
            .putExtra(EXTRA_TAG, config.tag)
            .putExtra(EXTRA_ACTION_INDEX, actionIndex)
        return PendingIntent.getBroadcast(
            context,
            requestCodeOf(config, requestCode),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun requestCodeOf(config: CallScreenConfig, slot: Int) = Objects.hash(config.callId, slot)
}
