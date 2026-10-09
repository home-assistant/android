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

    private fun window(vararg extra: Pair<String, Any>) = payload(
        "setup_pin_code" to 20202021,
        "discriminator" to 3840,
        "remaining_seconds" to 250,
        *extra,
    )

    @Test
    fun `Given a complete payload when fromPayload then reads every field`() {
        assertEquals(
            MatterShareRequest(
                passcode = 20202021,
                discriminator = 3840,
                remainingSeconds = 250,
                vendorId = 0xFFF1,
                productId = 0x8000,
                deviceName = "Kitchen light",
            ),
            MatterShareRequest.fromPayload(
                window(
                    "setup_qr_code" to "MT:-24J0AFN00KA0648G00",
                    "vendor_id" to 0xFFF1,
                    "product_id" to 0x8000,
                    "device_name" to "Kitchen light",
                ),
            ),
        )
    }

    @Test
    fun `Given only the required fields when fromPayload then optional fields are null`() {
        assertEquals(
            MatterShareRequest(passcode = 20202021, discriminator = 0, remainingSeconds = 250),
            MatterShareRequest.fromPayload(
                payload("setup_pin_code" to 20202021, "discriminator" to 0, "remaining_seconds" to 250),
            ),
        )
    }

    /** The server is trusted: out-of-spec values are forwarded for Play Services to judge. */
    @Test
    fun `Given values the Matter spec forbids when fromPayload then they are passed through`() {
        assertEquals(
            MatterShareRequest(
                passcode = 12345678,
                discriminator = 4096,
                remainingSeconds = 2000,
                vendorId = 0x10000,
            ),
            MatterShareRequest.fromPayload(
                payload(
                    "setup_pin_code" to 12345678,
                    "discriminator" to 4096,
                    "remaining_seconds" to 2000,
                    "vendor_id" to 0x10000,
                ),
            ),
        )
    }

    @Test
    fun `Given a missing or unparseable required field when fromPayload then returns null`() {
        assertNull(MatterShareRequest.fromPayload(JsonObject(emptyMap())))
        assertNull(MatterShareRequest.fromPayload(payload("setup_pin_code" to 20202021, "remaining_seconds" to 250)))
        assertNull(MatterShareRequest.fromPayload(payload("discriminator" to 3840, "remaining_seconds" to 250)))
        assertNull(MatterShareRequest.fromPayload(payload("setup_pin_code" to 20202021, "discriminator" to 3840)))
        assertNull(MatterShareRequest.fromPayload(window("setup_pin_code" to "20202021x")))
        // Does not fit the field it is sent in.
        assertNull(MatterShareRequest.fromPayload(window("discriminator" to 5_000_000_000)))
    }

    @Test
    fun `Given an unknown field when fromPayload then it is ignored`() {
        assertEquals(
            MatterShareRequest(passcode = 20202021, discriminator = 3840, remainingSeconds = 250),
            MatterShareRequest.fromPayload(window("future_field" to 1)),
        )
    }

    @Test
    fun `Given a mistyped optional field when fromPayload then only that field is lost`() {
        assertEquals(
            MatterShareRequest(
                passcode = 20202021,
                discriminator = 3840,
                remainingSeconds = 250,
                deviceName = "Kitchen light",
            ),
            MatterShareRequest.fromPayload(
                window("vendor_id" to "0x1234", "device_name" to "Kitchen light"),
            ),
        )
    }
}
