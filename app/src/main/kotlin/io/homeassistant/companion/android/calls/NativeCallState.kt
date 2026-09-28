package io.homeassistant.companion.android.calls

import io.homeassistant.companion.android.common.data.call.NativeCallInvitation
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.selects.select

internal sealed interface NativeCallPhase {
    data object Validating : NativeCallPhase
    data object Ringing : NativeCallPhase
    data object Connecting : NativeCallPhase
    data object Active : NativeCallPhase
}

internal data class NativeCallState(
    val invitation: NativeCallInvitation,
    val caller: String,
    val phase: NativeCallPhase,
    val termination: NativeCallCommand.Terminal? = null,
)

internal sealed interface NativeCallCommand {
    val invitation: NativeCallInvitation
    data class Answer(override val invitation: NativeCallInvitation) : NativeCallCommand
    sealed interface Terminal : NativeCallCommand
    data class End(override val invitation: NativeCallInvitation) : Terminal
    data class Cancel(override val invitation: NativeCallInvitation) : Terminal
    data class Disconnected(override val invitation: NativeCallInvitation) : Terminal
}

/** Call-scoped mailbox; terminal intent survives command backpressure. */
@Singleton
internal class NativeCallStateRepository @Inject constructor() {
    private val mutableState = MutableStateFlow<NativeCallState?>(null)
    val state = mutableState.asStateFlow()
    val commands = Channel<NativeCallCommand>(4)
    private val terminalWakeup = Channel<NativeCallCommand.Terminal>(Channel.CONFLATED)

    fun start(state: NativeCallState): Boolean = mutableState.compareAndSet(null, state)

    /** Reserve the answer attempt before any suspending provider request. */
    fun beginAnswer(invitation: NativeCallInvitation): Boolean {
        val current = mutableState.value ?: return false
        return current.invitation == invitation &&
            current.phase == NativeCallPhase.Ringing &&
            current.termination == null &&
            mutableState.compareAndSet(current, current.copy(phase = NativeCallPhase.Connecting))
    }

    fun publish(state: NativeCallState): Boolean = mutableState.updateAndGet { current ->
        if (current?.invitation == state.invitation && current.termination == null) state else current
    } == state

    fun isCurrent(invitation: NativeCallInvitation): Boolean =
        state.value?.let { it.invitation == invitation && it.termination == null } == true

    fun finish(invitation: NativeCallInvitation) {
        mutableState.update { current -> if (current?.invitation == invitation) null else current }
    }

    fun send(command: NativeCallCommand): Boolean = if (command is NativeCallCommand.Terminal) {
        terminate(command)
    } else {
        isCurrent(command.invitation) && commands.trySend(command).isSuccess
    }

    suspend fun nextCommand(): NativeCallCommand = state.value?.termination ?: select {
        terminalWakeup.onReceive { it }
        commands.onReceive { it }
    }

    private fun terminate(command: NativeCallCommand.Terminal): Boolean {
        val current = mutableState.updateAndGet { current ->
            if (current?.invitation == command.invitation && current.termination == null) {
                current.copy(termination = command)
            } else {
                current
            }
        }
        val terminal = current?.termination
        if (current?.invitation != command.invitation || terminal == null) return false
        terminalWakeup.trySend(terminal)
        return true
    }
}
