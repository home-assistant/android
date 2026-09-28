package io.homeassistant.companion.android.calls

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.telecom.DisconnectCause
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.telecom.CallAttributesCompat
import androidx.core.telecom.CallControlResult
import androidx.core.telecom.CallControlScope
import androidx.core.telecom.CallException
import androidx.core.telecom.CallsManager
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.data.call.NativeCallDescription
import io.homeassistant.companion.android.common.data.call.NativeCallInvitation
import io.homeassistant.companion.android.common.data.call.NativeCallIoDispatcher
import io.homeassistant.companion.android.common.data.call.NativeCallRepository
import io.homeassistant.companion.android.common.util.SdkVersion
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

private const val CLEANUP_TIMEOUT_MS = 3000L

private const val EXTRA_SERVER = "call_server"
private const val EXTRA_ID = "call_id"
private const val EXTRA_PATH = "call_path"
private const val EXTRA_CALLER = "call_caller"
private const val EXTRA_REMAINING = "call_remaining_ms"
private const val ACTION_END = "native_call_end"
private const val ACTION_ANSWER = "native_call_answer"

/** One foreground lifetime for the native presentation and PCM adapter. */
@RequiresApi(Build.VERSION_CODES.O)
@AndroidEntryPoint
internal class NativeCallService : LifecycleService() {
    @Inject lateinit var repository: NativeCallRepository

    @Inject lateinit var state: NativeCallStateRepository

    @Inject lateinit var audio: NativeCallAudio

