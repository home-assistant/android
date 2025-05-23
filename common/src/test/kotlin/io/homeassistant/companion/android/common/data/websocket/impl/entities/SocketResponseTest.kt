package io.homeassistant.companion.android.common.data.websocket.impl.entities

import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SocketResponseTest {

    @Test
    fun `Given an unknown type when deserializing then it returns an UnknownTypeSocketResponse`() {
        val response: SocketResponse = kotlinJsonMapper.decodeFromString("""{ "type":"super_type","key1":1,"key2": null}""")
        assertTrue((response as? UnknownTypeSocketResponse)?.content is JsonObject)
        val content = (response as UnknownTypeSocketResponse).content.jsonObject
        assertEquals("super_type", (content["type"] as? JsonPrimitive)?.contentOrNull)
        assertEquals(1, (content["key1"] as? JsonPrimitive)?.intOrNull)
        assertEquals(JsonNull, content["key2"])
    }

    @Test
    fun `Given a known type when deserializing then it returns proper SocketResponse`() {
        assertTrue(
            kotlinJsonMapper.decodeFromString<SocketResponse>("""{"type":"auth_required"}""") is AuthRequiredSocketResponse,
        )
        assertEquals(
            AuthInvalidSocketResponse(),
            kotlinJsonMapper.decodeFromString<SocketResponse>("""{"type":"auth_invalid"}"""),
        )
        assertEquals(
            AuthOkSocketResponse(),
            kotlinJsonMapper.decodeFromString<SocketResponse>("""{"type":"auth_ok"}"""),
        )
        assertEquals(
            MessageSocketResponse(
                id = 1,
                success = true,
            ),
            kotlinJsonMapper.decodeFromString<SocketResponse>("""{"type": "result","id":1,"success":true}"""),
        )
        assertEquals(
            PongSocketResponse(
                id = 1,
                success = true,
            ),
            kotlinJsonMapper.decodeFromString<SocketResponse>("""{"type": "pong","id":1,"success":true}"""),
        )
        assertEquals(
            EventSocketResponse(
                id = 1,
                event = kotlinJsonMapper.parseToJsonElement("""{"data":"test"}"""),
            ),
            kotlinJsonMapper.decodeFromString<SocketResponse>("""{"type": "event","id":1,"event":{"data":"test"}}"""),
        )
    }
}
