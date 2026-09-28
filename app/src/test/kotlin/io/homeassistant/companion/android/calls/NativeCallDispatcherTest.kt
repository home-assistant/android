package io.homeassistant.companion.android.calls

import android.content.Context
import io.homeassistant.companion.android.common.data.call.NativeCallDescription
import io.homeassistant.companion.android.common.data.call.NativeCallInvitation
import io.homeassistant.companion.android.common.data.call.NativeCallRepository
import io.homeassistant.companion.android.common.util.SdkVersion
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class NativeCallDispatcherTest {
    @Test
    fun `Given cancellation during validation when the old response arrives then do not start a service`() = runTest {
        SdkVersion.sdkInt = 34
        val context = mockk<Context>(relaxed = true)
        val repository = mockk<NativeCallRepository>()
        val state = NativeCallStateRepository()
        val token = NativeCallInvitation(1, "pending", "/api/provider/call?generation=1")
        val description = CompletableDeferred<NativeCallDescription>()
        coEvery { repository.request(token) } coAnswers { description.await() }
        val dispatcher = NativeCallDispatcher(context, repository, state, true)
        val data = mapOf("call_id" to token.callId, "call_path" to token.path, "call_action" to "ring")
        val validation = async { dispatcher.receive(1, data) }
        runCurrent()
        assertEquals(NativeCallPhase.Validating, state.state.value?.phase)
        dispatcher.receive(1, data)
        dispatcher.receive(1, data + ("call_action" to "cancel"))
        description.complete(NativeCallDescription(token.callId, "ringing", "Peer"))
        validation.await()
        assertNull(state.state.value)
        coVerify(exactly = 1) { repository.request(token) }
        verify(exactly = 0) { context.startService(any()) }
        verify(exactly = 0) { context.startForegroundService(any()) }
    }
}
