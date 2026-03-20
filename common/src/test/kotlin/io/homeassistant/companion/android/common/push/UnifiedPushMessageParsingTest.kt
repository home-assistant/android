package io.homeassistant.companion.android.common.push

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

    private fun parseJson(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject

    @Test
    fun `parse simple notification message`() {
        val data = parseJson(
            """
            {
                "message": "Test notification",
                "title": "Test Title"
            }
            """.trimIndent()
        )
        assertEquals("Test notification", data["message"]?.jsonPrimitive?.contentOrNull)
        assertEquals("Test Title", data["title"]?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun `parse notification with nested data`() {
        val data = parseJson(
            """
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
        )
        assertEquals("Hello", data["message"]?.jsonPrimitive?.contentOrNull)

        val nestedData = data["data"]?.jsonObject
        assertNotNull(nestedData)
        assertEquals("alerts", nestedData!!["channel"]?.jsonPrimitive?.contentOrNull)
        assertEquals("high", nestedData["importance"]?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun `parse notification with actions`() {
        val data = parseJson(
            """
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
        )

        val nestedData = data["data"]?.jsonObject
        assertNotNull(nestedData)
        val actions = nestedData!!["actions"]?.jsonArray
        assertNotNull(actions)
        assertEquals(2, actions!!.size)
        assertEquals("OPEN", actions[0].jsonObject["action"]?.jsonPrimitive?.contentOrNull)
        assertEquals("Open Camera", actions[0].jsonObject["title"]?.jsonPrimitive?.contentOrNull)
        assertEquals("DISMISS", actions[1].jsonObject["action"]?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun `parse notification with registration_info`() {
        val data = parseJson(
            """
            {
                "message": "Test",
                "registration_info": {
                    "webhook_id": "abc123def456"
                }
            }
            """.trimIndent()
        )

        val regInfo = data["registration_info"]?.jsonObject
        assertNotNull(regInfo)
        assertEquals("abc123def456", regInfo!!["webhook_id"]?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun `parse notification with image and url`() {
        val data = parseJson(
            """
            {
                "message": "Doorbell pressed",
                "title": "Front Door",
                "data": {
                    "image": "https://example.com/camera/snapshot.jpg",
                    "clickAction": "https://example.com/dashboard"
                }
            }
            """.trimIndent()
        )
        assertEquals("Doorbell pressed", data["message"]?.jsonPrimitive?.contentOrNull)

        val nestedData = data["data"]?.jsonObject
        assertNotNull(nestedData)
        assertEquals("https://example.com/camera/snapshot.jpg", nestedData!!["image"]?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun `parse empty data map`() {
        val data = parseJson("""{}""")
        assertTrue(data.isEmpty())
    }

    @Test
    fun `parse notification with numeric values`() {
        val data = parseJson(
            """
            {
                "message": "Temperature alert",
                "data": {
                    "tag": "temp_alert",
                    "timeout": 300,
                    "vibrationPattern": "100, 200, 100"
                }
            }
            """.trimIndent()
        )
        assertNotNull(data["data"])
    }

    @Test
    fun `parse notification with confirm id for websocket ack`() {
        val data = parseJson(
            """
            {
                "message": "Test",
                "hass_confirm_id": "confirm_12345"
            }
            """.trimIndent()
        )
        assertEquals("confirm_12345", data["hass_confirm_id"]?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun `parse notification preserves unknown fields`() {
        val data = parseJson(
            """
            {
                "message": "Test",
                "custom_field": "custom_value",
                "another_field": true
            }
            """.trimIndent()
        )
        assertEquals(3, data.size)
        assertEquals("custom_value", data["custom_field"]?.jsonPrimitive?.contentOrNull)
        assertEquals(true, data["another_field"]?.jsonPrimitive?.boolean)
    }
}
