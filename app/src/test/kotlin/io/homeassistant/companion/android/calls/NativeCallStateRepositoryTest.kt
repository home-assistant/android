package io.homeassistant.companion.android.calls

import io.homeassistant.companion.android.common.data.call.NativeCallInvitation
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NativeCallStateRepositoryTest {
    @Test
    fun `Given repeated answers when starting then only one attempt owns the transition`() {
        val state = NativeCallStateRepository()
        val token = NativeCallInvitation(1, "call", "/api/call?generation=1")
        state.start(NativeCallState(token, "Peer", NativeCallPhase.Ringing))
        assertFalse(state.beginAnswer(token.copy(serverId = 2)))
        assertTrue(state.beginAnswer(token))
        assertFalse(state.beginAnswer(token))
        assertEquals(NativeCallPhase.Connecting, state.state.value?.phase)
        state.send(NativeCallCommand.Disconnected(token))
        assertFalse(state.publish(NativeCallState(token, "Late answer", NativeCallPhase.Active)))
        state.finish(token)
        val next = token.copy(path = "/api/call?generation=2")
        assertTrue(state.start(NativeCallState(next, "Peer", NativeCallPhase.Ringing)))
        assertTrue(state.beginAnswer(next))
    }

    @Test
    fun `Given another generation or server when answering then leave the current call alone`() {
        val repository = NativeCallStateRepository()
        val current = NativeCallInvitation(1, "call", "/api/call?generation=2")
        repository.start(NativeCallState(current, "Door", NativeCallPhase.Ringing))
        assertFalse(repository.send(NativeCallCommand.Answer(current.copy(serverId = 2))))
        assertFalse(repository.send(NativeCallCommand.End(current.copy(path = "/api/call?generation=1"))))
        assertTrue(repository.commands.tryReceive().isFailure)
        assertTrue(repository.send(NativeCallCommand.Answer(current)))
    }

    @Test
    fun `Given a finished call when a late button is pressed then ignore it`() {
        val repository = NativeCallStateRepository()
        assertFalse(repository.send(NativeCallCommand.Answer(NativeCallInvitation(1, "call", "/api/call"))))
    }

    @Test
    fun `Given a reserved call when another invitation arrives then only its owner can clear it`() {
        val repository = NativeCallStateRepository()
        val first = NativeCallInvitation(1, "call", "/api/call?generation=1")
        val next = first.copy(path = "/api/call?generation=2")
        assertTrue(repository.start(NativeCallState(first, "Door", NativeCallPhase.Ringing)))
        assertFalse(repository.start(NativeCallState(next, "Door", NativeCallPhase.Ringing)))
        repository.finish(first)
        assertTrue(repository.start(NativeCallState(next, "Door", NativeCallPhase.Ringing)))
        repository.finish(first)
        repository.publish(NativeCallState(first, "Door", NativeCallPhase.Active))
        assertTrue(repository.state.value?.invitation == next)
        assertTrue(repository.send(NativeCallCommand.Answer(next)))
    }

    @Test
    fun `Given a full command queue when terminating then retain termination and reject further commands`() {
        val invitation = NativeCallInvitation(1, "call", "/api/call?generation=1")
        for (terminal in listOf(NativeCallCommand.End(invitation), NativeCallCommand.Cancel(invitation))) {
            val repository = NativeCallStateRepository()
            repository.start(NativeCallState(invitation, "Peer", NativeCallPhase.Active))
            repeat(4) { assertTrue(repository.send(NativeCallCommand.Answer(invitation))) }
            assertTrue(repository.send(terminal))
            assertFalse(repository.send(NativeCallCommand.Answer(invitation)))
        }
    }

    @Test
    fun `Given a cancelled validation when its reply arrives then it cannot become ringing`() = runTest {
        val repository = NativeCallStateRepository()
        val first = NativeCallInvitation(1, "call", "/api/call?generation=1")
        val next = first.copy(path = "/api/call?generation=2")
        assertTrue(repository.start(NativeCallState(first, "", NativeCallPhase.Validating)))
        assertTrue(repository.send(NativeCallCommand.Cancel(first)))
        assertFalse(repository.publish(NativeCallState(first, "Late caller", NativeCallPhase.Ringing)))
        assertEquals(NativeCallCommand.Cancel(first), repository.nextCommand())
        repository.finish(first)
        assertTrue(repository.start(NativeCallState(next, "Peer", NativeCallPhase.Ringing)))
        assertFalse(repository.send(NativeCallCommand.End(first)))
        assertTrue(repository.isCurrent(next))
    }

    @Test
    fun `Given competing terminal intents when the queue is full then the first intent wins`() = runTest {
        val repository = NativeCallStateRepository()
        val invitation = NativeCallInvitation(1, "call", "/api/call?generation=1")
        repository.start(NativeCallState(invitation, "Peer", NativeCallPhase.Active))
        repeat(4) { assertTrue(repository.send(NativeCallCommand.Answer(invitation))) }
        assertTrue(repository.send(NativeCallCommand.End(invitation)))
        assertTrue(repository.send(NativeCallCommand.Cancel(invitation)))
        assertEquals(NativeCallCommand.End(invitation), repository.nextCommand())
        assertFalse(repository.publish(NativeCallState(invitation, "Late update", NativeCallPhase.Active)))
    }
}
