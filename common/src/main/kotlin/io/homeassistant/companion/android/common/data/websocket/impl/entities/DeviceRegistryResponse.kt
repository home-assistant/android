package io.homeassistant.companion.android.common.data.websocket.impl.entities

import kotlinx.serialization.Serializable

/**
 * A device of the `config/device_registry/list` websocket command, either a regular device or a
 * child device (a logical part of its parent device, like an outlet of a power strip). A child
 * device only carries its own fields, the others come from its parent.
 */
@Serializable
data class DeviceRegistryResponse(
    val areaId: String? = null,
    val id: String,
    val name: String? = null,
    val nameByUser: String? = null,
    /** Id of the parent device, only set on child devices. Available since Home Assistant 2026.9. */
    val parentDeviceId: String? = null,
    /** Next owner of the device naming context, null when none. */
    val nextNamePart: NextNamePart? = null,
)
