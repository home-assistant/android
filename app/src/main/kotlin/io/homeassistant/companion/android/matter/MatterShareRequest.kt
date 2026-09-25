package io.homeassistant.companion.android.matter

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/**
 * A commissioning window Home Assistant opened for one of its Matter devices, as the frontend sends
 * it with `matter/share_device`.
 *
 * @property passcode Setup passcode of the open window
 * @property discriminator Long (12-bit) discriminator the device advertises while the window is open
 * @property vendorId Vendor ID of the device, if known
 * @property productId Product ID of the device, if known
 * @property deviceName Home Assistant's name for the device, suggested to the receiving app
 * @property remainingSeconds Seconds until the window closes, if known
 */
data class MatterShareRequest(
    val passcode: Long,
    val discriminator: Int,
    val vendorId: Int? = null,
    val productId: Int? = null,
    val deviceName: String? = null,
    val remainingSeconds: Long? = null,
) {
    // The passcode lets anyone on the network add the device while the window is open, keep it out of logs.
    override fun toString(): String = "MatterShareRequest(discriminator=$discriminator, vendorId=$vendorId, " +
        "productId=$productId, deviceName=$deviceName, remainingSeconds=$remainingSeconds)"

    companion object {
        /**
         * Reads a `matter/share_device` payload, returning `null` when the passcode or discriminator
         * is missing or invalid. Invalid optional fields are dropped.
         */
        fun fromPayload(payload: JsonObject): MatterShareRequest? {
            val passcode = payload.long("setup_pin_code")
                ?.takeIf { it in PASSCODE_RANGE && it !in INVALID_PASSCODES }
            val discriminator = payload.long("discriminator")?.takeIf { it in DISCRIMINATOR_RANGE }
            if (passcode == null || discriminator == null) return null
            return MatterShareRequest(
                passcode = passcode,
                discriminator = discriminator.toInt(),
                vendorId = payload.id("vendor_id"),
                productId = payload.id("product_id"),
                deviceName = (payload["device_name"] as? JsonPrimitive)
                    ?.takeIf { it.isString }?.contentOrNull?.takeIf { it.isNotBlank() },
                remainingSeconds = payload.long("remaining_seconds")?.takeIf { it > 0 },
            )
        }
    }
}

private val PASSCODE_RANGE = 1L..99_999_998L

// Matter Core spec 5.1.7.1 forbids these besides the out-of-range values.
private val INVALID_PASSCODES = setOf(
    11_111_111L, 22_222_222L, 33_333_333L, 44_444_444L, 55_555_555L,
    66_666_666L, 77_777_777L, 88_888_888L, 12_345_678L, 87_654_321L,
)
private val DISCRIMINATOR_RANGE = 0L..0xFFFL
private val ID_RANGE = 0L..0xFFFFL

private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull

private fun JsonObject.id(key: String): Int? = long(key)?.takeIf { it in ID_RANGE }?.toInt()
