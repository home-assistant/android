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
    fun `Given assist-settings JSON then parses to OpenAssistSettingsMessage`() {
        val json = """{"type":"assist/settings","id":5}"""

        val message = frontendExternalBusJson.decodeFromString<IncomingExternalBusMessage>(json)

        assertInstanceOf(OpenAssistSettingsMessage::class.java, message)
        assertEquals(5, (message as OpenAssistSettingsMessage).id)
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
    fun `Given handleBlob JSON then parses to HandleBlobMessage`() {
        val json = """{"type":"handleBlob","id":10,"data":"data:application/pdf;base64,abc","filename":"file.pdf"}"""

        val message = frontendExternalBusJson.decodeFromString<IncomingExternalBusMessage>(json)

        assertInstanceOf(HandleBlobMessage::class.java, message)
        val blobMessage = message as HandleBlobMessage
        assertEquals(10, blobMessage.id)
        assertEquals("data:application/pdf;base64,abc", blobMessage.data)
        assertEquals("file.pdf", blobMessage.filename)
    }

    @Test
    fun `Given tag-write JSON with tag then parses to TagWriteMessage with tag`() {
        val json = """{"type":"tag/write","id":11,"payload":{"tag":"abc-123"}}"""

        val message = frontendExternalBusJson.decodeFromString<IncomingExternalBusMessage>(json)

        assertInstanceOf(TagWriteMessage::class.java, message)
        val tagMessage = message as TagWriteMessage
        assertEquals(11, tagMessage.id)
        assertEquals("abc-123", tagMessage.payload.tag)
    }

    @Test
    fun `Given tag-write JSON without payload then parses to TagWriteMessage with null tag`() {
        val json = """{"type":"tag/write","id":12}"""

        val message = frontendExternalBusJson.decodeFromString<IncomingExternalBusMessage>(json)

        assertInstanceOf(TagWriteMessage::class.java, message)
        val tagMessage = message as TagWriteMessage
        assertEquals(12, tagMessage.id)
        assertNull(tagMessage.payload.tag)
    }

    @Test
    fun `Given unknown type JSON then parses to UnknownIncomingMessage`() {
        val json = """{"type":"future-feature","id":99,"payload":{"data":"something"}}"""

        val message = frontendExternalBusJson.decodeFromString<IncomingExternalBusMessage>(json)

        assertInstanceOf(UnknownIncomingMessage::class.java, message)
        val unknownMessage = message as UnknownIncomingMessage
        assertTrue(unknownMessage.content.toString().contains("future-feature"))
    }

    @Test
    fun `Given exoplayer play_hls JSON with full payload then parses to ExoPlayerPlayHlsMessage`() {
        val json =
            """{"type":"exoplayer/play_hls","id":20,"payload":{"url":"https://example.com/stream.m3u8","muted":true}}"""

        val message = frontendExternalBusJson.decodeFromString<IncomingExternalBusMessage>(json)

        assertInstanceOf(ExoPlayerPlayHlsMessage::class.java, message)
        val playHls = message as ExoPlayerPlayHlsMessage
        assertEquals(20, playHls.id)
        assertEquals("https://example.com/stream.m3u8", playHls.payload.url)
        assertTrue(playHls.payload.muted)
    }

    @Test
    fun `Given exoplayer play_hls JSON without muted then parses with muted defaulting to false`() {
        val json =
            """{"type":"exoplayer/play_hls","id":21,"payload":{"url":"https://example.com/stream.m3u8"}}"""

        val message = frontendExternalBusJson.decodeFromString<IncomingExternalBusMessage>(json)

        assertInstanceOf(ExoPlayerPlayHlsMessage::class.java, message)
        val playHls = message as ExoPlayerPlayHlsMessage
        assertEquals("https://example.com/stream.m3u8", playHls.payload.url)
        assertFalse(playHls.payload.muted)
    }

    @Test
    fun `Given exoplayer play_hls JSON without payload then parses with default payload`() {
        val json = """{"type":"exoplayer/play_hls","id":22}"""

        val message = frontendExternalBusJson.decodeFromString<IncomingExternalBusMessage>(json)

        assertInstanceOf(ExoPlayerPlayHlsMessage::class.java, message)
        val playHls = message as ExoPlayerPlayHlsMessage
        assertEquals(22, playHls.id)
        assertNull(playHls.payload.url)
        assertFalse(playHls.payload.muted)
    }

    @Test
    fun `Given exoplayer stop JSON then parses to ExoPlayerStopMessage`() {
        val json = """{"type":"exoplayer/stop","id":23}"""

        val message = frontendExternalBusJson.decodeFromString<IncomingExternalBusMessage>(json)

        assertInstanceOf(ExoPlayerStopMessage::class.java, message)
        assertEquals(23, (message as ExoPlayerStopMessage).id)
    }

    @Test
    fun `Given exoplayer resize JSON with fractional pixels then parses payload as floats`() {
        val json = """{"type":"exoplayer/resize","id":24,""" +
            """"payload":{"left":0,"top":10.5,"right":486.25,"bottom":200.5}}"""

        val message = frontendExternalBusJson.decodeFromString<IncomingExternalBusMessage>(json)

        assertInstanceOf(ExoPlayerResizeMessage::class.java, message)
        val resize = message as ExoPlayerResizeMessage
        assertEquals(24, resize.id)
        assertEquals(0.0, resize.payload.left)
        assertEquals(10.5, resize.payload.top)
        assertEquals(486.25, resize.payload.right)
        assertEquals(200.5, resize.payload.bottom)
    }

    @Test
    fun `Given improv scan JSON then parses to ImprovScanMessage`() {
        val json = """{"type":"improv/scan","id":50}"""

        val message = frontendExternalBusJson.decodeFromString<IncomingExternalBusMessage>(json)

        assertInstanceOf(ImprovScanMessage::class.java, message)
        assertEquals(50, (message as ImprovScanMessage).id)
    }

    @Test
    fun `Given improv scan JSON without id then parses to ImprovScanMessage with null id`() {
        val json = """{"type":"improv/scan"}"""

        val message = frontendExternalBusJson.decodeFromString<IncomingExternalBusMessage>(json)

        assertInstanceOf(ImprovScanMessage::class.java, message)
        assertNull((message as ImprovScanMessage).id)
    }

    @Test
    fun `Given improv configure_device JSON then parses to ImprovConfigureDeviceMessage with name`() {
        val json = """{"type":"improv/configure_device","id":51,"payload":{"name":"Smart Plug"}}"""

        val message = frontendExternalBusJson.decodeFromString<IncomingExternalBusMessage>(json)

        assertInstanceOf(ImprovConfigureDeviceMessage::class.java, message)
        val configureMessage = message as ImprovConfigureDeviceMessage
        assertEquals(51, configureMessage.id)
        assertEquals("Smart Plug", configureMessage.payload.name)
    }

    @Test
    fun `Given exoplayer resize JSON without payload then parses with zero defaults`() {
        val json = """{"type":"exoplayer/resize","id":25}"""

        val message = frontendExternalBusJson.decodeFromString<IncomingExternalBusMessage>(json)

        assertInstanceOf(ExoPlayerResizeMessage::class.java, message)
        val resize = message as ExoPlayerResizeMessage
        assertEquals(0.0, resize.payload.left)
        assertEquals(0.0, resize.payload.top)
        assertEquals(0.0, resize.payload.right)
        assertEquals(0.0, resize.payload.bottom)
    }
}
