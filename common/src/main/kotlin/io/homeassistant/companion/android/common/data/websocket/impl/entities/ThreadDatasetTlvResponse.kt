package io.homeassistant.companion.android.common.data.websocket.impl.entities

data class ThreadDatasetTlvResponse(
    val tlv: String
) {
    val tlvAsByteArray: ByteArray
        get() = tlv.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
