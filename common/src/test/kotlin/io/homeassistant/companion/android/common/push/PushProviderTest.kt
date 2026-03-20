package io.homeassistant.companion.android.common.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PushProviderTest {

    @Test
    fun `PushProvider default requiresPersistentConnection is false`() {
        val provider = object : PushProvider {
            override val name = "Test"
            override val priority = 0
            override suspend fun isAvailable() = true
            override suspend fun isActive() = false
            override suspend fun register() = null
            override suspend fun unregister() {}
        }
        assertFalse(provider.requiresPersistentConnection)
    }

    @Test
    fun `PushProvider can override requiresPersistentConnection`() {
        val provider = object : PushProvider {
            override val name = "WebSocket"
            override val priority = 30
            override val requiresPersistentConnection = true
            override suspend fun isAvailable() = true
            override suspend fun isActive() = false
            override suspend fun register() = null
            override suspend fun unregister() {}
        }
        assertTrue(provider.requiresPersistentConnection)
    }

    @Test
    fun `PushRegistrationResult preserves all fields`() {
        val result = PushRegistrationResult(
            pushToken = "auth:pubkey",
            pushUrl = "https://ntfy.example.com/up123",
            encrypt = true
        )
        assertEquals("auth:pubkey", result.pushToken)
        assertEquals("https://ntfy.example.com/up123", result.pushUrl)
        assertTrue(result.encrypt)
    }

    @Test
    fun `PushRegistrationResult with empty token and no url`() {
        val result = PushRegistrationResult(pushToken = "")
        assertEquals("", result.pushToken)
        assertNull(result.pushUrl)
        assertFalse(result.encrypt)
    }

    @Test
    fun `PushRegistrationResult equals and hashCode work correctly`() {
        val result1 = PushRegistrationResult("token", "url", true)
        val result2 = PushRegistrationResult("token", "url", true)
        val result3 = PushRegistrationResult("other", "url", true)

        assertEquals(result1, result2)
        assertEquals(result1.hashCode(), result2.hashCode())
        assertFalse(result1 == result3)
    }

    @Test
    fun `PushRegistrationResult copy works correctly`() {
        val original = PushRegistrationResult("token", "url", true)
        val copied = original.copy(pushToken = "new-token")

        assertEquals("new-token", copied.pushToken)
        assertEquals("url", copied.pushUrl)
        assertTrue(copied.encrypt)
    }
}
