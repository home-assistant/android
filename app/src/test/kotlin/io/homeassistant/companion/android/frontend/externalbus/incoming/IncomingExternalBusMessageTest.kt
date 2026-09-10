package io.homeassistant.companion.android.frontend.externalbus.incoming

import io.homeassistant.companion.android.frontend.externalbus.frontendExternalBusJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull

class IncomingExternalBusMessageTest {

    @Test
    fun `Given connection-status JSON then parses to ConnectionStatusMessage`() {
        val json = """{"type":"connection-status","id":1,"payload":{"event":"connected"}}"""

        val statusMessage = assertInstanceOf(ConnectionStatusMessage::class.java, frontendExternalBusJson.decodeFromString<IncomingExternalBusMessage>(json))
        assertEquals(1, statusMessage.id)
        assertEquals("connected", statusMessage.payload.event)
        assertTrue(statusMessage.payload.isConnected)
    }

    @Test
    fun `Given frontend-loaded JSON then parses to FrontendLoaded`() {
        val json = """{"type":"frontend/loaded","id":2}"""

        val message = assertInstanceOf(FrontendLoaded::class.java, frontendExternalBusJson.decodeFromString<IncomingExternalBusMessage>(json))
        assertEquals(2, message.id)
    }

    @Test
    fun `Given frontend-loaded JSON without id then parses to FrontendLoaded with null id`() {
        val json = """{"type":"frontend/loaded"}"""

        val message = assertInstanceOf(FrontendLoaded::class.java, frontendExternalBusJson.decodeFromString<IncomingExternalBusMessage>(json))
        assertNull(message.id)
    }

    @Test
    fun `Given config-get JSON then parses to ConfigGetMessage`() {
        val json = """{"type":"config/get","id":42}"""

        val message = assertInstanceOf(ConfigGetMessage::class.java, frontendExternalBusJson.decodeFromString<IncomingExternalBusMessage>(json))
        assertEquals(42, message.id)
    }

    @Test
    fun `Given theme-update JSON then parses to ThemeUpdateMessage`() {
        val json = """{"type":"theme-update","id":5}"""

        val message = assertInstanceOf(ThemeUpdateMessage::class.java, frontendExternalBusJson.decodeFromString<IncomingExternalBusMessage>(json))
        assertEquals(5, message.id)
    }

    @Test
    fun `Given config_screen-show JSON then parses to OpenSettingsMessage`() {
        val json = """{"type":"config_screen/show","id":5}"""

        val message = assertInstanceOf(OpenSettingsMessage::class.java, frontendExternalBusJson.decodeFromString<IncomingExternalBusMessage>(json))
        assertEquals(5, message.id)
    }

    @Test
    fun `Given assist-settings JSON then parses to OpenAssistSettingsMessage`() {
        val json = """{"type":"assist/settings","id":5}"""

        val message = assertInstanceOf(OpenAssistSettingsMessage::class.java, frontendExternalBusJson.decodeFromString<IncomingExternalBusMessage>(json))
        assertEquals(5, message.id)
    }

    @Test
    fun `Given assist-show JSON then parses to OpenAssistMessage with payload`() {
        val json = """{"type":"assist/show","id":7,"payload":{"pipeline_id":"abc","start_listening":false}}"""

        val assistMessage = assertInstanceOf(OpenAssistMessage::class.java, frontendExternalBusJson.decodeFromString<IncomingExternalBusMessage>(json))
        assertEquals(7, assistMessage.id)
        assertEquals("abc", assistMessage.payload.pipelineId)
        assertFalse(assistMessage.payload.startListening)
    }

    @Test
    fun `Given assist-show JSON without payload then parses to OpenAssistMessage with defaults`() {
        val json = """{"type":"assist/show","id":8}"""

        val assistMessage = assertInstanceOf(OpenAssistMessage::class.java, frontendExternalBusJson.decodeFromString<IncomingExternalBusMessage>(json))
        assertEquals(8, assistMessage.id)
        assertNull(assistMessage.payload.pipelineId)
        assertTrue(assistMessage.payload.startListening)
    }

