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
import java.io.IOException
import java.util.UUID
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

private const val MAX_RINGING_MS = 120_000L
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
                    NativeCallState(invitation, intent.getStringExtra(EXTRA_CALLER).orEmpty(), NativeCallPhase.Ringing)
                publish(incoming)
                session = lifecycleScope.launch { runCall(incoming, intent.getLongExtra(EXTRA_REMAINING, 0)) }
            }
        }
        return START_NOT_STICKY
    }

    private fun publish(value: NativeCallState) {
        state.publish(value)
        val notification = callNotification(this, value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val types = ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL or
                if (value.phase == NativeCallPhase.Active &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                ) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                } else {
                    0
                }
            startForeground(CALL_NOTIFICATION_ID, notification, types)
        } else {
            startForeground(CALL_NOTIFICATION_ID, notification)
        }
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
            while (state.commands.tryReceive().isSuccess) { /* Release commands owned by the completed call. */ }
            state.finish(invitation)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private suspend fun runCallSession(incoming: NativeCallState, remainingMs: Long) = coroutineScope {
        val callScope = this
        val workers = mutableListOf<Job>()
        val invitation = incoming.invitation
        val clientId = UUID.randomUUID().toString()
        val calls = CallsManager(this@NativeCallService)
        var control: CallControlScope? = null
        var answered = false
        var ringtone: Ringtone? = null
        suspend fun answer() {
            if (answered) return
            check(
                ContextCompat.checkSelfPermission(this@NativeCallService, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED,
            ) {
                "Microphone permission is required to answer"
            }
            publish(incoming.copy(phase = NativeCallPhase.Connecting))
            val description = repository.request(invitation, "answer", clientId)
            check(description.state == "in_call" && description.mediaPath != null) { "Call is no longer answerable" }
            val mediaPath = requireNotNull(description.mediaPath)
            answered = true
            ringtone?.stop()
            publish(incoming.copy(phase = NativeCallPhase.Active))
            workers += callScope.launch {
                try {
                    audio.run(invitation.serverId, mediaPath)
                } finally {
                    control?.disconnect(DisconnectCause(DisconnectCause.REMOTE))
                }
            }
        }
        try {
            ringtone = startRinging(calls)
            calls.addCall(
                CallAttributesCompat(
                    incoming.caller,
                    Uri.fromParts("homeassistant-call", invitation.callId, null),
                    CallAttributesCompat.DIRECTION_INCOMING,
                    callCapabilities = 0,
                ),
                onAnswer = { answer() },
                onDisconnect = { repository.request(invitation, if (answered) "hangup" else "decline", clientId) },
                onSetActive = {},
                onSetInactive = { throw IllegalStateException("Hold is not advertised") },
            ) {
                control = this
                workers += callScope.launch {
                    delay(remainingMs.coerceIn(1, MAX_RINGING_MS))
                    if (!answered) disconnect(DisconnectCause(DisconnectCause.MISSED))
                }
                val callControl = this
                workers += callScope.launch {
                    handleCommands(invitation, clientId, callControl, ::answer) {
                        if (answered) "hangup" else "decline"
                    }
                }
            }
        } finally {
            workers.forEach { it.cancel() }
            ringtone?.stop()
            withContext(NonCancellable) {
                withTimeoutOrNull(CLEANUP_TIMEOUT_MS) {
                    try {
                        repository.request(invitation, if (answered) "hangup" else "decline", clientId)
                    } catch (
                        error: IOException,
                    ) {
                        Timber.d(error, "Call provider already unavailable during cleanup")
                    }
                }
            }
        }
    }

    private suspend fun startRinging(calls: CallsManager): Ringtone? = withContext(ioDispatcher) {
        calls.registerAppWithTelecom(CallsManager.CAPABILITY_BASELINE)
        RingtoneManager.getRingtone(
            this@NativeCallService,
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
        )
            ?.also { tone ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) tone.isLooping = true
                tone.play()
            }
    }

    private suspend fun handleCommands(
        invitation: NativeCallInvitation,
        clientId: String,
        control: CallControlScope,
        answerCall: suspend () -> Unit,
        terminalAction: () -> String,
    ) {
        for (command in state.commands) {
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
                    repository.request(invitation, terminalAction(), clientId)
                    control.disconnect(DisconnectCause(DisconnectCause.LOCAL))
                }
                is NativeCallCommand.Cancel -> control.disconnect(DisconnectCause(DisconnectCause.REMOTE))
            }
        }
    }

    companion object {
        fun start(context: Context, invitation: NativeCallInvitation, description: NativeCallDescription) {
            ContextCompat.startForegroundService(
                context,
                baseIntent(context, invitation)
                    .putExtra(EXTRA_CALLER, description.caller).putExtra(EXTRA_REMAINING, description.remainingMs),
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
