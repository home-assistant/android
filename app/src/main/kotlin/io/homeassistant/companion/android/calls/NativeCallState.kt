package io.homeassistant.companion.android.calls

import io.homeassistant.companion.android.common.data.call.NativeCallInvitation
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal sealed interface NativeCallPhase {
    data object Ringing : NativeCallPhase
    data object Connecting : NativeCallPhase
    data object Active : NativeCallPhase
}

internal data class NativeCallState(
    val invitation: NativeCallInvitation,
    val caller: String,
    val phase: NativeCallPhase,
)

internal sealed interface NativeCallCommand {
    val invitation: NativeCallInvitation
    data class Answer(override val invitation: NativeCallInvitation) : NativeCallCommand
    data class End(override val invitation: NativeCallInvitation) : NativeCallCommand
    data class Cancel(override val invitation: NativeCallInvitation) : NativeCallCommand
}

/** Main-thread UI mailbox; the foreground service owns the active call. */
@Singleton
internal class NativeCallStateRepository @Inject constructor() {
    private val mutableState = MutableStateFlow<NativeCallState?>(null)
    val state = mutableState.asStateFlow()
    val commands = Channel<NativeCallCommand>(4)

    fun start(state: NativeCallState): Boolean = mutableState.compareAndSet(null, state)

    fun publish(state: NativeCallState) {
        mutableState.update { current -> if (current?.invitation == state.invitation) state else current }
    }

    fun finish(invitation: NativeCallInvitation) {
        mutableState.update { current -> if (current?.invitation == invitation) null else current }
    }

    fun send(command: NativeCallCommand): Boolean =
        state.value?.invitation == command.invitation && commands.trySend(command).isSuccess
}
