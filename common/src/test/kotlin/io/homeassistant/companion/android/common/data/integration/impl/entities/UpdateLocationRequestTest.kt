package io.homeassistant.companion.android.common.data.integration.impl.entities

import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UpdateLocationRequestTest {
    @Test
    fun `Given a valid UpdateLocationRequest when serializing it then it creates a valid JSON`() {
        assertEquals(
            """{"gps":[1.0,2.0],"gps_accuracy":0,"location_name":"Test Location","speed":1,"altitude":2,"course":3,"vertical_accuracy":4}""",
            kotlinJsonMapper.encodeToString(
                UpdateLocationRequest(
                    gps = listOf(1.0, 2.0),
                    gpsAccuracy = 0,
                    locationName = "Test Location",
                    speed = 1,
                    altitude = 2,
                    course = 3,
                    verticalAccuracy = 4,
                ),
            ),
        )
        assertEquals(
            """{}""",
            kotlinJsonMapper.encodeToString(
                UpdateLocationRequest(),
            ),
        )
    }

    @Test
    fun `Given a valid JSON when deserializing it then it creates a valid UpdateLocationRequest`() {
        assertEquals(
            UpdateLocationRequest(
                gps = listOf(1.0, 2.0),
                gpsAccuracy = 0,
                locationName = "Test Location",
                speed = 1,
                altitude = 2,
                course = 3,
                verticalAccuracy = 4,
            ),
            kotlinJsonMapper.decodeFromString<UpdateLocationRequest>("""{"gps":[1.0,2.0],"gps_accuracy":0,"location_name":"Test Location","speed":1,"altitude":2,"course":3,"vertical_accuracy":4}"""),
        )
        assertEquals(
            UpdateLocationRequest(),
            kotlinJsonMapper.decodeFromString<UpdateLocationRequest>("""{}"""),
        )
    }
}
