package io.homeassistant.companion.android.common.data.websocket.impl.entities

import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import java.util.stream.Stream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class NextNamePartTest {

    companion object {
        @JvmStatic
        fun nextNameParts(): Stream<Arguments> = Stream.of(
            Arguments.of("area", NextNamePart.Area),
            Arguments.of("device", NextNamePart.Device),
            Arguments.of("parent_device", NextNamePart.ParentDevice),
            Arguments.of("floor", NextNamePart.Unknown("floor")),
        )
    }

    @ParameterizedTest
    @MethodSource("nextNameParts")
    fun `Given a next name part when decoding and encoding then it maps to its type and back`(
        value: String,
        expected: NextNamePart,
    ) {
        val json = "\"$value\""

        assertEquals(expected, kotlinJsonMapper.decodeFromString<NextNamePart>(json))
        assertEquals(json, kotlinJsonMapper.encodeToString(expected))
    }

    @Test
    fun `Given registry payloads with next name parts when decoding then they are mapped`() {
        val displayEntry = kotlinJsonMapper.decodeFromString<EntityRegistryDisplayEntry>(
            """{"ei": "sensor.power", "np": "device"}""",
        )
        val device = kotlinJsonMapper.decodeFromString<DeviceRegistryResponse>(
            """{"id": "outlet", "parent_device_id": "strip", "next_name_part": "parent_device"}""",
        )
        val classicEntry = kotlinJsonMapper.decodeFromString<EntityRegistryResponse>(
            """{"entity_id": "sensor.power", "next_name_part": null}""",
        )

        assertEquals(NextNamePart.Device, displayEntry.nextNamePart)
        assertEquals(NextNamePart.ParentDevice, device.nextNamePart)
        assertEquals("strip", device.parentDeviceId)
        assertEquals(null, classicEntry.nextNamePart)
    }
}