    @Test
    fun `Given handleBlob JSON then parses to HandleBlobMessage`() {
        val json = """{"type":"handleBlob","id":10,"data":"data:application/pdf;base64,abc","filename":"file.pdf"}"""

        val blobMessage = assertInstanceOf(HandleBlobMessage::class.java, frontendExternalBusJson.decodeFromString<IncomingExternalBusMessage>(json))
        assertEquals(10, blobMessage.id)
        assertEquals("data:application/pdf;base64,abc", blobMessage.data)
        assertEquals("file.pdf", blobMessage.filename)
    }

    @Test
    fun `Given tag-write JSON with tag then parses to TagWriteMessage with tag`() {
        val json = """{"type":"tag/write","id":11,"payload":{"tag":"abc-123"}}"""

        val tagMessage = assertInstanceOf(TagWriteMessage::class.java, frontendExternalBusJson.decodeFromString<IncomingExternalBusMessage>(json))
        assertEquals(11, tagMessage.id)
        assertEquals("abc-123", tagMessage.payload.tag)
    }

    @Test
    fun `Given tag-write JSON without payload then parses to TagWriteMessage with null tag`() {
        val json = """{"type":"tag/write","id":12}"""

        val tagMessage = assertInstanceOf(TagWriteMessage::class.java, frontendExternalBusJson.decodeFromString<IncomingExternalBusMessage>(json))
        assertEquals(12, tagMessage.id)
        assertNull(tagMessage.payload.tag)
    }

    @Test
    fun `Given unknown type JSON then parses to UnknownIncomingMessage`() {
        val json = """{"type":"future-feature","id":99,"payload":{"data":"something"}}"""

        val unknownMessage = assertInstanceOf(UnknownIncomingMessage::class.java, frontendExternalBusJson.decodeFromString<IncomingExternalBusMessage>(json))
        assertTrue(unknownMessage.content.toString().contains("future-feature"))
    }

    @Test
    fun `Given exoplayer play_hls JSON with full payload then parses to ExoPlayerPlayHlsMessage`() {
        val json =
            """{"type":"exoplayer/play_hls","id":20,"payload":{"url":"https://example.com/stream.m3u8","muted":true}}"""

        val playHls = assertInstanceOf(ExoPlayerPlayHlsMessage::class.java, frontendExternalBusJson.decodeFromString<IncomingExternalBusMessage>(json))
        assertEquals(20, playHls.id)
        assertEquals("https://example.com/stream.m3u8", playHls.payload.url)
        assertTrue(playHls.payload.muted)
    }

    @Test
    fun `Given exoplayer play_hls JSON without muted then parses with muted defaulting to false`() {
        val json =
            """{"type":"exoplayer/play_hls","id":21,"payload":{"url":"https://example.com/stream.m3u8"}}"""

        val playHls = assertInstanceOf(ExoPlayerPlayHlsMessage::class.java, frontendExternalBusJson.decodeFromString<IncomingExternalBusMessage>(json))
        assertEquals("https://example.com/stream.m3u8", playHls.payload.url)
        assertFalse(playHls.payload.muted)
    }

    @Test
    fun `Given exoplayer play_hls JSON without payload then parses with default payload`() {
        val json = """{"type":"exoplayer/play_hls","id":22}"""

        val playHls = assertInstanceOf(ExoPlayerPlayHlsMessage::class.java, frontendExternalBusJson.decodeFromString<IncomingExternalBusMessage>(json))
        assertEquals(22, playHls.id)
        assertNull(playHls.payload.url)
        assertFalse(playHls.payload.muted)
    }

