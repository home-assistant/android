package io.homeassistant.companion.android.frontend.externalbus.outgoing

import io.homeassistant.companion.android.frontend.externalbus.frontendExternalBusJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CommandMessageTest {

    @Test
    fun `Given NavigateToMessage with replace when serializing then produces correct JSON`() {
        val message = NavigateToMessage(path = "/", replace = true)

        val json = frontendExternalBusJson.encodeToString<OutgoingExternalBusMessage>(message)

        assertEquals(
            """{"type":"command","id":null,"command":"navigate","payload":{"path":"/","options":{"replace":true}}}""",
            json,
        )
    }

    @Test
    fun `Given NavigateToMessage without replace when serializing then defaults replace to false`() {
        val message = NavigateToMessage(path = "/lovelace/dashboard")

        val json = frontendExternalBusJson.encodeToString<OutgoingExternalBusMessage>(message)

        assertEquals(
            """{"type":"command","id":null,"command":"navigate","payload":{"path":"/lovelace/dashboard","options":{"replace":false}}}""",
            json,
        )
    }

    @Test
    fun `Given ShowSidebarMessage when serializing then produces correct JSON without payload`() {
        val json = frontendExternalBusJson.encodeToString<OutgoingExternalBusMessage>(ShowSidebarMessage)

        assertEquals(
            """{"type":"command","id":null,"command":"sidebar/show","payload":null}""",
            json,
        )
    }

    @Test
    fun `Given ImprovDiscoveredDeviceMessage when serializing then produces command with name payload`() {
        val message = ImprovDiscoveredDeviceMessage(name = "Smart Plug")

        val json = frontendExternalBusJson.encodeToString<OutgoingExternalBusMessage>(message)

        assertEquals(
            """{"type":"command","id":null,"command":"improv/discovered_device","payload":{"name":"Smart Plug"}}""",
            json,
        )
    }

    @Test
    fun `Given ImprovDeviceSetupDoneMessage when serializing then produces command without payload`() {
        val json = frontendExternalBusJson.encodeToString<OutgoingExternalBusMessage>(ImprovDeviceSetupDoneMessage)

        assertEquals(
            """{"type":"command","id":null,"command":"improv/device_setup_done","payload":null}""",
            json,
        )
    }

    @Test
    fun `Given BarcodeScanResultMessage when serializing then produces bar_code scan_result command`() {
        val message = BarcodeScanResultMessage(id = 7, rawValue = "HA-12345", format = "qr_code")

        val json = frontendExternalBusJson.encodeToString<OutgoingExternalBusMessage>(message)

        assertEquals(
            """{"type":"command","id":7,"command":"bar_code/scan_result","payload":{"rawValue":"HA-12345","format":"qr_code"}}""",
            json,
        )
    }

    @Test
    fun `Given BarcodeScanAbortedMessage forAction true when serializing then reason is alternative_options`() {
        val message = BarcodeScanAbortedMessage(id = 7, forAction = true)

        val json = frontendExternalBusJson.encodeToString<OutgoingExternalBusMessage>(message)

        assertEquals(
            """{"type":"command","id":7,"command":"bar_code/aborted","payload":{"reason":"alternative_options"}}""",
            json,
        )
    }

    @Test
    fun `Given BarcodeScanAbortedMessage forAction false when serializing then reason is canceled`() {
        val message = BarcodeScanAbortedMessage(id = 7, forAction = false)

        val json = frontendExternalBusJson.encodeToString<OutgoingExternalBusMessage>(message)

        assertEquals(
            """{"type":"command","id":7,"command":"bar_code/aborted","payload":{"reason":"canceled"}}""",
            json,
        )
    }
}
