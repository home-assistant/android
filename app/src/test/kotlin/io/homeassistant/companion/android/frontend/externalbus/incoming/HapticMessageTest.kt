package io.homeassistant.companion.android.frontend.externalbus.incoming

import io.homeassistant.companion.android.frontend.externalbus.frontendExternalBusJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull

class HapticMessageTest {

    @Test
    fun `Given haptic JSON with success type then parses to HapticMessage with Success`() {
        val json = """{"type":"haptic","payload":{"hapticType":"success"}}"""

        val message = assertInstanceOf(
            HapticMessage::class.java,
            frontendExternalBusJson.decodeFromString<IncomingExternalBusMessage>(json),
        )
        assertEquals(HapticType.Success, message.payload)
        assertNull(message.id)
    }

    @Test
    fun `Given haptic JSON with id then parses id correctly`() {
        val json = """{"type":"haptic","id":5,"payload":{"hapticType":"light"}}"""

        val message = assertInstanceOf(
            HapticMessage::class.java,
            frontendExternalBusJson.decodeFromString<IncomingExternalBusMessage>(json),
        )
        assertEquals(5, message.id)
        assertEquals(HapticType.Light, message.payload)
    }

    @Test
    fun `Given haptic JSON with all known types then parses to correct HapticType`() {
        val expectedMapping = mapOf(
            "success" to HapticType.Success,
            "warning" to HapticType.Warning,
            "failure" to HapticType.Failure,
            "light" to HapticType.Light,
            "medium" to HapticType.Medium,
            "heavy" to HapticType.Heavy,
            "selection" to HapticType.Selection,
        )

        expectedMapping.forEach { (typeString, expectedType) ->
            val json = """{"type":"haptic","payload":{"hapticType":"$typeString"}}"""
            val message = assertInstanceOf(
                HapticMessage::class.java,
                frontendExternalBusJson.decodeFromString<IncomingExternalBusMessage>(json),
                "Failed for type: $typeString",
            )
            assertEquals(expectedType, message.payload)
        }
    }

    @Test
    fun `Given haptic JSON with unknown type then parses to Unknown`() {
        val json = """{"type":"haptic","payload":{"hapticType":"future_type"}}"""

        val message = assertInstanceOf(
            HapticMessage::class.java,
            frontendExternalBusJson.decodeFromString<IncomingExternalBusMessage>(json),
        )
        assertEquals(HapticType.Unknown, message.payload)
    }
}