    @Test
    fun `Given exoplayer stop JSON then parses to ExoPlayerStopMessage`() {
        val json = """{"type":"exoplayer/stop","id":23}"""

        val message = assertInstanceOf(ExoPlayerStopMessage::class.java, frontendExternalBusJson.decodeFromString<IncomingExternalBusMessage>(json))
        assertEquals(23, message.id)
    }

    @Test
    fun `Given exoplayer resize JSON with fractional pixels then parses payload as floats`() {
        val json = """{"type":"exoplayer/resize","id":24,""" +
            """"payload":{"left":0,"top":10.5,"right":486.25,"bottom":200.5}}"""

        val resize = assertInstanceOf(ExoPlayerResizeMessage::class.java, frontendExternalBusJson.decodeFromString<IncomingExternalBusMessage>(json))
        assertEquals(24, resize.id)
        assertEquals(0.0, resize.payload.left)
        assertEquals(10.5, resize.payload.top)
        assertEquals(486.25, resize.payload.right)
        assertEquals(200.5, resize.payload.bottom)
    }

    @Test
    fun `Given Improv scan JSON then parses to ImprovScanMessage`() {
        val json = """{"type":"improv/scan","id":50}"""

        val message = assertInstanceOf(ImprovScanMessage::class.java, frontendExternalBusJson.decodeFromString<IncomingExternalBusMessage>(json))
        assertEquals(50, message.id)
    }

    @Test
    fun `Given Improv scan JSON without id then parses to ImprovScanMessage with null id`() {
        val json = """{"type":"improv/scan"}"""

        val message = assertInstanceOf(ImprovScanMessage::class.java, frontendExternalBusJson.decodeFromString<IncomingExternalBusMessage>(json))
        assertNull(message.id)
    }

    @Test
    fun `Given Improv configure_device JSON then parses to ImprovConfigureDeviceMessage with name`() {
        val json = """{"type":"improv/configure_device","id":51,"payload":{"name":"Smart Plug"}}"""

        val configureMessage = assertInstanceOf(ImprovConfigureDeviceMessage::class.java, frontendExternalBusJson.decodeFromString<IncomingExternalBusMessage>(json))
        assertEquals(51, configureMessage.id)
        assertEquals("Smart Plug", configureMessage.payload.name)
    }

    @Test
    fun `Given exoplayer resize JSON without payload then parses with zero defaults`() {
        val json = """{"type":"exoplayer/resize","id":25}"""

        val resize = assertInstanceOf(ExoPlayerResizeMessage::class.java, frontendExternalBusJson.decodeFromString<IncomingExternalBusMessage>(json))
        assertEquals(0.0, resize.payload.left)
        assertEquals(0.0, resize.payload.top)
        assertEquals(0.0, resize.payload.right)
        assertEquals(0.0, resize.payload.bottom)
    }

    @Test
    fun `Given entity add_to get_actions JSON then parses to EntityAddToGetActionsMessage`() {
        val json = """{"type":"entity/add_to/get_actions","id":20,"payload":{"entity_id":"light.living_room"}}"""

        val addToMessage = assertInstanceOf(EntityAddToGetActionsMessage::class.java, frontendExternalBusJson.decodeFromString<IncomingExternalBusMessage>(json))
        assertEquals(20, addToMessage.id)
        assertEquals("light.living_room", addToMessage.payload.entityId)
    }

    @Test
    fun `Given entity add_to JSON then parses to EntityAddToMessage`() {
        val json = """{"type":"entity/add_to","id":21,"payload":{"entity_id":"light.living_room","app_payload":"dGVzdA=="}}"""

        val addToMessage = assertInstanceOf(EntityAddToMessage::class.java, frontendExternalBusJson.decodeFromString<IncomingExternalBusMessage>(json))
        assertEquals(21, addToMessage.id)
        assertEquals("light.living_room", addToMessage.payload.entityId)
        assertEquals("dGVzdA==", addToMessage.payload.appPayload)
    }

