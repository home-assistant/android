package io.homeassistant.companion.android.matter

import io.homeassistant.companion.android.common.util.getIntOrNull
import io.homeassistant.companion.android.common.util.getStringOrNull
import kotlinx.serialization.json.JsonObject

/** The window from `matter/share_device`. [discriminator] is the long, 12-bit form. */
data class MatterShareRequest(
    val passcode: Long,
    val discriminator: Int,
    val remainingSeconds: Long,
    val vendorId: Int? = null,
    val productId: Int? = null,
    val deviceName: String? = null,
) {
    companion object {
        /** The server's values are trusted; null only without a readable passcode, discriminator or remaining time. */
        fun fromPayload(payload: JsonObject): MatterShareRequest? {
            val passcode = payload.getIntOrNull("setup_pin_code")
            val discriminator = payload.getIntOrNull("discriminator")
            val remainingSeconds = payload.getIntOrNull("remaining_seconds")
            if (passcode == null || discriminator == null || remainingSeconds == null) return null
            return MatterShareRequest(
                passcode = passcode.toLong(),
                discriminator = discriminator,
                remainingSeconds = remainingSeconds.toLong(),
                vendorId = payload.getIntOrNull("vendor_id"),
                productId = payload.getIntOrNull("product_id"),
                deviceName = payload.getStringOrNull("device_name"),
            )
        }
    }
}
