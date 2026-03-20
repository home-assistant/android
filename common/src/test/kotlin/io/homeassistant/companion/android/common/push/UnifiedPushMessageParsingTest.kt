package io.homeassistant.companion.android.common.push

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the JSON message parsing used by the UnifiedPush notification receiver.
 * UnifiedPush messages arrive as JSON payloads that must be deserialized into
 * Map<String, Any> before being passed to MessagingManager.handleMessage().
 *
 * These tests verify the parsing logic independent of Android framework dependencies.
 */
class UnifiedPushMessageParsingTest {

    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `parse simple notification message`() {
        val json = """
            {
                "message": "Test notification",
                "title": "Test Title"
            }
        """.trimIndent()

        val data: Map<String, Any> = objectMapper.readValue(json)
        assertEquals("Test notification", data["message"])
        assertEquals("Test Title", data["title"])
    }

    @Test
    fun `parse notification with nested data`() {
        val json = """
            {
                "message": "Hello",
                "title": "Greetings",
                "data": {
                    "channel": "alerts",
                    "importance": "high",
                    "ttl": 0
                }
            }
        """.trimIndent()

        val data: Map<String, Any> = objectMapper.readValue(json)
        assertEquals("Hello", data["message"])

        @Suppress("UNCHECKED_CAST")
        val nestedData = data["data"] as Map<String, Any>
        assertEquals("alerts", nestedData["channel"])
        assertEquals("high", nestedData["importance"])
    }

    @Test
    fun `parse notification with actions`() {
        val json = """
            {
                "message": "Motion detected",
                "title": "Security",
                "data": {
                    "actions": [
                        {
                            "action": "OPEN",
                            "title": "Open Camera"
                        },
                        {
                            "action": "DISMISS",
                            "title": "Dismiss"
                        }
                    ]
                }
            }
        """.trimIndent()

        val data: Map<String, Any> = objectMapper.readValue(json)

        @Suppress("UNCHECKED_CAST")
        val nestedData = data["data"] as Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val actions = nestedData["actions"] as List<Map<String, Any>>
        assertEquals(2, actions.size)
        assertEquals("OPEN", actions[0]["action"])
        assertEquals("Open Camera", actions[0]["title"])
        assertEquals("DISMISS", actions[1]["action"])
    }

    @Test
    fun `parse notification with registration_info`() {
        val json = """
            {
                "message": "Test",
                "registration_info": {
                    "webhook_id": "abc123def456"
                }
            }
        """.trimIndent()

        val data: Map<String, Any> = objectMapper.readValue(json)

        @Suppress("UNCHECKED_CAST")
        val regInfo = data["registration_info"] as Map<String, Any>
        assertEquals("abc123def456", regInfo["webhook_id"])
    }

    @Test
    fun `parse notification with image and url`() {
        val json = """
            {
                "message": "Doorbell pressed",
                "title": "Front Door",
                "data": {
                    "image": "https://example.com/camera/snapshot.jpg",
                    "clickAction": "https://example.com/dashboard"
                }
            }
        """.trimIndent()

        val data: Map<String, Any> = objectMapper.readValue(json)
        assertEquals("Doorbell pressed", data["message"])

        @Suppress("UNCHECKED_CAST")
        val nestedData = data["data"] as Map<String, Any>
        assertEquals("https://example.com/camera/snapshot.jpg", nestedData["image"])
    }

    @Test
    fun `parse empty data map`() {
        val json = """{}"""
        val data: Map<String, Any> = objectMapper.readValue(json)
        assertTrue(data.isEmpty())
    }

    @Test
    fun `parse notification with numeric values`() {
        val json = """
            {
                "message": "Temperature alert",
                "data": {
                    "tag": "temp_alert",
                    "timeout": 300,
                    "vibrationPattern": "100, 200, 100"
                }
            }
        """.trimIndent()

        val data: Map<String, Any> = objectMapper.readValue(json)
        assertNotNull(data["data"])
    }

    @Test
    fun `parse notification with confirm id for websocket ack`() {
        val json = """
            {
                "message": "Test",
                "hass_confirm_id": "confirm_12345"
            }
        """.trimIndent()

        val data: Map<String, Any> = objectMapper.readValue(json)
        assertEquals("confirm_12345", data["hass_confirm_id"])
    }

    @Test
    fun `parse notification preserves unknown fields`() {
        val json = """
            {
                "message": "Test",
                "custom_field": "custom_value",
                "another_field": true
            }
        """.trimIndent()

        val data: Map<String, Any> = objectMapper.readValue(json)
        assertEquals(3, data.size)
        assertEquals("custom_value", data["custom_field"])
        assertEquals(true, data["another_field"])
    }
}
