package io.homeassistant.companion.android.calls

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.common.data.call.NativeCallInvitation
import io.homeassistant.companion.android.common.data.call.NativeCallRepository
import io.homeassistant.companion.android.common.data.integration.NativeCallsSupport
import java.io.IOException
import javax.inject.Inject
import timber.log.Timber

/** Validates addressed invitations before starting native ringing. */
class NativeCallDispatcher @Inject internal constructor(
    @ApplicationContext private val context: Context,
    private val repository: NativeCallRepository,
    private val state: NativeCallStateRepository,
    @NativeCallsSupport private val supported: Boolean,
) {
    suspend fun receive(serverId: Int, data: Map<String, String>) {
        if (!supported || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
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

    private suspend fun receiveInvitation(invitation: NativeCallInvitation) {
        val serverId = invitation.serverId
        try {
            val description = repository.request(invitation)
            if (description.state != "ringing" || description.remainingMs <= 0) return
            if (!state.start(NativeCallState(invitation, description.caller, NativeCallPhase.Ringing))) {
                if (state.state.value?.invitation != invitation) repository.request(invitation, "decline")
                return
            }
            try {
                NativeCallService.start(context, invitation, description)
            } catch (error: IllegalStateException) {
                state.finish(invitation)
                Timber.e(error, "Failed to start call service for server %d", serverId)
                repository.request(invitation, "decline")
            } catch (error: SecurityException) {
                state.finish(invitation)
                Timber.e(error, "Call service permission denied for server %d", serverId)
                repository.request(invitation, "decline")
            }
        } catch (error: IOException) {
            Timber.e(error, "Failed to validate incoming call for server %d", serverId)
        } catch (error: IllegalArgumentException) {
            Timber.w(error, "Invalid call description for server %d", serverId)
        }
    }
}