    @Test
    fun `Given Matter commission JSON then parses to MatterCommissionMessage`() {
        val json = """{"type":"matter/commission","id":60}"""

        val message = assertInstanceOf(MatterCommissionMessage::class.java, frontendExternalBusJson.decodeFromString<IncomingExternalBusMessage>(json))
        assertEquals(60, message.id)
    }

    @Test
    fun `Given Matter commission JSON without id then parses to MatterCommissionMessage with null id`() {
        val json = """{"type":"matter/commission"}"""

        val message = assertInstanceOf(MatterCommissionMessage::class.java, frontendExternalBusJson.decodeFromString<IncomingExternalBusMessage>(json))
        assertNull(message.id)
    }

    @Test
    fun `Given Thread import_credentials JSON then parses to ThreadImportCredentialsMessage`() {
        val json = """{"type":"thread/import_credentials","id":61}"""

        val message = assertInstanceOf(ThreadImportCredentialsMessage::class.java, frontendExternalBusJson.decodeFromString<IncomingExternalBusMessage>(json))
        assertEquals(61, message.id)
    }

    @Test
    fun `Given Thread import_credentials JSON without id then parses to ThreadImportCredentialsMessage with null id`() {
        val json = """{"type":"thread/import_credentials"}"""

        val message = assertInstanceOf(ThreadImportCredentialsMessage::class.java, frontendExternalBusJson.decodeFromString<IncomingExternalBusMessage>(json))
        assertNull(message.id)
    }

    @Test
    fun `Given bar_code scan JSON with full payload then parses to BarcodeScanMessage`() {
        val json =
            """{"type":"bar_code/scan","id":60,"payload":{"title":"Scan code","description":"Point the camera","alternative_option_label":"Enter manually"}}"""

        val scanMessage = assertInstanceOf(BarcodeScanMessage::class.java, frontendExternalBusJson.decodeFromString<IncomingExternalBusMessage>(json))
        assertEquals(60, scanMessage.id)
        assertEquals("Scan code", scanMessage.payload.title)
        assertEquals("Point the camera", scanMessage.payload.description)
        assertEquals("Enter manually", scanMessage.payload.alternativeOptionLabel)
    }

    @Test
    fun `Given bar_code scan JSON without alternative_option_label then parses with null label`() {
        val json =
            """{"type":"bar_code/scan","id":61,"payload":{"title":"Scan code","description":"Point the camera"}}"""

        val scanMessage = assertInstanceOf(BarcodeScanMessage::class.java, frontendExternalBusJson.decodeFromString<IncomingExternalBusMessage>(json))
        assertEquals(61, scanMessage.id)
        assertEquals("Scan code", scanMessage.payload.title)
        assertEquals("Point the camera", scanMessage.payload.description)
        assertNull(scanMessage.payload.alternativeOptionLabel)
    }

    @Test
    fun `Given bar_code notify JSON then parses to BarcodeNotifyMessage with message`() {
        val json = """{"type":"bar_code/notify","id":62,"payload":{"message":"Code already paired"}}"""

        val notifyMessage = assertInstanceOf(BarcodeNotifyMessage::class.java, frontendExternalBusJson.decodeFromString<IncomingExternalBusMessage>(json))
        assertEquals(62, notifyMessage.id)
        assertEquals("Code already paired", notifyMessage.payload.message)
    }

    @Test
    fun `Given bar_code close JSON with id then parses to BarcodeCloseMessage`() {
        val json = """{"type":"bar_code/close","id":63}"""

        val message = assertInstanceOf(BarcodeCloseMessage::class.java, frontendExternalBusJson.decodeFromString<IncomingExternalBusMessage>(json))
        assertEquals(63, message.id)
    }

    @Test
    fun `Given bar_code close JSON without id then parses to BarcodeCloseMessage with null id`() {
        val json = """{"type":"bar_code/close"}"""

        val message = assertInstanceOf(BarcodeCloseMessage::class.java, frontendExternalBusJson.decodeFromString<IncomingExternalBusMessage>(json))
        assertNull(message.id)
    }
}
