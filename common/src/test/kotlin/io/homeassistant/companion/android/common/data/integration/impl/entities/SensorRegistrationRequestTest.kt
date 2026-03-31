package io.homeassistant.companion.android.common.data.integration.impl.entities

import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SensorRegistrationRequestTest {
    @Test
    fun `Given a valid SensorRegistrationRequest when serializing it then it creates a valid JSON`() {
        assertEquals(
            """{"unique_id":"1","state":"2","type":"3","icon":"4","attributes":{"1":"2","2":1},"name":"5","device_class":"6","unit_of_measurement":"7","state_class":"8","entity_category":"9","disabled":true}""",
            kotlinJsonMapper.encodeToString(
                SensorRegistrationRequest(
                    uniqueId = "1",
                    state = "2",
                    type = "3",
                    icon = "4",
                    attributes = mapOf("1" to "2", "2" to 1),
                    name = "5",
                    deviceClass = "6",
                    unitOfMeasurement = "7",
                    stateClass = "8",
                    entityCategory = "9",
                    disabled = true,
                ),
            ),
        )
        assertEquals(
            """{"unique_id":"1","state":"2","type":"3","icon":"4","attributes":{"1":"2","2":1},"device_class":null,"entity_category":null}""",
            kotlinJsonMapper.encodeToString(
                SensorRegistrationRequest(
                    uniqueId = "1",
                    state = "2",
                    type = "3",
                    icon = "4",
                    attributes = mapOf("1" to "2", "2" to 1),
                    canRegisterNullProperties = true,
                ),
            ),
        )
        assertEquals(
            """{"unique_id":"1","state":"2","type":"3","icon":"4","attributes":{"1":"2","2":1}}""",
            kotlinJsonMapper.encodeToString(
                SensorRegistrationRequest(
                    uniqueId = "1",
                    state = "2",
                    type = "3",
                    icon = "4",
                    attributes = mapOf("1" to "2", "2" to 1),
                    canRegisterNullProperties = false,
                ),
            ),
        )
    }

    @Test
    fun `Given a valid JSON when deserializing it then it creates a valid SensorRegistrationRequest`() {
        assertEquals(
            SensorRegistrationRequest(
                uniqueId = "1",
                state = "2",
                type = "3",
                icon = "4",
                attributes = mapOf("1" to "2", "2" to 1),
                name = "5",
                deviceClass = "6",
                unitOfMeasurement = "7",
                stateClass = "8",
                entityCategory = "9",
                disabled = true,
            ),
            kotlinJsonMapper.decodeFromString<SensorRegistrationRequest>("""{"unique_id":"1","state":"2","type":"3","icon":"4","attributes":{"1":"2","2":1},"name":"5","device_class":"6","unit_of_measurement":"7","state_class":"8","entity_category":"9","disabled":true}"""),
        )
        val withoutNullableElements = SensorRegistrationRequest(
            uniqueId = "1",
            state = "2",
            type = "3",
            icon = "4",
            attributes = mapOf("1" to "2", "2" to 1),
        )
        assertEquals(
            withoutNullableElements,
            kotlinJsonMapper.decodeFromString<SensorRegistrationRequest>("""{"unique_id":"1","state":"2","type":"3","icon":"4","attributes":{"1":"2","2":1},"device_class":null,"entity_category":null}"""),
        )
        assertEquals(
            withoutNullableElements,
            kotlinJsonMapper.decodeFromString<SensorRegistrationRequest>("""{"unique_id":"1","state":"2","type":"3","icon":"4","attributes":{"1":"2","2":1}}"""),
        )
    }
}
