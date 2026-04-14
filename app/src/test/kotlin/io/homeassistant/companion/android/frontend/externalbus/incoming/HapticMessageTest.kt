package io.homeassistant.companion.android.frontend.externalbus.incoming

import io.homeassistant.companion.android.frontend.externalbus.frontendExternalBusJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class HapticMessageTest {

    @Test
    fun `Given haptic JSON with success type then parses to HapticMessage with Success`() {
        val json = """{"type":"haptic","payload":{"hapticType":"success"}}"""

        val message = frontendExternalBusJson.decodeFromString<IncomingExternalBusMessage>(json)

        assertInstanceOf(HapticMessage::class.java, message)
        val hapticMessage = message as HapticMessage
        assertEquals(HapticType.Success, hapticMessage.payload)
        assertNull(hapticMessage.id)
    }

    @Test
    fun `Given haptic JSON with id then parses id correctly`() {
        val json = """{"type":"haptic","id":5,"payload":{"hapticType":"light"}}"""

        val message = frontendExternalBusJson.decodeFromString<IncomingExternalBusMessage>(json)

        assertInstanceOf(HapticMessage::class.java, message)
        val hapticMessage = message as HapticMessage
        assertEquals(5, hapticMessage.id)
        assertEquals(HapticType.Light, hapticMessage.payload)
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
            val message = frontendExternalBusJson.decodeFromString<IncomingExternalBusMessage>(json)

            assertInstanceOf(HapticMessage::class.java, message, "Failed for type: $typeString")
            assertEquals(expectedType, (message as HapticMessage).payload)
        }
    }

    @Test
    fun `Given haptic JSON with unknown type then parses to Unknown`() {
        val json = """{"type":"haptic","payload":{"hapticType":"future_type"}}"""

        val message = frontendExternalBusJson.decodeFromString<IncomingExternalBusMessage>(json)

        assertInstanceOf(HapticMessage::class.java, message)
        assertEquals(HapticType.Unknown, (message as HapticMessage).payload)
    }
}
