package io.homeassistant.companion.android.calls

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.common.data.call.NativeCallDescription
import io.homeassistant.companion.android.common.data.call.NativeCallInvitation
import io.homeassistant.companion.android.common.data.call.NativeCallRepository
import io.homeassistant.companion.android.common.data.integration.NativeCallsSupport
import io.homeassistant.companion.android.common.util.SdkVersion
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Validates addressed invitations before starting native ringing. */
class NativeCallDispatcher @Inject internal constructor(
    @ApplicationContext private val context: Context,
    private val repository: NativeCallRepository,
    private val state: NativeCallStateRepository,
    @NativeCallsSupport private val supported: Boolean,
) {
    suspend fun receive(serverId: Int, data: Map<String, String>) {
        if (!supported || !SdkVersion.isAtLeast(Build.VERSION_CODES.O)) return
        val invitation = try {
            NativeCallInvitation(serverId, data["call_id"].orEmpty(), data["call_path"].orEmpty())
        } catch (error: IllegalArgumentException) {
            Timber.w(error, "Invalid native call invitation for server %d", serverId)
            return
        }
        when (data["call_action"]) {
            "cancel" -> state.send(NativeCallCommand.Cancel(invitation))
            "ring" -> if (state.state.value?.invitation != invitation) receiveInvitation(invitation)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun receiveInvitation(invitation: NativeCallInvitation): Boolean {
        val reserved = state.start(NativeCallState(invitation, "", NativeCallPhase.Validating))
        var started = false
        try {
            val description = repository.request(invitation)
            if (eligible(description)) {
                started = if (reserved) {
                    state.isCurrent(invitation) && startService(invitation, description)
                } else {
                    if (state.state.value?.invitation != invitation) {
                        repository.request(invitation, "decline")
                    }
                    false
                }
            }
        } catch (error: IOException) {
            Timber.e(error, "Failed to validate call for server %d", invitation.serverId)
        } catch (error: IllegalArgumentException) {
            Timber.w(error, "Invalid call description for server %d", invitation.serverId)
        } finally {
            if (reserved && !started) releaseReservation(invitation)
        }
        return started
    }

    private suspend fun releaseReservation(invitation: NativeCallInvitation) {
        try {
            if (state.state.value?.termination is NativeCallCommand.End) {
                withContext(NonCancellable) {
                    repository.request(invitation, "decline")
                }
            }
        } catch (error: IOException) {
            Timber.d(error, "Call provider unavailable while releasing pending call")
        } finally {
            state.finish(invitation)
        }
    }

    private fun eligible(description: NativeCallDescription): Boolean =
        description.state == "ringing" && (description.remainingMs?.let { it > 0 } != false)

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun startService(invitation: NativeCallInvitation, description: NativeCallDescription): Boolean {
        val phase = NativeCallPhase.Ringing
        val terminalAction = "decline"
        if (!state.publish(NativeCallState(invitation, description.caller, phase))) return false
        val started = try {
            NativeCallService.start(context, invitation, description)
            true
        } catch (error: IllegalStateException) {
            Timber.e(error, "Failed to start call service for server %d", invitation.serverId)
            false
        } catch (error: SecurityException) {
            Timber.e(error, "Call service permission denied for server %d", invitation.serverId)
            false
        }
        if (!started) {
            repository.request(invitation, terminalAction)
        }
        return started
    }
}
