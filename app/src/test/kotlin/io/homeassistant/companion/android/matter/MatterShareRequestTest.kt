package io.homeassistant.companion.android.matter

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MatterShareRequestTest {

    private fun payload(vararg entries: Pair<String, Any>) = JsonObject(
        entries.associate { (key, value) ->
            key to when (value) {
                is Number -> JsonPrimitive(value)
                else -> JsonPrimitive(value.toString())
            }
        },
    )

    @Test
    fun `Given a complete payload when fromPayload then reads every field`() {
        assertEquals(
            MatterShareRequest(
                passcode = 20202021,
                discriminator = 3840,
                vendorId = 0xFFF1,
                productId = 0x8000,
                deviceName = "Kitchen light",
                remainingSeconds = 250,
            ),
            MatterShareRequest.fromPayload(
                payload(
                    "setup_pin_code" to 20202021,
                    "discriminator" to 3840,
                    "vendor_id" to 0xFFF1,
                    "product_id" to 0x8000,
                    "device_name" to "Kitchen light",
                    "remaining_seconds" to 250,
                ),
            ),
        )
    }

    @Test
    fun `Given only the required fields when fromPayload then optional fields are null`() {
        assertEquals(
            MatterShareRequest(passcode = 20202021, discriminator = 0),
            MatterShareRequest.fromPayload(payload("setup_pin_code" to 20202021, "discriminator" to 0)),
        )
    }

    @Test
    fun `Given invalid optional fields when fromPayload then they are dropped`() {
        val request = MatterShareRequest.fromPayload(
            payload(
                "setup_pin_code" to 20202021,
                "discriminator" to 3840,
                "vendor_id" to 0x10000,
                "device_name" to "",
                "remaining_seconds" to 0,
            ),
        )

        assertEquals(MatterShareRequest(passcode = 20202021, discriminator = 3840), request)
    }

    @Test
    fun `Given invalid required fields when fromPayload then returns null`() {
        assertNull(MatterShareRequest.fromPayload(JsonObject(emptyMap())))
        assertNull(MatterShareRequest.fromPayload(payload("setup_pin_code" to 20202021)))
        assertNull(MatterShareRequest.fromPayload(payload("discriminator" to 3840)))
        assertNull(MatterShareRequest.fromPayload(payload("setup_pin_code" to "20202021x", "discriminator" to 3840)))
        assertNull(MatterShareRequest.fromPayload(payload("setup_pin_code" to 20202021, "discriminator" to 4096)))
        assertNull(MatterShareRequest.fromPayload(payload("setup_pin_code" to 0, "discriminator" to 3840)))
        assertNull(MatterShareRequest.fromPayload(payload("setup_pin_code" to 99999999, "discriminator" to 3840)))
        assertNull(MatterShareRequest.fromPayload(payload("setup_pin_code" to 12345678, "discriminator" to 3840)))
    }
}
