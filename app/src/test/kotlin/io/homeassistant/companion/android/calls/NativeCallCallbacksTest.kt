package io.homeassistant.companion.android.calls

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NativeCallCallbacksTest {
    @Test
    fun `Given a finished call when Telecom retains callbacks then late callbacks do nothing`() = runTest {
        val events = mutableListOf<String>()
        val callbacks = NativeCallCallbacks(
            onAnswer = { events += "answer" },
            onDisconnect = { events += "disconnect" },
        )
        callbacks.answer()
        callbacks.disconnect()
        assertEquals(listOf("answer", "disconnect"), events)
        callbacks.close()
        callbacks.close()
        callbacks.answer()
        callbacks.disconnect()
        assertEquals(listOf("answer", "disconnect"), events)
        val next = NativeCallCallbacks(onAnswer = { events += "next" }, onDisconnect = {})
        next.answer()
        callbacks.disconnect()
        assertEquals(listOf("answer", "disconnect", "next"), events)
    }
}
