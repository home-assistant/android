package io.homeassistant.companion.android.frontend.externalbus.incoming

import io.homeassistant.companion.android.frontend.externalbus.frontendExternalBusJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IncomingExternalBusMessageTest {

    @Test
    fun `Given connection-status JSON then parses to ConnectionStatusMessage`() {
        val json = """{"type":"connection-status","id":1,"payload":{"event":"connected"}}"""

        val message = frontendExternalBusJson.decodeFromString<IncomingExternalBusMessage>(json)

        assertInstanceOf(ConnectionStatusMessage::class.java, message)
        val statusMessage = message as ConnectionStatusMessage
        assertEquals(1, statusMessage.id)
        assertEquals("connected", statusMessage.payload.event)
        assertTrue(statusMessage.payload.isConnected)
    }

    @Test
    fun `Given config-get JSON then parses to ConfigGetMessage`() {
        val json = """{"type":"config/get","id":42}"""

        val message = frontendExternalBusJson.decodeFromString<IncomingExternalBusMessage>(json)

        assertInstanceOf(ConfigGetMessage::class.java, message)
        assertEquals(42, (message as ConfigGetMessage).id)
    }

    @Test
    fun `Given theme-update JSON then parses to ThemeUpdateMessage`() {
        val json = """{"type":"theme-update","id":5}"""

        val message = frontendExternalBusJson.decodeFromString<IncomingExternalBusMessage>(json)

        assertInstanceOf(ThemeUpdateMessage::class.java, message)
        assertEquals(5, (message as ThemeUpdateMessage).id)
    }

    @Test
    fun `Given config_screen-show JSON then parses to OpenSettingsMessage`() {
        val json = """{"type":"config_screen/show","id":5}"""

        val message = frontendExternalBusJson.decodeFromString<IncomingExternalBusMessage>(json)

        assertInstanceOf(OpenSettingsMessage::class.java, message)
        assertEquals(5, (message as OpenSettingsMessage).id)
    }

    @Test
    fun `Given assist-show JSON then parses to OpenAssistMessage with payload`() {
        val json = """{"type":"assist/show","id":7,"payload":{"pipeline_id":"abc","start_listening":false}}"""

        val message = frontendExternalBusJson.decodeFromString<IncomingExternalBusMessage>(json)

        assertInstanceOf(OpenAssistMessage::class.java, message)
        val assistMessage = message as OpenAssistMessage
        assertEquals(7, assistMessage.id)
        assertEquals("abc", assistMessage.payload.pipelineId)
        assertFalse(assistMessage.payload.startListening)
    }

    @Test
    fun `Given assist-show JSON without payload then parses to OpenAssistMessage with defaults`() {
        val json = """{"type":"assist/show","id":8}"""

        val message = frontendExternalBusJson.decodeFromString<IncomingExternalBusMessage>(json)

        assertInstanceOf(OpenAssistMessage::class.java, message)
        val assistMessage = message as OpenAssistMessage
        assertEquals(8, assistMessage.id)
        assertNull(assistMessage.payload.pipelineId)
        assertTrue(assistMessage.payload.startListening)
    }

    @Test
    fun `Given unknown type JSON then parses to UnknownIncomingMessage`() {
        val json = """{"type":"future-feature","id":99,"payload":{"data":"something"}}"""

        val message = frontendExternalBusJson.decodeFromString<IncomingExternalBusMessage>(json)

        assertInstanceOf(UnknownIncomingMessage::class.java, message)
        val unknownMessage = message as UnknownIncomingMessage
        assertTrue(unknownMessage.content.toString().contains("future-feature"))
    }
}