    @Inject @NativeCallIoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher
    private var session: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val invitation = intent?.let {
            NativeCallInvitation(
                it.getIntExtra(EXTRA_SERVER, -1),
                it.getStringExtra(EXTRA_ID).orEmpty(),
                it.getStringExtra(EXTRA_PATH).orEmpty(),
            )
        } ?: return START_NOT_STICKY
        when {
            intent.action == ACTION_END || intent.action == ACTION_ANSWER -> {
                if (session?.isActive == true) {
                    state.send(
                        if (intent.action == ACTION_ANSWER) {
                            NativeCallCommand.Answer(invitation)
                        } else {
                            NativeCallCommand.End(invitation)
                        },
                    )
                } else {
                    stopSelf(startId)
                }
            }
            session?.isActive != true -> {
                val incoming =
                    NativeCallState(
                        invitation,
                        intent.getStringExtra(EXTRA_CALLER).orEmpty(),
                        NativeCallPhase.Ringing,
                    )
                if (publish(incoming)) {
                    session =
                        lifecycleScope.launch { runCall(incoming, intent.getLongExtra(EXTRA_REMAINING, 0)) }
                } else {
                    state.finish(invitation)
                    stopSelf(startId)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun publish(value: NativeCallState): Boolean {
        if (!state.publish(value)) return false
        val notification = callNotification(this, value)
        if (SdkVersion.isAtLeast(Build.VERSION_CODES.Q)) {
            val types = ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL or
                if (value.phase == NativeCallPhase.Active &&
                    SdkVersion.isAtLeast(Build.VERSION_CODES.R)
                ) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                } else {
                    0
                }
            startForeground(CALL_NOTIFICATION_ID, notification, types)
        } else {
            startForeground(CALL_NOTIFICATION_ID, notification)
        }
        return true
    }

    private suspend fun runCall(incoming: NativeCallState, remainingMs: Long) {
        val invitation = incoming.invitation
        try {
            runCallSession(incoming, remainingMs)
        } catch (error: CallException) {
            Timber.e(error, "Telecom rejected call for server %d with code %d", invitation.serverId, error.code)
        } catch (error: IOException) {
            Timber.e(error, "Failed native call transport for server %d", invitation.serverId)
        } catch (error: IllegalArgumentException) {
            Timber.e(error, "Invalid native call data for server %d", invitation.serverId)
        } catch (error: CancellationException) {
            throw error
        } catch (error: IllegalStateException) {
            Timber.e(error, "Failed native call state for server %d", invitation.serverId)
        } finally {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            state.finish(invitation)
        }
    }

    private suspend fun runCallSession(incoming: NativeCallState, remainingMs: Long) = coroutineScope {
        val callScope = this
        val workers = mutableListOf<Job>()
        val invitation = incoming.invitation
        val clientId = repository.mediaClientId
        val calls = CallsManager(applicationContext)
        var control: CallControlScope? = null
        var answered = false
        var ringtone: Ringtone? = null
        val terminalAction = { if (answered) "hangup" else "decline" }
        suspend fun connectMedia(description: NativeCallDescription) {
            if (answered) return
            val mediaPath = activateMedia(incoming, description) ?: return
            answered = true
            ringtone?.stop()
            workers += callScope.launch {
                try {
                    audio.run(invitation.serverId, mediaPath)
                } finally {
                    control?.disconnect(DisconnectCause(DisconnectCause.REMOTE))
                }
            }
        }
        suspend fun answer() {
            if (!publish(incoming.copy(phase = NativeCallPhase.Connecting))) return
            connectMedia(repository.request(invitation, "answer", clientId))
        }
        val callbacks = NativeCallCallbacks(
            onAnswer = { answer() },
            onDisconnect = { repository.request(invitation, terminalAction(), clientId) },
        )

        try {
            ringtone = preparePresentation(calls)
            calls.addCall(
                callAttributes(incoming),
                onAnswer = { callbacks.answer() },
                onDisconnect = { callbacks.disconnect() },
                onSetActive = {},
                onSetInactive = { throw IllegalStateException("Hold is not advertised") },
            ) {
                control = this
                if (remainingMs > 0) {
                    workers += callScope.launch {
                        expireUnanswered(remainingMs, this@addCall) { answered }
                    }
                }
                val callControl = this
                workers += callScope.launch {
                    handleCommands(invitation, callControl, ::answer, terminalAction)
                }
            }
        } finally {
            callbacks.close()
            workers.forEach { it.cancel() }
            ringtone?.stop()
            finishProviderCall(invitation, terminalAction())
        }
    }

    private fun activateMedia(incoming: NativeCallState, description: NativeCallDescription): String? {
        if (!state.isCurrent(incoming.invitation)) return null
        val path = checkedMediaPath(description)
        return if (publish(incoming.copy(phase = NativeCallPhase.Active))) path else null
    }

    private suspend fun expireUnanswered(remainingMs: Long, control: CallControlScope, answered: () -> Boolean) {
        delay(remainingMs)
        if (!answered()) control.disconnect(DisconnectCause(DisconnectCause.MISSED))
    }

    private fun checkedMediaPath(description: NativeCallDescription): String {
        check(
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        ) {
            "Microphone permission is required to answer"
        }
        check(description.state == "in_call" && description.mediaPath != null) { "Call is no longer answerable" }
        return requireNotNull(description.mediaPath)
    }

    private suspend fun finishProviderCall(invitation: NativeCallInvitation, action: String) {
        withContext(NonCancellable) {
            withTimeoutOrNull(CLEANUP_TIMEOUT_MS) {
                try {
                    repository.request(invitation, action, repository.mediaClientId)
                } catch (error: IOException) {
                    Timber.d(error, "Call provider already unavailable during cleanup")
                }
            }
        }
    }

    private suspend fun preparePresentation(calls: CallsManager): Ringtone? = withContext(ioDispatcher) {
        calls.registerAppWithTelecom(CallsManager.CAPABILITY_BASELINE)
        RingtoneManager.getRingtone(
            this@NativeCallService,
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
        )
            ?.also { tone ->
                if (SdkVersion.isAtLeast(Build.VERSION_CODES.P)) tone.isLooping = true
                tone.play()
            }
    }

    private suspend fun handleCommands(
        invitation: NativeCallInvitation,
        control: CallControlScope,
        answerCall: suspend () -> Unit,
        terminalAction: () -> String,
    ) {
        while (true) {
            val command = state.nextCommand()
            if (command.invitation != invitation) continue
            when (command) {
                is NativeCallCommand.Answer -> {
                    if (control.answer(
                            CallAttributesCompat.CALL_TYPE_AUDIO_CALL,
                        ) is CallControlResult.Success
                    ) {
                        answerCall()
                    }
                }
                is NativeCallCommand.End -> {
                    finishProviderCall(invitation, terminalAction())
                    control.disconnect(DisconnectCause(DisconnectCause.LOCAL))
                    return
                }
                is NativeCallCommand.Cancel -> {
                    control.disconnect(DisconnectCause(DisconnectCause.REMOTE))
                    return
                }
            }
        }
    }

    companion object {
        fun start(context: Context, invitation: NativeCallInvitation, description: NativeCallDescription) {
            ContextCompat.startForegroundService(
                context,
                baseIntent(context, invitation)
                    .putExtra(
                        EXTRA_CALLER,
                        description.caller,
                    ).putExtra(EXTRA_REMAINING, description.remainingMs ?: 0L),
            )
        }
        fun answerIntent(context: Context, invitation: NativeCallInvitation): Intent =
            baseIntent(context, invitation).setAction(ACTION_ANSWER)
        fun endIntent(context: Context, invitation: NativeCallInvitation): Intent =
            baseIntent(context, invitation).setAction(ACTION_END)
        private fun baseIntent(context: Context, invitation: NativeCallInvitation): Intent =
            Intent(context, NativeCallService::class.java)
                .setData(
                    Uri.Builder().scheme(
                        "homeassistant-call",
                    ).authority(
                        invitation.serverId.toString(),
                    ).appendPath(invitation.callId).appendQueryParameter("session", invitation.path).build(),
                )
                .putExtra(EXTRA_SERVER, invitation.serverId)
                .putExtra(EXTRA_ID, invitation.callId).putExtra(EXTRA_PATH, invitation.path)
    }
}

@RequiresApi(Build.VERSION_CODES.O)
private fun callAttributes(incoming: NativeCallState): CallAttributesCompat = CallAttributesCompat(
    incoming.caller,
    Uri.fromParts("homeassistant-call", incoming.invitation.callId, null),
    CallAttributesCompat.DIRECTION_INCOMING,
    callCapabilities = 0,
)
